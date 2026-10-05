/* JNI bridge: runs the vendored spd_dump in a forked child that inherits the
 * USB file descriptor handed out by Android's UsbManager, with stdout+stderr
 * piped back to the app.
 *
 * A control socketpair links the app (parent) and spd_dump (child). When the
 * phone re-enumerates on USB (typically right after FDL1 starts) the old
 * descriptor is dead; the child writes 'R' on the socket, the app opens the
 * returning device through UsbManager and sends the new descriptor back with
 * SCM_RIGHTS ('F' + fd), or 'N' if it could not get one. */
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

/* defined in spd_dump.c via -Dmain=spd_dump_main */
extern int spd_dump_main(int argc, char **argv);

#define TAG "spdbridge"

/* ---- exit()/atexit() replacements used by the child (see spd_compat.h) ---- */
#define MAX_ATEXIT 8
static void (*g_atexit[MAX_ATEXIT])(void);
static int g_atexit_n;

int spd_child_atexit(void (*fn)(void)) {
	if (g_atexit_n >= MAX_ATEXIT) return -1;
	g_atexit[g_atexit_n++] = fn;
	return 0;
}

void spd_child_exit(int code) {
	while (g_atexit_n > 0) {
		void (*fn)(void) = g_atexit[--g_atexit_n];
		if (fn) fn();
	}
	fflush(NULL);
	_exit(code);
}

/* ---- child side of the control channel ---- */
static int g_ctl_fd = -1;

int spd_android_reacquire_fd(int timeout_ms) {
	char req = 'R', tag = 0;
	char cbuf[CMSG_SPACE(sizeof(int))];
	struct iovec iov;
	struct msghdr msg;
	struct cmsghdr *cm;
	struct pollfd pfd;
	ssize_t n;
	int pr, fd = -1;

	if (g_ctl_fd < 0) return -1;
	do { n = write(g_ctl_fd, &req, 1); } while (n < 0 && errno == EINTR);
	if (n != 1) return -1;

	pfd.fd = g_ctl_fd; pfd.events = POLLIN; pfd.revents = 0;
	do { pr = poll(&pfd, 1, timeout_ms); } while (pr < 0 && errno == EINTR);
	if (pr <= 0) return -1;

	memset(&msg, 0, sizeof(msg));
	memset(cbuf, 0, sizeof(cbuf));
	iov.iov_base = &tag; iov.iov_len = 1;
	msg.msg_iov = &iov; msg.msg_iovlen = 1;
	msg.msg_control = cbuf; msg.msg_controllen = sizeof(cbuf);
	do { n = recvmsg(g_ctl_fd, &msg, 0); } while (n < 0 && errno == EINTR);
	if (n <= 0 || tag != 'F') return -1;
	for (cm = CMSG_FIRSTHDR(&msg); cm; cm = CMSG_NXTHDR(&msg, cm)) {
		if (cm->cmsg_level == SOL_SOCKET && cm->cmsg_type == SCM_RIGHTS) {
			memcpy(&fd, CMSG_DATA(cm), sizeof(int));
			break;
		}
	}
	return fd;
}

/* ---- plain C core (also used by the host test) ----
 * out[0]=pid out[1]=read fd (child stdout+stderr) out[2]=write fd (child stdin)
 * out[3]=control socket (parent end). Returns 0 on success. */
int spd_start(int usbFd, const char *workdir, char **args, int nargs, int out[4]) {
	int argc = 3 + nargs, i;
	char **argv = calloc(argc + 1, sizeof(char *));
	if (!argv) return -1;
	char fdbuf[16];
	snprintf(fdbuf, sizeof(fdbuf), "%d", usbFd);
	argv[0] = strdup("spd_dump");
	argv[1] = strdup("--usb-fd");
	argv[2] = strdup(fdbuf);
	for (i = 0; i < nargs; i++) argv[3 + i] = strdup(args[i]);

	int outp[2] = { -1, -1 }, inp[2] = { -1, -1 }, ctl[2] = { -1, -1 };
	pid_t pid = -1;
	if (pipe(outp) != 0 || pipe(inp) != 0 || socketpair(AF_UNIX, SOCK_STREAM, 0, ctl) != 0) goto fail;

	pid = fork();
	if (pid < 0) goto fail;
	if (pid == 0) {
		/* child: only simple libc work before running spd_dump */
		dup2(inp[0], 0);
		dup2(outp[1], 1);
		dup2(outp[1], 2);
		close(inp[0]); close(inp[1]); close(outp[0]); close(outp[1]);
		close(ctl[0]);
		g_ctl_fd = ctl[1];
		if (workdir && workdir[0] && chdir(workdir) != 0) _exit(126);
		signal(SIGPIPE, SIG_IGN);
		setvbuf(stdout, NULL, _IOLBF, 0);
		setvbuf(stderr, NULL, _IONBF, 0);
		int rc = spd_dump_main(argc, argv);
		spd_child_exit(rc);
	}
	/* parent */
	close(outp[1]); close(inp[0]); close(ctl[1]);
	for (i = 0; i < argc; i++) free(argv[i]);
	free(argv);
	out[0] = (int)pid; out[1] = outp[0]; out[2] = inp[1]; out[3] = ctl[0];
	return 0;

fail:
	__android_log_print(ANDROID_LOG_ERROR, TAG, "start failed: %s", strerror(errno));
	{
		int *all[] = { &outp[0], &outp[1], &inp[0], &inp[1], &ctl[0], &ctl[1] };
		for (i = 0; i < 6; i++) if (*all[i] >= 0) close(*all[i]);
	}
	for (i = 0; i < argc; i++) free(argv[i]);
	free(argv);
	return -1;
}

