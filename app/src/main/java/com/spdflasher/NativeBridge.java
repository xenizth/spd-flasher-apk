package com.spdflasher;

/** JNI entry points for the forked spd_dump runner (see cpp/bridge.c). */
final class NativeBridge {
    static {
        System.loadLibrary("spdbridge");
    }

    private NativeBridge() {}

    /**
     * Forks a child that runs spd_dump with {@code --usb-fd usbFd args...}.
     * @return {pid, readFd (child stdout+stderr), writeFd (child stdin), ctlFd} or null
     */
    static native int[] nativeStart(int usbFd, String workDir, String[] args);

    /** Blocks until the child ends; returns its exit code (128+signal if killed). */
    static native int nativeWait(int pid);

    static native void nativeKill(int pid);

    /** Blocks until spd_dump wants a new USB descriptor: 1 = request, 0 = child gone, -1 = error. */
    static native int nativeCtlWait(int ctlFd);

    /** Answers a request with a new USB descriptor, or fd &lt; 0 if none could be obtained. */
    static native void nativeCtlSendFd(int ctlFd, int fd);
}
