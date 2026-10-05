package com.spdflasher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Finds the Unisoc/Spreadtrum device, gets USB permission, opens it through
 * UsbManager and runs the bundled spd_dump on the resulting file descriptor.
 * One call to {@link #run} = one spd_dump process = one USB session.
 *
 * Reconnect: some phones drop off the bus and come back (new device node) when
 * FDL1 starts. spd_dump then asks us, over a control socket, for a new
 * descriptor; a helper thread waits for the device to return, gets USB
 * permission again, opens it and passes the descriptor to spd_dump.
 */
final class SpdRunner {

    interface Listener {
        void log(String text);
        void status(String text);
    }

    static final int SPRD_VENDOR_ID = 0x1782;
    private static final String ACTION_PERMISSION = "com.spdflasher.USB_PERMISSION";
    private static final int RECONNECT_WAIT_SECONDS = 90;
    private static final int PERMISSION_WAIT_SECONDS = 30;
    /** If the same device node is still listed after this long, treat its descriptor as stale and reopen it. */
    private static final long SAME_NODE_REOPEN_MS = 10_000;

    private final Context ctx;
    private final UsbManager usb;
    private final Listener ui;

    private volatile boolean root;
    private volatile Process rootProc;
    private volatile String rootPids = "";
    private volatile boolean cancelled;
    private volatile boolean childAlive;
    private volatile int pid = -1;
    private volatile FileOutputStream childStdin;
    private volatile OutputStream rootStdin;
    private volatile UsbDevice currentDevice;
    private final List<UsbDeviceConnection> connections = new ArrayList<>();

    SpdRunner(Context context, Listener listener) {
        this.ctx = context.getApplicationContext();
        this.usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        this.ui = listener;
    }

    /** Root mode: spd_dump is started through su and opens /dev/bus/usb itself. */
    void setRoot(boolean r) { root = r; }

    /** Shared-storage folder (backups) whose ownership is fixed after a root session. */
    void setSharedDir(File d) { sharedDir = d; }
    private volatile File sharedDir;

    /** Quick check (blocking, call off the UI thread) that a working su is available. */
    static boolean checkRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l).append('\n');
            }
            if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroy(); return false; }
            return sb.toString().contains("uid=0");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Stops waiting for a device, or terminates a running spd_dump. */
    void cancel() {
        cancelled = true;
        int p = pid;
        if (p > 0) NativeBridge.nativeKill(p);
        Process rp = rootProc;
        if (rp != null) {
            final String pids = rootPids;
            new Thread(() -> {
                if (!pids.isEmpty()) {
                    try {
                        new ProcessBuilder("su", "-c", "kill -9 " + pids).start().waitFor(5, TimeUnit.SECONDS);
                    } catch (IOException | InterruptedException ignored) {
                    }
                }
                rp.destroy();
            }, "root-kill").start();
        }
    }

    /** Sends one line to spd_dump's stdin (only useful in interactive mode). */
    void sendLine(String line) {
        OutputStream out = root ? rootStdin : childStdin;
        if (out == null) return;
        try {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
        }
    }

    /**
     * Blocking. Waits up to waitSeconds for the device, then runs spd_dump.
     * @return spd_dump's exit code, or -1 if it never started
     */
    int run(File workDir, List<String> args, int waitSeconds) {
        cancelled = false;
        try {
            return runInternal(workDir, args, waitSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            pid = -1;
            rootProc = null;
            rootStdin = null;
            rootPids = "";
            childStdin = null;
            childAlive = false;
            closeConnections();
            currentDevice = null;
        }
    }

    private int runInternal(File workDir, List<String> args, int waitSeconds)
            throws InterruptedException {
        ui.status("Waiting for device...");
        ui.log("Waiting up to " + waitSeconds + " s for a Unisoc device (USB 1782:xxxx).\n"
                + "Plug the phone in now, in download mode.\n");
        UsbDevice device = null;
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (!cancelled && System.currentTimeMillis() < deadline) {
            device = findDevice();
            if (device != null) break;
            Thread.sleep(250);
        }
        if (cancelled) {
            ui.log("Cancelled.\n");
            return -1;
        }
        if (device == null) {
            ui.log("No Unisoc device appeared. Check the OTG cable and download-mode key combo.\n");
            return -1;
        }
        ui.log(String.format("Found %04x:%04x\n", device.getVendorId(), device.getProductId()));

        if (root) return runRoot(device, workDir, args);

        if (!ensurePermission(device, PERMISSION_WAIT_SECONDS)) {
            ui.log("USB permission was not granted.\n");
            return -1;
        }
        UsbDeviceConnection conn = usb.openDevice(device);
        if (conn == null) {
            ui.log("UsbManager.openDevice() failed (device gone or permission revoked).\n");
            return -1;
        }
        synchronized (connections) { connections.add(conn); }
        currentDevice = device;

        int fd = conn.getFileDescriptor();
        if (fd < 3) {
            ui.log("Android gave an invalid USB descriptor (" + fd + ").\n");
            return -1;
        }
        StringBuilder cmd = new StringBuilder("spd_dump --usb-fd " + fd);
        for (String a : args) cmd.append(' ').append(a);
        ui.log("$ " + cmd + "\n\n");

        int[] r = NativeBridge.nativeStart(fd, workDir.getAbsolutePath(), args.toArray(new String[0]));
        if (r == null) {
            ui.log("Could not start the native runner (fork failed).\n");
            return -1;
        }
        pid = r[0];
        childAlive = true;
        final int ctlFd = r[3];
        final FileInputStream childOut = new ParcelFileDescriptor.AutoCloseInputStream(
                ParcelFileDescriptor.adoptFd(r[1]));
        childStdin = new ParcelFileDescriptor.AutoCloseOutputStream(
                ParcelFileDescriptor.adoptFd(r[2]));
        ui.status("Running...");

        Thread reader = new Thread(() -> {
            char[] buf = new char[2048];
            try (InputStreamReader in = new InputStreamReader(childOut, StandardCharsets.UTF_8)) {
                int n;
                while ((n = in.read(buf)) > 0) ui.log(new String(buf, 0, n));
            } catch (IOException ignored) {
            }
        }, "spd-reader");
        reader.start();

        // Serves "give me a new USB descriptor" requests from spd_dump.
        Thread ctl = new Thread(() -> {
            while (NativeBridge.nativeCtlWait(ctlFd) == 1) {
                handleReconnect(ctlFd);
            }
        }, "spd-ctl");
        ctl.start();

        int code = NativeBridge.nativeWait(pid);
        childAlive = false;
        ctl.interrupt();
        ctl.join(8000);
        try {
            ParcelFileDescriptor.adoptFd(ctlFd).close();
        } catch (IOException ignored) {
        }

        FileOutputStream in = childStdin;
        childStdin = null;
        if (in != null) {
            try { in.close(); } catch (IOException ignored) { }
        }
        reader.join(3000);
        ui.log("\n[spd_dump exited with code " + code + "]\n");
        ui.status(code == 0 ? "Finished" : "Failed (exit " + code + ")");
        return code;
    }


    /** Single-quotes a string for /system/bin/sh. */
    static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Root mode: su -> app_process -> RootMain, which opens the USB node itself and runs the same native
     * spd_dump. Output comes back through su's stdout, typed lines go in through its stdin.
     */
    private int runRoot(UsbDevice device, File workDir, List<String> args) throws InterruptedException {
        String node = device.getDeviceName();
        if (node == null || !node.startsWith("/dev/bus/usb/")) {
            ui.log("Unexpected USB device node '" + node + "' - cannot use root mode.\n");
            return -1;
        }
        String apk = ctx.getApplicationInfo().sourceDir;
        String libDir = ctx.getApplicationInfo().nativeLibraryDir;
        File lib = new File(libDir, "libspdbridge.so");
        if (!lib.isFile()) {
            ui.log("Root mode needs the native library on disk, but " + lib + " is missing.\n");
            return -1;
        }
        // Use the app_process flavour that matches the app's ABI (32-bit userspace phones are common on Unisoc).
        String pref = libDir.endsWith("/arm64") ? "app_process64" : libDir.endsWith("/arm") ? "app_process32" : "app_process";
        int uid = android.os.Process.myUid();
        StringBuilder sh = new StringBuilder();
        sh.append("AP=/system/bin/").append(pref).append("; [ -x \"$AP\" ] || AP=/system/bin/app_process; ");
        sh.append("CLASSPATH=").append(shq(apk)).append(" \"$AP\" /system/bin com.spdflasher.RootMain ")
                .append(shq(lib.getAbsolutePath())).append(' ').append(shq(node)).append(' ')
                .append(shq(workDir.getAbsolutePath()));
        for (String a : args) sh.append(' ').append(shq(a));
        // Hand files that root created back to the app, so Files / Restore can still read and delete them.
        StringBuilder dirs = new StringBuilder(shq(workDir.getAbsolutePath()));
        File bk = sharedDir;
        if (bk != null) dirs.append(' ').append(shq(bk.getAbsolutePath()));
        sh.append("; rc=$?; chown -R ").append(uid).append(':').append(uid).append(' ').append(dirs)
                .append(" 2>/dev/null; chmod -R a+rwX ").append(dirs).append(" 2>/dev/null; exit $rc");
        return runRootShell(sh.toString());
    }

    private int runRootShell(String script) throws InterruptedException {
        ui.log("$ su -c  (root helper: RootMain + spd_dump)\n");
        ui.status("Starting root helper...");
        Process p;
        try {
            p = new ProcessBuilder("su", "-c", script).redirectErrorStream(true).start();
        } catch (IOException e) {
            ui.log("Could not run su: " + e.getMessage() + "\nIs the phone rooted? Switch Connection Mode back to No Root.\n");
            return -1;
        }
        rootProc = p;
        rootStdin = p.getOutputStream();
        childAlive = true;
        final InputStream in = p.getInputStream();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[2048];
                int n;
                while ((n = br.read(buf)) > 0) {
                    String chunk = new String(buf, 0, n);
                    int i = chunk.indexOf("[root] pids ");
                    if (i >= 0) {
                        int e = chunk.indexOf('\n', i);
                        String line = chunk.substring(i + 12, e < 0 ? chunk.length() : e).trim();
                        if (line.matches("[0-9 ]+")) {
                            rootPids = line;
                            ui.status("Running (root)...");
                        }
                    }
                    ui.log(chunk);
                }
            } catch (IOException ignored) {
            }
        }, "root-reader");
        reader.start();
        int code = p.waitFor();
        childAlive = false;
        reader.join(4000);
        ui.log("\n[root session exited with code " + code + "]\n");
        if (code == 1 && rootPids.isEmpty())
            ui.log("Nothing ran. Open your root manager (Magisk / KernelSU) and allow root for SPD Tool, then retry.\n");
        ui.status(code == 0 ? "Finished" : "Failed (exit " + code + ")");
        return code;
    }

    /** spd_dump says the phone left the bus: wait for it, get permission, hand over a new descriptor. */
    private void handleReconnect(int ctlFd) {
        ui.status("Reconnecting...");
        ui.log("\n[reconnect] phone left the USB bus - waiting for it to come back "
                + "(tap OK if Android asks for USB permission again)\n");
        int fd = -1;
        try {
            UsbDevice nd = waitForReturn(currentDevice, RECONNECT_WAIT_SECONDS);
            if (nd == null) {
                ui.log("[reconnect] device did not come back\n");
            } else if (!ensurePermission(nd, PERMISSION_WAIT_SECONDS)) {
                ui.log("[reconnect] USB permission not granted\n");
            } else {
                UsbDeviceConnection c = usb.openDevice(nd);
                if (c == null) {
                    ui.log("[reconnect] openDevice() failed\n");
                } else if (c.getFileDescriptor() < 3) {
                    c.close();
                    ui.log("[reconnect] Android gave an invalid descriptor\n");
                } else {
                    synchronized (connections) { connections.add(c); }
                    currentDevice = nd;
                    fd = c.getFileDescriptor();
                    ui.log(String.format("[reconnect] reopened %04x:%04x\n",
                            nd.getVendorId(), nd.getProductId()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        NativeBridge.nativeCtlSendFd(ctlFd, fd);
        if (fd >= 0) ui.status("Running...");
    }

    /** Waits for a Unisoc device on a different node than the one that vanished. */
    private UsbDevice waitForReturn(UsbDevice old, int seconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        long deadline = start + seconds * 1000L;
        String oldName = old != null ? old.getDeviceName() : null;
        while (childAlive && !cancelled && System.currentTimeMillis() < deadline) {
            for (UsbDevice d : usb.getDeviceList().values()) {
                if (d.getVendorId() != SPRD_VENDOR_ID) continue;
                if (oldName == null || !oldName.equals(d.getDeviceName())) return d;
                // Same node still listed: the old descriptor is probably just stale.
                if (System.currentTimeMillis() - start > SAME_NODE_REOPEN_MS) return d;
            }
            Thread.sleep(200);
        }
        return null;
    }

    private void closeConnections() {
        synchronized (connections) {
            for (UsbDeviceConnection c : connections) {
                try { c.close(); } catch (RuntimeException ignored) { }
            }
            connections.clear();
        }
    }

    private UsbDevice findDevice() {
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (d.getVendorId() == SPRD_VENDOR_ID) return d;
        }
        return null;
    }

    private boolean ensurePermission(UsbDevice device, int timeoutSeconds)
            throws InterruptedException {
        if (usb.hasPermission(device)) return true;
        ui.status("Waiting for USB permission...");
        ui.log("Tap OK on the Android USB permission dialog.\n");

        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (ACTION_PERMISSION.equals(intent.getAction())) latch.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, filter);
        }
        try {
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(ACTION_PERMISSION).setPackage(ctx.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, flags);
            usb.requestPermission(device, pi);
            latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            ctx.unregisterReceiver(receiver);
        }
        return usb.hasPermission(device);
    }
}