/* ---- JNI ---- */

/* int[] nativeStart(int usbFd, String workDir, String[] args) -> {pid, outFd, inFd, ctlFd} */
JNIEXPORT jintArray JNICALL
Java_com_spdflasher_NativeBridge_nativeStart(JNIEnv *env, jclass cls, jint usbFd,
		jstring jwork, jobjectArray jargs) {
	int n = (*env)->GetArrayLength(env, jargs), i;
	char **args = calloc(n + 1, sizeof(char *));
	if (!args) return NULL;
	for (i = 0; i < n; i++) {
		jstring s = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
		const char *c = (*env)->GetStringUTFChars(env, s, NULL);
		args[i] = strdup(c ? c : "");
		if (c) (*env)->ReleaseStringUTFChars(env, s, c);
		(*env)->DeleteLocalRef(env, s);
	}
	const char *w = jwork ? (*env)->GetStringUTFChars(env, jwork, NULL) : NULL;
	char *work = w ? strdup(w) : NULL;
	if (w) (*env)->ReleaseStringUTFChars(env, jwork, w);

	int out[4];
	int rc = spd_start((int)usbFd, work, args, n, out);
	for (i = 0; i < n; i++) free(args[i]);
	free(args);
	free(work);
	if (rc != 0) return NULL;
	jint vals[4] = { out[0], out[1], out[2], out[3] };
	jintArray res = (*env)->NewIntArray(env, 4);
	(*env)->SetIntArrayRegion(env, res, 0, 4, vals);
	return res;
}

/* int nativeWait(int pid): blocks; returns exit code, or 128+signal if killed */
JNIEXPORT jint JNICALL
Java_com_spdflasher_NativeBridge_nativeWait(JNIEnv *env, jclass cls, jint pid) {
	int status = 0;
	pid_t r;
	do { r = waitpid((pid_t)pid, &status, 0); } while (r < 0 && errno == EINTR);
	if (r < 0) return -1;
	if (WIFEXITED(status)) return WEXITSTATUS(status);
	if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
	return -1;
}

JNIEXPORT void JNICALL
Java_com_spdflasher_NativeBridge_nativeKill(JNIEnv *env, jclass cls, jint pid) {
	if (pid > 1) kill((pid_t)pid, SIGTERM);
}

/* int nativeCtlWait(int ctlFd): blocks until spd_dump asks for a new USB fd.
 * 1 = request, 0 = child closed the channel (exited), -1 = error. */
JNIEXPORT jint JNICALL
Java_com_spdflasher_NativeBridge_nativeCtlWait(JNIEnv *env, jclass cls, jint ctlFd) {
	char c;
	for (;;) {
		ssize_t n = read((int)ctlFd, &c, 1);
		if (n < 0 && errno == EINTR) continue;
		if (n == 0) return 0;
		if (n < 0) return -1;
		if (c == 'R') return 1;
	}
}

/* void nativeCtlSendFd(int ctlFd, int fd): answer a request. fd < 0 = could not get one. */
JNIEXPORT void JNICALL
Java_com_spdflasher_NativeBridge_nativeCtlSendFd(JNIEnv *env, jclass cls, jint ctlFd, jint fd) {
	char tag = fd >= 0 ? 'F' : 'N';
	char cbuf[CMSG_SPACE(sizeof(int))];
	struct iovec iov;
	struct msghdr msg;
	memset(&msg, 0, sizeof(msg));
	memset(cbuf, 0, sizeof(cbuf));
	iov.iov_base = &tag; iov.iov_len = 1;
	msg.msg_iov = &iov; msg.msg_iovlen = 1;
	if (fd >= 0) {
		struct cmsghdr *cm;
		msg.msg_control = cbuf; msg.msg_controllen = sizeof(cbuf);
		cm = CMSG_FIRSTHDR(&msg);
		cm->cmsg_level = SOL_SOCKET;
		cm->cmsg_type = SCM_RIGHTS;
		cm->cmsg_len = CMSG_LEN(sizeof(int));
		memcpy(CMSG_DATA(cm), &fd, sizeof(int));
	}
	ssize_t n;
	do { n = sendmsg((int)ctlFd, &msg, MSG_NOSIGNAL); } while (n < 0 && errno == EINTR);
}
