package com.spdflasher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Root-mode helper. The app starts it with
 * {@code su -c "CLASSPATH=<apk> app_process /system/bin com.spdflasher.RootMain <lib> <usb node> <workdir> <spd_dump args...>"}.
 *
 * It opens the USB device node directly (root may), runs the same bundled spd_dump as the
 * no-root path, relays its output to stdout and stdin to it, and answers "phone re-enumerated"
 * requests by finding the returning Unisoc device itself. Plain java.io only, so it also runs on a PC for tests.
 */
public final class RootMain {
    private static final String SPRD_VENDOR = "1782";
    private static volatile String currentNode;

    private RootMain() {}

    public static void main(String[] a) {
        if (a.length < 3) {
            System.out.println("usage: RootMain <libspdbridge.so> <usb node> <workdir> [spd_dump args...]");
            Runtime.getRuntime().halt(2);
        }
        System.setProperty("spd.lib", a[0]);
        currentNode = a[1];
        String[] spdArgs = Arrays.copyOfRange(a, 3, a.length);

        int fd = NativeBridge.nativeOpenNode(currentNode);
        if (fd < 0) {
            System.out.println("[root] cannot open " + currentNode + " (errno " + (-fd) + ")");
            System.out.flush();
            Runtime.getRuntime().halt(3);
        }
        int[] r = NativeBridge.nativeStart(fd, a[2], spdArgs);
        if (r == null) {
            System.out.println("[root] could not start spd_dump (fork failed)");
            System.out.flush();
            Runtime.getRuntime().halt(4);
        }
        final int pid = r[0];
        // The app parses this line so Stop can kill both processes.
        System.out.println("[root] pids " + selfPid() + " " + pid);
        System.out.flush();

        final int outFd = r[1], inFd = r[2], ctlFd = r[3];
        Thread pumpOut = new Thread(() -> {
            try (InputStream in = new FileInputStream("/proc/self/fd/" + outFd)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    System.out.write(buf, 0, n);
                    System.out.flush();
                }
            } catch (IOException ignored) {
            }
        }, "pump-out");
        pumpOut.start();

        Thread pumpIn = new Thread(() -> {
            try (OutputStream out = new FileOutputStream("/proc/self/fd/" + inFd)) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = System.in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }, "pump-in");
        pumpIn.setDaemon(true);
        pumpIn.start();

        Thread ctl = new Thread(() -> {
            while (NativeBridge.nativeCtlWait(ctlFd) == 1) {
                NativeBridge.nativeCtlSendFd(ctlFd, reacquire());
            }
        }, "ctl");
        ctl.setDaemon(true);
        ctl.start();

        int code = NativeBridge.nativeWait(pid);
        try { pumpOut.join(3000); } catch (InterruptedException ignored) { }
        System.out.println("[root] spd_dump exited with code " + code);
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }

    /** Finds the returning Unisoc device in sysfs and opens its node. Returns an fd or -1. */
    private static int reacquire() {
        long start = System.currentTimeMillis();
        String old = currentNode;
        System.out.println("\n[reconnect] phone left the USB bus - waiting for it to come back");
        System.out.flush();
        while (System.currentTimeMillis() - start < 90_000) {
            String node = findUnisocNode();
            if (node != null && (!node.equals(old) || System.currentTimeMillis() - start > 10_000)) {
                int fd = NativeBridge.nativeOpenNode(node);
                if (fd >= 0) {
                    currentNode = node;
                    System.out.println("[reconnect] reopened " + node);
                    System.out.flush();
                    return fd;
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { return -1; }
        }
        System.out.println("[reconnect] device did not come back");
        System.out.flush();
        return -1;
    }

    private static String findUnisocNode() {
        File[] devs = new File("/sys/bus/usb/devices").listFiles();
        if (devs == null) return null;
        for (File d : devs) {
            String vid = readLine(new File(d, "idVendor"));
            if (!SPRD_VENDOR.equalsIgnoreCase(vid)) continue;
            try {
                int bus = Integer.parseInt(readLine(new File(d, "busnum")));
                int dev = Integer.parseInt(readLine(new File(d, "devnum")));
                return String.format(Locale.US, "/dev/bus/usb/%03d/%03d", bus, dev);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String readLine(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s = br.readLine();
            return s == null ? "" : s.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String selfPid() {
        String s = readLine(new File("/proc/self/stat"));
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : "0";
    }
}
