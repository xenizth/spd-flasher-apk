/* Force-included into the vendored spd_dump sources only.
 *
 * spd_dump was written as a standalone program: it calls exit() on every
 * error and registers atexit() handlers. We run it inside a fork()ed child
 * of the Android app process, where a real exit() would run the app
 * runtime's own exit handlers. So exit()/atexit() are redirected to small
 * replacements that flush, run spd_dump's own handlers, then _exit(). */
#ifndef SPD_COMPAT_H
#define SPD_COMPAT_H
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

void spd_child_exit(int code) __attribute__((noreturn));
int spd_child_atexit(void (*fn)(void));

/* Asks the Android app (parent process) for a fresh USB file descriptor after the
 * phone re-enumerated. Returns the new fd, or -1 on failure/timeout. */
int spd_android_reacquire_fd(int timeout_ms);

#define exit(code) spd_child_exit(code)
#define atexit(fn) spd_child_atexit(fn)
#endif
