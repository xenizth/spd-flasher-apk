package com.spdflasher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements SpdRunner.Listener {

    private static final int REQ_IMAGES = 1, REQ_FDL1 = 2, REQ_FDL2 = 3, REQ_EXPORT = 4, REQ_XML = 5, REQ_STORAGE = 6;
    private static final int TAB_DEVICE = 0, TAB_OPS = 1, TAB_FILES = 2, TAB_CONSOLE = 3;
    private static final int SUB_INPUT = 0, SUB_BACKUP = 1, SUB_FDL = 2;

    private static final Pattern ADDR = Pattern.compile("^(0[xX][0-9a-fA-F]+|[0-9]+)$");
    private static final Pattern PART = Pattern.compile("^[A-Za-z0-9_\\-]+$");
    /** Partitions that can leave a phone unbootable or hold unique device data. */
    private static final Set<String> RISKY = new HashSet<>(Arrays.asList(
            "persist", "splloader", "uboot", "uboot_a", "uboot_b", "sml", "trustos", "teecfg",
            "fixnv1", "fixnv2", "runtimenv1", "runtimenv2", "prodnv", "nvitem", "miscdata", "misc",
            "l_fixnv1", "l_fixnv2", "l_runtimenv1", "l_runtimenv2", "l_modem", "l_deltanv"));
    private static final String[] NV_PARTS = {
            "miscdata", "prodnv", "l_fixnv1", "l_fixnv2", "l_runtimenv1", "l_runtimenv2"};
    /** Android bootloader control block that makes recovery wipe userdata (shipped by the source repo). */
    private static final String MISC_WIPE_SHA256 =
            "bd6b67e852d6072e6fb87040f2ac40216d5b661b7fa661e7024569ecf8ddb3a7";
    private static final String[] REBOOT_LABELS = {"System", "Recovery", "Fastboot", "Power Off"};
    private static final String[] REBOOT_CMDS = {"reset", "reboot-recovery", "reboot-fastboot", "poweroff"};
    private static final String[] HEX_LABELS = {"Standard", "Alternate", "None"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private SpdRunner runner;
    private volatile boolean busy;
    private SharedPreferences prefs;

    private File inputDir, backupDir, workDir, fdlRoot;

    // user state (persisted)
    private String chipId, brandId;
    private int hexMode, rebootMode, filesSub;
    private boolean rootMode;
    private int currentTab = -1;

    // views
    private final View[] tabViews = new View[4];
    private final View[] navPills = new View[4];
    private final TextView[] navLabels = new TextView[4];
    private LinearLayout deviceBox, opsBox, filesBox;
    private TextView statusLine, logView;
    private ScrollView logScroll;
    private EditText stdinInput;

    private final StringBuilder logBuf = new StringBuilder();
    private boolean pendingCr, flushScheduled;
    private String statusText = "";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            main.post(() -> { if (currentTab == TAB_DEVICE) renderDevice(); });
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        workDir = new File(getFilesDir(), "work");
        prefs = getSharedPreferences("spdflasher", MODE_PRIVATE);
        rootMode = prefs.getBoolean("root", false);
        backupDir = resolveBackupDir();
        fdlRoot = resolveFdlRoot();
        inputDir = resolveInputDir();
        inputDir.mkdirs(); workDir.mkdirs(); fdlRoot.mkdirs();
        chipId = prefs.getString("chip", null);
        brandId = prefs.getString("brand", null);
        hexMode = prefs.getInt("hex", 0);
        rebootMode = prefs.getInt("reboot", 0);
        filesSub = SUB_INPUT;
        if (chipId != null && Profiles.find(chipId) == null) { chipId = null; brandId = null; }
        ensurePreset();

        runner = new SpdRunner(this, this);
        runner.setSharedDir(backupDir);
        setContentView(buildRoot());
        append("SPD Flasher - wraps TomKing's spd_dump (libusb) for Unisoc/Spreadtrum phones.\n"
                + "Backups: " + backupDir.getAbsolutePath() + "\nLoaders: " + fdlRoot.getAbsolutePath() + "\nInput: " + inputDir.getAbsolutePath() + "\n\n");
        renderAll();
        selectTab(TAB_DEVICE);
        if (!hasAllFiles() && !prefs.getBoolean("askedStorage", false)) {
            prefs.edit().putBoolean("askedStorage", true).apply();
            askStorageAccess();
        }
    }

    // ------------------------------------------------------------------ backup folder / storage access

    /** Folder the user asked for: /storage/emulated/0/SPD-FLASHER. */
    @SuppressWarnings("deprecation")
    private File sharedBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), "SPD-FLASHER");
    }

    private boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private boolean usingSharedBackup() {
        return backupDir != null && backupDir.equals(sharedBackupDir());
    }

    /** SPD-FLASHER on shared storage when we may write there, else the app's own external folder. */
    private File resolveBackupDir() {
        File shared = sharedBackupDir();
        if (hasAllFiles()) {
            shared.mkdirs();
            if (shared.isDirectory() && shared.canWrite()) return shared;
        }
        File ext = getExternalFilesDir("backup");
        File d = ext != null ? ext : new File(getFilesDir(), "backup");
        d.mkdirs();
        return d;
    }

    /**
     * Loaders live in /storage/emulated/0/SPD-FLASHER/FDLS/<chip>/<brand>/ when storage access is granted,
     * so they are easy to copy in by hand; otherwise in the app's private folder.
     */
    private File resolveFdlRoot() {
        File priv = new File(getFilesDir(), "fdl");
        File root = priv;
        if (usingSharedBackup()) {
            File shared = new File(sharedBackupDir(), "FDLS");
            shared.mkdirs();
            if (shared.isDirectory() && shared.canWrite()) root = shared;
        }
        root.mkdirs();
        if (!root.equals(priv)) {
            // move loaders from the old private location the first time
            if (listProfilesIn(root).isEmpty() && !listProfilesIn(priv).isEmpty()) copyTree(priv, root);
        }
        // empty chip/brand folders, so the layout is visible in a file manager
        for (Profiles.Chip c : Profiles.CHIPS)
            for (String b : c.brands) new File(root, c.id + "/" + b.toLowerCase(Locale.US)).mkdirs();
        return root;
    }

    /** Images to flash: /storage/emulated/0/SPD-FLASHER/input when storage access is granted. */
    private File resolveInputDir() {
        File priv = new File(getFilesDir(), "input");
        File dir = priv;
        if (usingSharedBackup()) {
            File shared = new File(sharedBackupDir(), "input");
            shared.mkdirs();
            if (shared.isDirectory() && shared.canWrite()) dir = shared;
        }
        dir.mkdirs();
        if (!dir.equals(priv)) copyTree(priv, dir);
        return dir;
    }

    private static void copyTree(File from, File to) {
        File[] kids = from.listFiles();
        if (kids == null) return;
        to.mkdirs();
        for (File k : kids) {
            File dest = new File(to, k.getName());
            if (k.isDirectory()) { copyTree(k, dest); continue; }
            if (dest.exists()) continue;
            try (InputStream in = new FileInputStream(k); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (IOException ignored) {
            }
        }
    }

    private void askStorageAccess() {
        new AlertDialog.Builder(this).setTitle("Backup folder access")
                .setMessage("Backups are saved to\n" + sharedBackupDir().getAbsolutePath()
                        + "\n\nAndroid needs your OK for that: on the next screen allow 'All files access' for SPD Flasher. "
                        + "Without it, backups go to the app's private folder instead.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Allow", (d, w) -> requestStorageAccess()).show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQ_STORAGE);
            } catch (RuntimeException e) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_STORAGE) refreshBackupDir();
    }

    private void refreshBackupDir() {
        File old = backupDir;
        backupDir = resolveBackupDir();
        fdlRoot = resolveFdlRoot();
        inputDir = resolveInputDir();
        ensurePreset();
        runner.setSharedDir(backupDir);
        if (!backupDir.equals(old)) append("Backup folder: " + backupDir.getAbsolutePath() + "\nLoaders folder: " + fdlRoot.getAbsolutePath() + "\n");
        if (!busy) renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(usbReceiver, f);
        if (backupDir != null && !busy && !backupDir.equals(resolveBackupDir())) refreshBackupDir();
        if (currentTab == TAB_DEVICE) renderDevice();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(usbReceiver); } catch (IllegalArgumentException ignored) { }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        main.post(() -> { renderAll(); });
    }

    @Override
    public void onBackPressed() {
        if (busy) {
            toast("Session still running - moved to background");
            moveTaskToBack(true);
        } else if (currentTab != TAB_DEVICE) {
            selectTab(TAB_DEVICE);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ shell: top bar, tabs, bottom nav

    private int dp(float v) { return Ui.dp(this, v); }

    private View buildRoot() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);

        // top bar
        LinearLayout bar = new Ui.CappedRow(this);
        bar.setPadding(dp(8), dp(8), dp(8), dp(4));
        bar.addView(barButton("ℹ️", v -> showAbout()));
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(new Logo());
        bar.addView(logo, Ui.lp(this, dp(34), dp(34), 0, 0, 10, 0));
        TextView title = Ui.text(this, "SPD Flasher", 22, Ui.TEXT, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(barButton("🔄", v -> onRefresh()));
        bar.addView(barButton("⚙️", v -> showSettings()));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(bar, barLp);

        statusLine = Ui.text(this, "", 13, Ui.ACCENT, true);
        statusLine.setPadding(dp(20), 0, dp(20), dp(4));
        statusLine.setVisibility(View.GONE);
        root.addView(statusLine);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        deviceBox = addScrollTab(content, TAB_DEVICE);
        opsBox = addScrollTab(content, TAB_OPS);
        filesBox = addScrollTab(content, TAB_FILES);
        View console = buildConsole();
        tabViews[TAB_CONSOLE] = console;
        content.addView(console, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // bottom navigation: floating rounded bar, selected tab gets a glowing pill
        LinearLayout nav = new Ui.CappedRow(this);
        nav.setBackground(Ui.panel(this, 0xFF2C3140, 0xFF1D2028, 30));
        nav.setPadding(dp(6), dp(8), dp(6), dp(8));
        nav.setElevation(dp(10));
        String[] glyphs = {"📱", "🧰", "📁", "🖥️"};
        String[] labels = {"Device", "Operations", "Files", "Console"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = Ui.vbox(this);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setOnClickListener(v -> selectTab(idx));
            TextView pill = Ui.text(this, glyphs[i], 22, Ui.TEXT, false);
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22);
            pill.setGravity(Gravity.CENTER);
            pill.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(34)));
            navPills[i] = pill;
            item.addView(pill);
            TextView lab = Ui.text(this, labels[i], 12, Ui.MUTED, false);
            lab.setGravity(Gravity.CENTER);
            lab.setSingleLine(true);
            lab.setPadding(0, dp(3), 0, 0);
            navLabels[i] = lab;
            item.addView(lab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams navLp = Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 12, 4, 12, 10);
        navLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(nav, navLp);
        return root;
    }

    private TextView barButton(String glyph, View.OnClickListener l) {
        TextView t = Ui.text(this, glyph, 22, Ui.ACCENT, false);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return t;
    }

    private LinearLayout addScrollTab(FrameLayout parent, int tab) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout box = new Ui.Capped(this);
        box.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.setFillViewport(true);
        sv.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        parent.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        tabViews[tab] = sv;
        return box;
    }

    private void selectTab(int tab) {
        currentTab = tab;
        for (int i = 0; i < 4; i++) {
            tabViews[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            boolean sel = i == tab;
            navPills[i].setBackground(sel ? Ui.panel(this, 0xFF4C5E94, 0xFF33406A, 17) : null);
            navPills[i].setAlpha(sel ? 1f : 0.6f);
            navLabels[i].setTextColor(sel ? Ui.ACCENT : Ui.MUTED);
            navLabels[i].setTypeface(sel ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
        if (tab == TAB_DEVICE) renderDevice();
        else if (tab == TAB_OPS) renderOps();
        else if (tab == TAB_FILES) renderFiles();
    }

    private void renderAll() {
        renderDevice();
        renderOps();
        renderFiles();
    }

    private void onRefresh() {
        renderAll();
        toast("Refreshed");
    }

    // ------------------------------------------------------------------ Device tab

    private List<UsbDevice> unisocDevices() {
        List<UsbDevice> out = new ArrayList<>();
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (um == null) return out;
        for (UsbDevice d : um.getDeviceList().values()) {
            if (d.getVendorId() == SpdRunner.SPRD_VENDOR_ID) out.add(d);
        }
        return out;
    }

    private void renderDevice() {
        if (deviceBox == null) return;
        deviceBox.removeAllViews();

        // hero
        LinearLayout hero = Ui.hbox(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(Ui.panel(this, 0xFF35508F, 0xFF161A24, 28));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        ImageView hl = new ImageView(this);
        hl.setImageDrawable(new Logo());
        hero.addView(hl, Ui.lp(this, dp(60), dp(60), 0, 0, 16, 0));
        LinearLayout ht = Ui.vbox(this);
        ht.addView(Ui.text(this, "SPD Flasher", 24, Ui.TEXT, true));
        ht.addView(Ui.text(this, "Unisoc / Spreadtrum toolkit  ·  " + (rootMode ? "Root" : "No Root"), 14, Ui.MUTED, false));
        hero.addView(ht, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        deviceBox.addView(hero, Ui.fullLp(this, 8, 8));

        // connection card
        List<UsbDevice> devs = unisocDevices();
        LinearLayout c1 = Ui.card(this, Ui.CARD);
        LinearLayout head = Ui.hbox(this);
        boolean on = !devs.isEmpty();
        head.addView(Ui.iconBox(this, on ? "🔌" : "⚠️", on ? Ui.GREEN_BG : Ui.RED_BG,
                on ? Ui.GREEN : Ui.RED_TEXT, 48), Ui.wrapLp(this, 0, 0, 14, 0));
        head.addView(Ui.text(this, on ? "Unisoc Device Connected" : "No Device Connected", 20, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String idText = on ? String.format(Locale.US, "%04x:%04x", devs.get(0).getVendorId(),
                devs.get(0).getProductId()) : "NONE";
        head.addView(Ui.badge(this, idText.toUpperCase(Locale.US), on ? Ui.GREEN : 0xFFFFFFFF, on ? Ui.GREEN_BG : Ui.RED));
        c1.addView(head);
        String msg = on
                ? "Download-mode device detected on USB. Tap an operation and the app will connect."
                : "Nothing connected yet. Pick an operation first, then plug the phone in (OTG) while holding its download-mode key.";
        TextView m = Ui.text(this, msg, 15, Ui.MUTED, false);
        m.setLineSpacing(0, 1.15f);
        c1.addView(m, Ui.fullLp(this, 12, 0));
        deviceBox.addView(c1, Ui.fullLp(this, 8, 8));

        // connection mode card
        LinearLayout cm = Ui.card(this, Ui.CARD);
        cm.addView(Ui.text(this, "🔑  Connection Mode", 17, Ui.ACCENT, true));
        HorizontalScrollView modes = Ui.chipRow(this);
        Ui.addChip(modes, Ui.chip(this, "No Root (Android USB)", !rootMode, v -> setRootMode(false)));
        Ui.addChip(modes, Ui.chip(this, "Root (su)", rootMode, v -> setRootMode(true)));
        cm.addView(modes, Ui.fullLp(this, 10, 0));
        TextView mt = Ui.text(this, rootMode
                ? "Root: the app asks su to open the USB device directly. No Android USB permission pop-ups, and the phone can come back on a new "
                + "USB address without a prompt. Experimental - allow root for this app in Magisk / KernelSU."
                : "No Root: works on any phone. Android hands the app the USB device after you tap OK on the permission dialog.",
                13, Ui.MUTED, false);
        mt.setLineSpacing(0, 1.15f);
        cm.addView(mt, Ui.fullLp(this, 10, 0));
        deviceBox.addView(cm, Ui.fullLp(this, 8, 8));

        // toolchain card
        LinearLayout c2 = Ui.card(this, Ui.CARD);
        c2.addView(Ui.text(this, "🧰  Platform Binaries & Toolchain", 17, Ui.ACCENT, true));
        c2.addView(Ui.text(this, "Everything is built into the app. No Termux or downloads, and root is optional.", 14, Ui.MUTED, false),
                Ui.fullLp(this, 6, 12));
        boolean fdlOk = hasProfile() && fdlFile(1).isFile() && fdlFile(2).isFile();
        LinearLayout r1 = Ui.hbox(this);
        r1.addView(miniStatus("⚡", "spd_dump", "Spreadtrum BROM Engine", true), miniLp(true));
        r1.addView(miniStatus("🔌", "libusb 1.0.27", "USB host driver", true), miniLp(false));
        c2.addView(r1, Ui.fullLp(this, 0, 0));
        LinearLayout r2 = Ui.hbox(this);
        r2.addView(miniStatus("💾", "FDL pair", hasProfile() ? chipLabel() + " / " + brandLabel() : "No profile", fdlOk),
                miniLp(true));
        r2.addView(miniStatus("🔄", "Reconnect", "Re-attaches after FDL1", true), miniLp(false));
        c2.addView(r2, Ui.fullLp(this, 8, 0));
        deviceBox.addView(c2, Ui.fullLp(this, 8, 8));

        // profile card
        LinearLayout c3 = Ui.card(this, Ui.CARD);
        LinearLayout h3 = Ui.hbox(this);
        h3.addView(Ui.text(this, "📱  Device Hardware Profile", 17, Ui.ACCENT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView auto = Ui.text(this, "✨ Auto-Detect", 14, Ui.TEXT, true);
        auto.setBackground(Ui.ripple(Ui.round(this, Ui.CHIP_SEL, 14)));
        auto.setPadding(dp(14), dp(8), dp(14), dp(8));
        auto.setClickable(true);
        auto.setOnClickListener(v -> autoDetect());
        h3.addView(auto);
        c3.addView(h3);

        String problem = profileProblem();
        if (!hasProfile()) {
            c3.addView(Ui.banner(this, "⚠️", "Device setup required: pick a chipset and brand below before running operations.",
                    Ui.RED_BANNER, Ui.RED_TEXT), Ui.fullLp(this, 12, 0));
        } else {
            String ok = "Active profile: " + chipLabel() + " / " + brandLabel() + "\n"
                    + (problem == null ? "FDL pair ready" : problem);
            c3.addView(Ui.banner(this, problem == null ? "✅" : "⚠️", ok,
                    problem == null ? Ui.BLUE_BANNER : Ui.AMBER_BG, problem == null ? Ui.TEXT : Ui.AMBER),
                    Ui.fullLp(this, 12, 0));
        }

        c3.addView(Ui.text(this, "Chipset Model", 16, Ui.MUTED, true), Ui.fullLp(this, 16, 8));
        HorizontalScrollView chips = Ui.chipRow(this);
        for (Profiles.Chip chip : Profiles.CHIPS) {
            final Profiles.Chip cc = chip;
            Ui.addChip(chips, Ui.chip(this, chip.label, chip.id.equals(chipId), v -> selectChip(cc)));
        }
        c3.addView(chips);

        c3.addView(Ui.text(this, "Manufacturer / Brand", 16, Ui.MUTED, true), Ui.fullLp(this, 16, 8));
        Profiles.Chip cur = chipId == null ? null : Profiles.find(chipId);
        if (cur == null) {
            c3.addView(Ui.text(this, "Select a chipset first.", 14, Ui.MUTED, false));
        } else {
            HorizontalScrollView brands = Ui.chipRow(this);
            for (String b : cur.brands) {
                final String bid = b.toLowerCase(Locale.US);
                Ui.addChip(brands, Ui.chip(this, b, bid.equals(brandId), v -> selectProfile(chipId, bid)));
            }
            c3.addView(brands);

            c3.addView(Ui.text(this, "Load addresses", 16, Ui.MUTED, true), Ui.fullLp(this, 16, 4));
            c3.addView(Ui.text(this, "Defaults for the chosen chipset. Change them only if your loader documents other values.",
                    13, Ui.MUTED, false), Ui.fullLp(this, 0, 4));
            c3.addView(addrField("FDL1 address", "fdl1"), Ui.fullLp(this, 6, 0));
            c3.addView(addrField("FDL2 address", "fdl2"), Ui.fullLp(this, 6, 0));
            c3.addView(addrField("exec_addr (Standard hex mode)", "exec"), Ui.fullLp(this, 6, 0));
            c3.addView(addrField("exec_addr (Alternate hex mode)", "alt"), Ui.fullLp(this, 6, 0));
        }
        deviceBox.addView(c3, Ui.fullLp(this, 8, 8));
    }

    private void setRootMode(final boolean want) {
        if (busy) { toast("A session is running"); return; }
        if (!want) {
            rootMode = false;
            prefs.edit().putBoolean("root", false).apply();
            renderDevice();
            return;
        }
        toast("Asking for root...");
        new Thread(() -> {
            final boolean ok = SpdRunner.checkRoot();
            main.post(() -> {
                if (ok) {
                    rootMode = true;
                    prefs.edit().putBoolean("root", true).apply();
                } else {
                    toast("Root was not granted. Install Magisk / KernelSU and allow SPD Flasher, then try again.");
                }
                renderDevice();
            });
        }, "root-check").start();
    }

    private LinearLayout.LayoutParams miniLp(boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(first ? 0 : 4);
        p.rightMargin = dp(first ? 4 : 0);
        return p;
    }

    private View miniStatus(String glyph, String name, String sub, boolean ready) {
        LinearLayout l = Ui.card(this, Ui.CARD_DARK);
        l.setBackground(Ui.round(this, Ui.CARD_DARK, 20));
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.iconBox(this, glyph, ready ? Ui.GREEN_BG : Ui.RED_BG, ready ? Ui.GREEN : Ui.RED_TEXT, 40));
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(Ui.badge(this, ready ? "READY" : "MISSING", ready ? Ui.GREEN : Ui.RED_TEXT,
                ready ? Ui.GREEN_BG : Ui.RED_BG));
        l.addView(top);
        l.addView(Ui.text(this, name, 17, Ui.TEXT, true), Ui.fullLp(this, 10, 0));
        TextView s = Ui.text(this, sub, 13, Ui.MUTED, false);
        l.addView(Ui.ellipsized(s, 1), Ui.fullLp(this, 2, 0));
        return l;
    }

    private View addrField(String hint, final String key) {
        final EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(addr(key));
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setTextSize(15);
        e.setTypeface(android.graphics.Typeface.MONOSPACE);
        e.setBackground(Ui.round(this, Ui.CARD_DARK, 14));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(chipId + "." + key, s.toString().trim()).apply();
            }
        });
        return e;
    }

    // ------------------------------------------------------------------ profile state

    private boolean hasProfile() { return chipId != null && brandId != null; }

    private String chipLabel() {
        Profiles.Chip c = chipId == null ? null : Profiles.find(chipId);
        return c == null ? "-" : c.label;
    }

    private String brandLabel() {
        if (brandId == null) return "-";
        Profiles.Chip c = chipId == null ? null : Profiles.find(chipId);
        if (c != null) for (String b : c.brands) if (b.toLowerCase(Locale.US).equals(brandId)) return b;
        return brandId;
    }

    private File profileDir(String chip, String brand) { return new File(fdlRoot, chip + "/" + brand); }

    private File fdlFile(int n) { return new File(profileDir(chipId, brandId), "fdl" + n + ".bin"); }

    private String addr(String key) {
        Profiles.Chip c = Profiles.find(chipId);
        String def = "";
        if (c != null) def = key.equals("fdl1") ? c.fdl1 : key.equals("fdl2") ? c.fdl2
                : key.equals("exec") ? c.exec : c.execAlt;
        return prefs.getString(chipId + "." + key, def);
    }

    private void selectChip(Profiles.Chip chip) {
        chipId = chip.id;
        brandId = chip.brands.length == 1 ? chip.brands[0].toLowerCase(Locale.US) : null;
        savePrefs();
        ensurePreset();
        renderAll();
    }

    private void selectProfile(String chip, String brand) {
        chipId = chip;
        brandId = brand;
        savePrefs();
        ensurePreset();
        renderAll();
    }

    private void savePrefs() {
        prefs.edit().putString("chip", chipId).putString("brand", brandId)
                .putInt("hex", hexMode).putInt("reboot", rebootMode).apply();
    }

    /** The one loader pair bundled with the app: Infinix UMS9230. */
    private void ensurePreset() {
        if (!"ums9230".equals(chipId) || !"infinix".equals(brandId)) return;
        File dir = profileDir("ums9230", "infinix");
        File f1 = new File(dir, "fdl1.bin"), f2 = new File(dir, "fdl2.bin");
        if (f1.isFile() && f2.isFile()) return;
        dir.mkdirs();
        try {
            assetCopy("fdl/ums9230_infinix/fdl1-dl.bin", f1);
            assetCopy("fdl/ums9230_infinix/fdl2-dl.bin", f2);
        } catch (IOException e) {
            toast("Could not unpack the built-in loaders: " + e.getMessage());
        }
    }

    private List<String[]> listProfiles() { return listProfilesIn(fdlRoot); }

    private static List<String[]> listProfilesIn(File root) {
        List<String[]> out = new ArrayList<>();
        File[] chips = root.listFiles();
        if (chips == null) return out;
        Arrays.sort(chips);
        for (File c : chips) {
            File[] brands = c.listFiles();
            if (brands == null) continue;
            Arrays.sort(brands);
            for (File b : brands) {
                String[] files = b.list();
                if (b.isDirectory() && files != null && files.length > 0) out.add(new String[]{c.getName(), b.getName()});
            }
        }
        return out;
    }

    private void autoDetect() {
        if (listProfiles().isEmpty()) {
            chipId = "ums9230";
            brandId = "infinix";
            ensurePreset();
        }
        final List<String[]> found = listProfiles();
        if (found.isEmpty()) { toast("No loader files found. Import them in Files > FDLs."); return; }
        if (found.size() == 1) {
            selectProfile(found.get(0)[0], found.get(0)[1]);
            toast("Profile: " + found.get(0)[0] + " / " + found.get(0)[1]);
            return;
        }
        String[] names = new String[found.size()];
        for (int i = 0; i < names.length; i++) names[i] = found.get(i)[0] + " / " + found.get(i)[1];
        new AlertDialog.Builder(this).setTitle("Choose profile").setItems(names,
                (d, w) -> selectProfile(found.get(w)[0], found.get(w)[1])).show();
    }

    /** null if the profile can run operations, otherwise a short reason. */
    private String profileProblem() {
        if (!hasProfile()) return "Select a chipset and brand in the Device tab first.";
        if (!fdlFile(1).isFile() || !fdlFile(2).isFile())
            return "FDL1/FDL2 missing for " + chipLabel() + " / " + brandLabel() + ". Import them in Files > FDLs.";
        if (!ADDR.matcher(addr("fdl1")).matches() || !ADDR.matcher(addr("fdl2")).matches())
            return "Enter valid FDL1 and FDL2 addresses (like 0x65000800) in the Device tab.";
        return null;
    }

    // ------------------------------------------------------------------ Operations tab

    private void renderOps() {
        if (opsBox == null) return;
        opsBox.removeAllViews();
        opsBox.addView(Ui.text(this, "Spreadtrum Flash Operations", 22, Ui.ACCENT, true), Ui.fullLp(this, 8, 0));
        opsBox.addView(Ui.text(this, "Tap an operation to run it. Plug the phone in after you tap.", 15, Ui.MUTED, false),
                Ui.fullLp(this, 4, 12));

        String problem = profileProblem();
        if (problem != null) {
            opsBox.addView(Ui.banner(this, "⚠️", problem, Ui.RED_BANNER, Ui.RED_TEXT), Ui.fullLp(this, 0, 12));
        }

        LinearLayout opts = Ui.card(this, Ui.CARD);
        opts.addView(Ui.text(this, "Hex Mode (exec_addr)", 16, Ui.TEXT, true));
        HorizontalScrollView hex = Ui.chipRow(this);
        for (int i = 0; i < HEX_LABELS.length; i++) {
            final int idx = i;
            Ui.addChip(hex, Ui.chip(this, HEX_LABELS[i], i == hexMode, v -> { hexMode = idx; savePrefs(); renderOps(); }));
        }
        opts.addView(hex, Ui.fullLp(this, 8, 0));
        View div = new View(this);
        div.setBackgroundColor(0x33FFFFFF);
        opts.addView(div, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, 1, 0, 14, 0, 14));
        opts.addView(Ui.text(this, "Reboot Mode After Execution", 16, Ui.TEXT, true));
        HorizontalScrollView rb = Ui.chipRow(this);
        for (int i = 0; i < REBOOT_LABELS.length; i++) {
            final int idx = i;
            Ui.addChip(rb, Ui.chip(this, REBOOT_LABELS[i], i == rebootMode, v -> { rebootMode = idx; savePrefs(); renderOps(); }));
        }
        opts.addView(rb, Ui.fullLp(this, 8, 0));
        opsBox.addView(opts, Ui.fullLp(this, 0, 12));

        Tile[] tiles = {
                new Tile("🚀", "FLASH", 1, "Flash Partitions", "Write every image in Input Files to its partition", this::opFlash),
                new Tile("📋", "INFO", 0, "Partition Table", "Connect and list the phone's partitions (read-only)", this::opTable),
                new Tile("📦", "BACKUP", 0, "Backup Partitions", "Dump chosen partitions to Backup Files", this::opBackup),
                new Tile("📶", "IMEI", 0, "Backup IMEI & NV", "Dump miscdata, prodnv and the modem NV partitions", this::opBackupNv),
                new Tile("🔄", "RESTORE", 0, "Restore Backup", "Write backed-up .bin files back to the phone", this::opRestore),
                new Tile("🗑️", "WIPE", 2, "Wipe Userdata", "Factory reset through recovery on the next boot", this::opWipe),
                new Tile("🅰️", "SLOT A", 0, "Slot A (SPD)", "Switch the active boot slot to A", () -> opSlot("a")),
                new Tile("🅱️", "SLOT B", 0, "Slot B (SPD)", "Switch the active boot slot to B", () -> opSlot("b")),
                new Tile("🔓", "AVB 0", 0, "Disable Verity", "verity 0: turn off dm-verity in vbmeta", this::opVerity),
                new Tile("🛡️", "AVB 3", 0, "Disable Verification", "Turn off verity and verification in vbmeta", this::opVerityAll),
                new Tile("🔒", "AVB 1", 0, "Enable Verity", "verity 1: turn dm-verity back on", this::opVerityOn),
                new Tile("🧹", "ERASE", 3, "Erase Persist", "Wipe the persist partition (backup offered)", this::opErasePersist),
                new Tile("🛠️", "RECOVERY", 0, "Reboot Recovery", "Reboot the device into recovery", () -> opReboot(1)),
                new Tile("⚡", "FASTBOOT", 0, "Reboot Fastboot", "Reboot the device into fastbootd", () -> opReboot(2)),
                new Tile("📴", "POWER", 0, "Power Off Device", "Shut the device down", () -> opReboot(3)),
                new Tile("🧩", "REPART", 3, "Repartition", "Apply a partition-list XML you choose (erases all data)", this::opRepartition),
                new Tile("💻", "CLI", 0, "Custom spd_dump", "Type your own spd_dump commands", this::opCustom),
        };
        int cols = Ui.tileColumns(this);
        for (int i = 0; i < tiles.length; i += cols) {
            LinearLayout row = Ui.hbox(this);
            row.setBaselineAligned(false);
            for (int k = 0; k < cols; k++) {
                View v = i + k < tiles.length ? tileView(tiles[i + k]) : new View(this);
                row.addView(v, tileLp(k == 0, k == cols - 1));
            }
            opsBox.addView(row, Ui.fullLp(this, 0, 8));
        }
    }

    private static final class Tile {
        final String glyph, tag, title, desc;
        final int kind;
        final Runnable action;
        Tile(String glyph, String tag, int kind, String title, String desc, Runnable action) {
            this.glyph = glyph; this.tag = tag; this.kind = kind; this.title = title;
            this.desc = desc; this.action = action;
        }
    }

    private LinearLayout.LayoutParams tileLp(boolean first, boolean last) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.leftMargin = dp(first ? 0 : 5);
        p.rightMargin = dp(last ? 0 : 5);
        return p;
    }

    private View tileView(final Tile t) {
        LinearLayout l = Ui.vbox(this);
        l.setBackground(Ui.ripple(Ui.panel(this, 0xFF343A49, 0xFF23262E, 26)));
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setClickable(true);
        l.setOnClickListener(v -> { if (ready()) t.action.run(); });
        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.iconBox(this, t.glyph, Ui.ICON_BG, Ui.ACCENT, 46));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        int fg = t.kind == 1 ? Ui.GREEN : t.kind == 2 ? Ui.AMBER : t.kind == 3 ? Ui.RED_TEXT : Ui.ACCENT;
        int bg = t.kind == 1 ? Ui.GREEN_BG : t.kind == 2 ? Ui.AMBER_BG : t.kind == 3 ? Ui.RED_BG : Ui.ICON_BG;
        top.addView(Ui.badge(this, t.tag, fg, bg));
        l.addView(top);
        l.addView(Ui.ellipsized(Ui.text(this, t.title, 17, Ui.TEXT, true), 1), Ui.fullLp(this, 12, 0));
        l.addView(Ui.ellipsized(Ui.mono(this, t.desc, 12, Ui.MUTED), 3), Ui.fullLp(this, 4, 0));
        return l;
    }

    /** Common gate for every operation tile. */
    private boolean ready() {
        if (busy) { toast("A session is already running - see the Console tab"); return false; }
        String p = profileProblem();
        if (p != null) { toast(p); return false; }
        for (String k : new String[]{"fdl1", "fdl2"})
            if (!ADDR.matcher(addr(k)).matches()) { toast("Invalid " + k + " address"); return false; }
        return true;
    }

    private void opTable() {
        confirm("Read partition table?", "Connects to the phone and lists its partitions. Nothing is written.", "Run",
                () -> startSession(list("p"), rebootMode));
    }

    private void opFlash() {
        File[] files = inputDir.listFiles();
        if (files == null || files.length == 0) { toast("Add images in Files > Input Files first"); return; }
        Arrays.sort(files);
        final List<String> actions = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean risky = false;
        for (File f : files) {
            String part = partFromName(f.getName());
            if (part == null) { sb.append("skip  ").append(f.getName()).append(" (name is not a partition)\n"); continue; }
            sb.append(f.getName()).append("  ->  ").append(part).append("  (")
                    .append(sizeText(f.length())).append(")\n");
            actions.add("w"); actions.add(part); actions.add(f.getAbsolutePath());
            if (RISKY.contains(part.toLowerCase(Locale.US))) risky = true;
        }
        if (actions.isEmpty()) { toast("No file names match partition names (boot.img -> boot)"); return; }
        String msg = sb + "\nThe old contents of these partitions are overwritten.";
        final int exit = rebootMode;
        if (risky) {
            confirmTyped("Flash critical partition", msg + "\nAt least one target is critical or holds unique device data. A bad write can brick the phone.",
                    "FLASH", () -> startSession(actions, exit));
        } else {
            confirm("Flash partitions?", msg, "Flash", () -> startSession(actions, exit));
        }
    }

    private void opBackup() {
        final EditText names = dialogField("boot vbmeta vendor_boot", "boot");
        final CheckBox all = new CheckBox(this);
        all.setText("Everything except userdata/cache (all_lite, large)");
        all.setTextColor(Ui.TEXT);
        LinearLayout box = dialogBox(names, all);
        new AlertDialog.Builder(this).setTitle("Backup partitions")
                .setMessage("Partition names, separated by spaces. Files are saved as <name>.bin in Backup Files.")
                .setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Back up", (d, w) -> {
                    List<String> a = new ArrayList<>();
                    a.add("path"); a.add(backupDir.getAbsolutePath());
                    if (all.isChecked()) { a.add("r"); a.add("all_lite"); }
                    else {
                        List<String> toks = tokenize(names.getText().toString());
                        if (toks.isEmpty()) { toast("Enter at least one partition"); return; }
                        for (String t : toks) {
                            if (!PART.matcher(t).matches()) { toast("Bad partition name: " + t); return; }
                            a.add("r"); a.add(t);
                        }
                    }
                    a.add("path"); a.add(workDir.getAbsolutePath());
                    startSession(a, rebootMode);
                }).show();
    }

    private void opBackupNv() {
        confirm("Backup IMEI & NV?", "Dumps " + join(Arrays.asList(NV_PARTS)) + " to Backup Files. Do this before any flashing or erasing.",
                "Back up", () -> {
                    List<String> a = new ArrayList<>();
                    a.add("path"); a.add(backupDir.getAbsolutePath());
                    for (String p : NV_PARTS) { a.add("r"); a.add(p); }
                    a.add("path"); a.add(workDir.getAbsolutePath());
                    startSession(a, rebootMode);
                });
    }

    private void opRestore() {
        final File[] files = listFiles(backupDir);
        if (files.length == 0) { toast("No backups yet - run a backup first"); return; }
        final String[] names = new String[files.length];
        final boolean[] on = new boolean[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Restore which backups?")
                .setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (d, w) -> {
                    final List<String> a = new ArrayList<>();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < files.length; i++) {
                        if (!on[i]) continue;
                        String part = partFromName(files[i].getName());
                        if (part == null) continue;
                        a.add("w"); a.add(part); a.add(files[i].getAbsolutePath());
                        sb.append(files[i].getName()).append("  ->  ").append(part).append('\n');
                    }
                    if (a.isEmpty()) { toast("Nothing selected"); return; }
                    final int exit = rebootMode;
                    confirmTyped("Restore backups", sb + "\nThese partitions are overwritten with the backup contents.",
                            "RESTORE", () -> startSession(a, exit));
                }).show();
    }

    private void opWipe() {
        confirmTyped("Wipe userdata",
                "Writes a recovery command that erases ALL user data (apps, photos, accounts) on the next boot. "
                        + "The phone reboots to system so recovery can run it; the reboot mode setting is ignored for this action.",
                "WIPE", () -> {
                    File bcb = prepareWipeBcb();
                    if (bcb == null) return;
                    startSession(list("wof", "misc", "0", bcb.getAbsolutePath()), 0);
                });
    }

    /** Copies the bundled 2048-byte BCB to storage after checking it is the exact file from the source repo. */
    private File prepareWipeBcb() {
        File out = new File(workDir, "misc-wipe.bin");
        try {
            assetCopy("misc-wipe.bin", out);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(out)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte x : md.digest()) hex.append(String.format(Locale.US, "%02x", x));
            if (!hex.toString().equals(MISC_WIPE_SHA256) || out.length() != 2048) {
                toast("Wipe image failed its integrity check - not running");
                return null;
            }
            return out;
        } catch (Exception e) {
            toast("Could not prepare the wipe image: " + e.getMessage());
            return null;
        }
    }

    private void opSlot(final String slot) {
        confirm("Switch to slot " + slot.toUpperCase(Locale.US) + "?", "Runs: set_active " + slot
                + "\nOnly valid on A/B (VAB) devices.", "Switch", () -> startSession(list("set_active", slot), rebootMode));
    }

    private void opVerity() {
        confirm("Disable verity?", "Runs: verity 0\nPatches the vbmeta flags so dm-verity is off. Needs an FDL2 that allows writing.",
                "Disable", () -> startSession(list("verity", "0"), rebootMode));
    }

    private void opVerityAll() {
        confirm("Disable verity + verification?", "Runs: wov vbmeta 0x78 0x03000000\nSets the vbmeta flags to 3 (hashtree and verification disabled).",
                "Disable", () -> startSession(list("wov", "vbmeta", "0x78", "0x03000000"), rebootMode));
    }

    private void opVerityOn() {
        confirm("Enable verity?", "Runs: verity 1", "Enable", () -> startSession(list("verity", "1"), rebootMode));
    }

    private void opErasePersist() {
        final CheckBox bk = new CheckBox(this);
        bk.setText("Back up persist first (recommended)");
        bk.setTextColor(Ui.TEXT);
        bk.setChecked(true);
        final EditText typed = dialogField("Type ERASE to confirm", "");
        typed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout box = dialogBox(bk, typed);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Erase persist partition")
                .setMessage("persist usually holds sensor, camera, fingerprint and Wi-Fi/Bluetooth calibration and MAC data that cannot be "
                        + "regenerated. Features can stop working afterwards.")
                .setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Erase", (d, w) -> {
                    List<String> a = new ArrayList<>();
                    if (bk.isChecked()) {
                        a.add("path"); a.add(backupDir.getAbsolutePath());
                        a.add("r"); a.add("persist");
                        a.add("path"); a.add(workDir.getAbsolutePath());
                    }
                    a.add("e"); a.add("persist");
                    startSession(a, rebootMode);
                }).create();
        dlg.show();
        gateOnWord(dlg, typed, "ERASE");
    }

    private void opReboot(final int mode) {
        confirm("Reboot: " + REBOOT_LABELS[mode == 3 ? 3 : mode] + "?", "Runs: " + REBOOT_CMDS[mode], "Run",
                () -> startSession(new ArrayList<String>(), mode));
    }

    // ------------------------------------------------------------------ repartition (XML)

    private static final Pattern XML_PART = Pattern.compile(
            "^<Partition id=\"([^\"]{1,35})\" size=\"(0[xX][0-9a-fA-F]+|[0-9]+)\"/>$");

    private void opRepartition() {
        confirm("Repartition", "Pick the partition-list XML to apply (the same format spd_dump writes to partition.xml: "
                        + "<Partitions> with <Partition id=\"name\" size=\"MB\"/> lines).\n\n"
                        + "Repartitioning rewrites the table and ERASES the data on the phone. Back up IMEI & NV and persist first.",
                "Choose XML", () -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_XML);
                });
    }

    /**
     * Mirrors the strict parser in spd_dump so a bad file is rejected here instead of aborting mid-session.
     * @return a human-readable partition summary, or null with a toast shown
     */
    private String checkPartitionXml(File f) {
        String text;
        try {
            byte[] data = new byte[(int) f.length()];
            try (InputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            }
            text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            toast("Could not read the XML: " + e.getMessage());
            return null;
        }
        if (text.indexOf('\0') >= 0) { toast("XML contains a zero byte"); return null; }
        text = text.replaceAll("(?s)<!--.*?-->", "");
        int a = text.indexOf("<Partitions>"), b = text.indexOf("</Partitions>");
        if (a < 0 || b < a || text.indexOf("<Partitions>", a + 1) >= 0) {
            toast("XML needs exactly one <Partitions> ... </Partitions> block");
            return null;
        }
        String body = text.substring(a + 12, b);
        java.util.regex.Matcher tags = Pattern.compile("<[^>]*>").matcher(body);
        StringBuilder sb = new StringBuilder();
        int count = 0, last = 0;
        long totalMb = 0;
        boolean unlimited = false;
        while (tags.find()) {
            if (!body.substring(last, tags.start()).trim().isEmpty()) { toast("XML has stray text between partitions"); return null; }
            last = tags.end();
            java.util.regex.Matcher m = XML_PART.matcher(tags.group());
            if (!m.matches()) {
                toast("Line not accepted by spd_dump (use exactly <Partition id=\"x\" size=\"N\"/>): " + tags.group());
                return null;
            }
            long size;
            try {
                String sz = m.group(2);
                size = sz.toLowerCase(Locale.US).startsWith("0x") ? Long.parseLong(sz.substring(2), 16) : Long.parseLong(sz);
            } catch (NumberFormatException e) { toast("Bad size in " + tags.group()); return null; }
            count++;
            if (count > 128) { toast("More than 128 partitions"); return null; }
            if (size == 0xffffffffL) { unlimited = true; sb.append(m.group(1)).append("  (rest of storage)\n"); }
            else { totalMb += size; sb.append(m.group(1)).append("  ").append(size).append(" MB\n"); }
        }
        if (!body.substring(last).trim().isEmpty()) { toast("XML has stray text after the last partition"); return null; }
        if (count == 0) { toast("No <Partition> entries found"); return null; }
        return count + " partitions, " + totalMb + " MB" + (unlimited ? " + remainder" : "") + "\n\n" + sb;
    }

    private void importPartitionXml(final Uri uri) {
        new Thread(() -> {
            final File dest = new File(workDir, "repartition.xml");
            try {
                long size = querySize(uri);
                if (size > 1 << 20) { main.post(() -> toast("That file is too big to be a partition list")); return; }
                copy(uri, dest, size);
            } catch (IOException e) {
                final String m = "Import failed: " + e.getMessage();
                main.post(() -> toast(m));
                return;
            }
            main.post(() -> {
                String summary = checkPartitionXml(dest);
                if (summary == null) return;
                final String shown = summary.length() > 1800 ? summary.substring(0, 1800) + "..." : summary;
                confirmTyped("Repartition the phone", shown
                                + "\nThis rewrites the partition table and ERASES the data on the phone. A table that does not fit "
                                + "this phone's storage, or an interrupted run, can brick it.",
                        "REPARTITION", () -> startSession(list("repartition", dest.getAbsolutePath()), rebootMode));
            });
        }, "import-xml").start();
    }

    private void opCustom() {
        final EditText args = dialogField("e.g. r boot", "");
        final CheckBox ex = new CheckBox(this);
        ex.setText("Add the reboot command when finished");
        ex.setTextColor(Ui.TEXT);
        ex.setChecked(true);
        new AlertDialog.Builder(this).setTitle("Custom spd_dump commands")
                .setMessage("Runs after the loaders are sent. Quote arguments that contain spaces.")
                .setView(dialogBox(args, ex)).setNegativeButton("Cancel", null)
                .setPositiveButton("Run", (d, w) -> {
                    List<String> toks = tokenize(args.getText().toString());
                    if (toks.isEmpty()) { toast("Nothing to run"); return; }
                    startSession(toks, ex.isChecked() ? rebootMode : -1);
                }).show();
    }

    // ------------------------------------------------------------------ running a session

    /** @param exitMode 0..3 index into REBOOT_CMDS, or -1 to leave the session open for typed commands */
    private void startSession(List<String> actions, int exitMode) {
        if (busy) { toast("A session is already running"); return; }
        String problem = profileProblem();
        if (problem != null) { toast(problem); return; }
        List<String> args = new ArrayList<>();
        args.add("--verbose"); args.add("1");
        String exec = hexMode == 0 ? addr("exec") : hexMode == 1 ? addr("alt") : "";
        if (!exec.isEmpty()) {
            if (!ADDR.matcher(exec).matches()) { toast("Invalid exec_addr in the Device tab"); return; }
            args.add("exec_addr"); args.add(exec);
        }
        args.add("fdl"); args.add(fdlFile(1).getAbsolutePath()); args.add(addr("fdl1"));
        args.add("fdl"); args.add(fdlFile(2).getAbsolutePath()); args.add(addr("fdl2"));
        args.add("exec");
        args.add("path"); args.add(workDir.getAbsolutePath());
        args.addAll(actions);
        if (exitMode >= 0) args.add(REBOOT_CMDS[exitMode]);

        int wait = Math.max(5, Math.min(600, prefs.getInt("wait", 60)));
        runner.setRoot(rootMode);
        runner.setSharedDir(backupDir);
        setBusy(true);
        selectTab(TAB_CONSOLE);
        final List<String> finalArgs = args;
        final int finalWait = wait;
        new Thread(() -> {
            runner.run(workDir, finalArgs, finalWait);
            main.post(() -> {
                setBusy(false);
                renderFiles();
            });
        }, "spd-session").start();
    }

    private void setBusy(boolean b) {
        busy = b;
        if (b && prefs.getBoolean("keepOn", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!b && statusText.startsWith("Waiting")) status("");
        updateStatusLine();
    }

    // ------------------------------------------------------------------ Files tab

    private File[] listFiles(File dir) {
        File[] f = dir.listFiles();
        if (f == null) return new File[0];
        List<File> out = new ArrayList<>();
        for (File x : f) if (x.isFile()) out.add(x);
        File[] arr = out.toArray(new File[0]);
        Arrays.sort(arr, Comparator.comparing(File::getName));
        return arr;
    }

    private void renderFiles() {
        if (filesBox == null) return;
        filesBox.removeAllViews();
        final File[] in = listFiles(inputDir), bk = listFiles(backupDir);
        final List<String[]> fdls = listProfiles();

        HorizontalScrollView chips = Ui.chipRow(this);
        Ui.addChip(chips, Ui.chip(this, "📥  Input Files (" + in.length + ")", filesSub == SUB_INPUT, v -> { filesSub = SUB_INPUT; renderFiles(); }));
        Ui.addChip(chips, Ui.chip(this, "📦  Backup Files (" + bk.length + ")", filesSub == SUB_BACKUP, v -> { filesSub = SUB_BACKUP; renderFiles(); }));
        Ui.addChip(chips, Ui.chip(this, "💾  FDLs (" + fdls.size() + ")", filesSub == SUB_FDL, v -> { filesSub = SUB_FDL; renderFiles(); }));
        filesBox.addView(chips, Ui.fullLp(this, 8, 12));

        if (filesSub == SUB_INPUT) {
            filesBox.addView(Ui.banner(this, "ℹ️", "Input folder: " + inputDir.getAbsolutePath() + "\nAdd .img / .bin files here (or copy them in with a file manager). Each file is flashed to the partition named like the file (boot.img -> boot).",
                    Ui.CARD_DARK, Ui.MUTED), Ui.fullLp(this, 0, 12));
            if (in.length == 0) {
                emptyState("📥", "No Input Images Found", "Tap the button to add boot.img, vbmeta.img, super.img or similar.");
            } else {
                for (final File f : in) filesBox.addView(fileRow(f, false, null), Ui.fullLp(this, 0, 8));
            }
            filesBox.addView(Ui.primaryButton(this, "+  Choose Image Files", v -> pickImages()), Ui.fullLp(this, 8, 0));
        } else if (filesSub == SUB_BACKUP) {
            filesBox.addView(Ui.banner(this, usingSharedBackup() ? "✅" : "⚠️", "Backup: partitions and NV dumps are stored in:\n"
                    + backupDir.getAbsolutePath() + (usingSharedBackup() ? ""
                    : "\n\nThis is the app's private folder. Allow 'All files access' to use "
                    + sharedBackupDir().getAbsolutePath() + " instead."),
                    usingSharedBackup() ? Ui.CARD_DARK : Ui.AMBER_BG, usingSharedBackup() ? Ui.MUTED : Ui.AMBER), Ui.fullLp(this, 0, 12));
            if (!usingSharedBackup())
                filesBox.addView(Ui.primaryButton(this, "Allow access to SPD-FLASHER folder", v -> requestStorageAccess()), Ui.fullLp(this, 0, 12));
            if (bk.length == 0) {
                emptyState("📁", "No Partition Backups Yet", "Run 'Backup Partitions' or 'Backup IMEI & NV' in the Operations tab to create dumps here.");
            } else {
                for (final File f : bk) filesBox.addView(fileRow(f, false, null), Ui.fullLp(this, 0, 8));
                filesBox.addView(Ui.outlineButton(this, "Export backups to a folder", v -> exportBackups()), Ui.fullLp(this, 8, 0));
            }
        } else {
            filesBox.addView(Ui.banner(this, "ℹ️", "Loaders live in " + fdlRoot.getAbsolutePath() + "/<chipset>/<brand>/ (fdl1.bin + fdl2.bin). Drop files there with any file manager, then tap a folder to make it the active profile.",
                    Ui.CARD_DARK, Ui.MUTED), Ui.fullLp(this, 0, 12));
            LinearLayout btns = Ui.hbox(this);
            TextView b1 = Ui.outlineButton(this, "Import FDL1", v -> pickFdl(REQ_FDL1));
            TextView b2 = Ui.primaryButton(this, "Import FDL2", v -> pickFdl(REQ_FDL2));
            btns.addView(b1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btns.addView(b2, Ui.lp(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 0, 0));
            ((LinearLayout.LayoutParams) b2.getLayoutParams()).weight = 1f;
            filesBox.addView(btns, Ui.fullLp(this, 0, 4));
            filesBox.addView(Ui.text(this, hasProfile() ? "Imports go to: " + chipLabel() + " / " + brandLabel()
                    : "Pick a chipset and brand in the Device tab to enable imports.", 13, Ui.MUTED, false), Ui.fullLp(this, 4, 12));
            if (fdls.isEmpty()) {
                emptyState("💾", "No Loaders Yet", "Select UMS9230 / Infinix for the built-in example, or import your own FDL1 and FDL2 files.");
            }
            for (final String[] p : fdls) {
                final File dir = profileDir(p[0], p[1]);
                String[] names = dir.list();
                if (names != null) Arrays.sort(names);
                String sub = names == null ? "" : join(Arrays.asList(names));
                filesBox.addView(dirRow(p[0] + " / " + p[1], sub, p[0].equals(chipId) && p[1].equals(brandId),
                        () -> selectProfile(p[0], p[1]), () -> confirm("Delete loaders?", "Remove " + p[0] + " / " + p[1] + "?", "Delete", () -> {
                            deleteRecursive(dir);
                            if (p[0].equals(chipId) && p[1].equals(brandId)) { brandId = null; savePrefs(); }
                            renderAll();
                        })), Ui.fullLp(this, 0, 8));
            }
        }
    }

    private void emptyState(String glyph, String title, String body) {
        LinearLayout c = Ui.card(this, Ui.CARD_DARK);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(20), dp(32), dp(20), dp(32));
        TextView icon = Ui.iconBox(this, glyph, Ui.CARD, Ui.MUTED, 72);
        c.addView(icon);
        TextView t = Ui.text(this, title, 22, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, Ui.fullLp(this, 16, 0));
        TextView b = Ui.text(this, body, 15, Ui.MUTED, false);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(0, 1.2f);
        c.addView(b, Ui.fullLp(this, 8, 0));
        filesBox.addView(c, Ui.fullLp(this, 0, 8));
    }

    private View fileRow(final File f, boolean dir, Runnable tap) {
        LinearLayout row = Ui.hbox(this);
        row.setBackground(Ui.round(this, Ui.CARD_DARK, 20));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.addView(Ui.iconBox(this, "📄", 0xFFFDE8B8, 0xFFB86E00, 44), Ui.wrapLp(this, 0, 0, 14, 0));
        LinearLayout mid = Ui.vbox(this);
        mid.addView(Ui.ellipsized(Ui.text(this, f.getName(), 17, Ui.TEXT, true), 1));
        mid.addView(Ui.text(this, new SimpleDateFormat("M/d/yyyy h:mm a", Locale.US).format(new Date(f.lastModified()))
                + "  ·  " + sizeText(f.length()), 13, Ui.MUTED, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView del = Ui.text(this, "🗑", 20, Ui.RED_TEXT, false);
        del.setPadding(dp(10), dp(6), dp(4), dp(6));
        del.setClickable(true);
        del.setOnClickListener(v -> confirm("Delete file?", f.getName(), "Delete", () -> { f.delete(); renderFiles(); }));
        row.addView(del);
        return row;
    }

    private View dirRow(String title, String sub, boolean active, final Runnable tap, final Runnable delete) {
        LinearLayout row = Ui.hbox(this);
        row.setBackground(Ui.ripple(active ? Ui.outline(this, Ui.CARD_DARK, Ui.ACCENT, 20) : Ui.round(this, Ui.CARD_DARK, 20)));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setClickable(true);
        row.setOnClickListener(v -> tap.run());
        row.addView(Ui.iconBox(this, "📂", 0xFFFDE8B8, 0xFFB86E00, 44), Ui.wrapLp(this, 0, 0, 14, 0));
        LinearLayout mid = Ui.vbox(this);
        mid.addView(Ui.ellipsized(Ui.text(this, title, 17, Ui.TEXT, true), 1));
        mid.addView(Ui.ellipsized(Ui.text(this, (active ? "Active  ·  " : "") + sub, 13, Ui.MUTED, false), 1));
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView del = Ui.text(this, "🗑", 20, Ui.RED_TEXT, false);
        del.setPadding(dp(10), dp(6), dp(4), dp(6));
        del.setClickable(true);
        del.setOnClickListener(v -> delete.run());
        row.addView(del);
        return row;
    }

    private void pickImages() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_IMAGES);
    }

    private void pickFdl(int req) {
        if (!hasProfile()) { toast("Pick a chipset and brand in the Device tab first"); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, req);
    }

    private void exportBackups() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_STORAGE) { refreshBackupDir(); return; }
        if (result != RESULT_OK || data == null) return;
        if (req == REQ_IMAGES) {
            final List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            importImages(uris);
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            if (req == REQ_FDL1) importFdl(uri, 1);
            else if (req == REQ_FDL2) importFdl(uri, 2);
            else if (req == REQ_EXPORT) doExport(uri);
            else if (req == REQ_XML) importPartitionXml(uri);
        }
    }

    private void importImages(final List<Uri> uris) {
        new Thread(() -> {
            for (Uri u : uris) {
                String name = displayName(u).replaceAll("[^A-Za-z0-9._\\-]", "_");
                long size = querySize(u);
                if (size > 0 && size > inputDir.getUsableSpace()) {
                    final String m = "Not enough free space for " + name;
                    main.post(() -> toast(m));
                    continue;
                }
                try {
                    copy(u, new File(inputDir, name), size);
                    log("Imported " + name + "\n");
                } catch (IOException e) {
                    final String m = "Import failed: " + e.getMessage();
                    main.post(() -> toast(m));
                }
            }
            main.post(this::renderAll);
        }, "import-img").start();
    }

    private void importFdl(final Uri uri, final int n) {
        final File dest = fdlFile(n);
        new Thread(() -> {
            try {
                dest.getParentFile().mkdirs();
                copy(uri, dest, -1);
                log("Imported FDL" + n + " for " + chipLabel() + " / " + brandLabel() + "\n");
                main.post(this::renderAll);
            } catch (IOException e) {
                final String m = "Import failed: " + e.getMessage();
                main.post(() -> toast(m));
            }
        }, "import-fdl").start();
    }

    private void doExport(final Uri tree) {
        new Thread(() -> {
            int n = 0;
            try {
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                for (File f : listFiles(backupDir)) {
                    Uri out = DocumentsContract.createDocument(getContentResolver(), docUri, "application/octet-stream", f.getName());
                    if (out == null) continue;
                    try (InputStream in = new FileInputStream(f); OutputStream os = getContentResolver().openOutputStream(out)) {
                        byte[] buf = new byte[1 << 16];
                        int r;
                        while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                    }
                    n++;
                }
                log("Exported " + n + " backup file(s).\n");
            } catch (Exception e) {
                final String m = "Export failed: " + e.getMessage();
                main.post(() -> toast(m));
            }
        }, "export").start();
    }

    // ------------------------------------------------------------------ Console tab

    private View buildConsole() {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout bar = Ui.hbox(this);
        bar.addView(Ui.text(this, "Console", 22, Ui.ACCENT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView stop = Ui.text(this, "Stop", 14, 0xFFFFFFFF, true);
        stop.setBackground(Ui.ripple(Ui.round(this, Ui.RED, 14)));
        stop.setPadding(dp(16), dp(8), dp(16), dp(8));
        stop.setClickable(true);
        stop.setOnClickListener(v -> { runner.cancel(); append("\n[stop requested]\n"); });
        bar.addView(stop, Ui.wrapLp(this, 0, 0, 8, 0));
        TextView copy = Ui.text(this, "Copy", 14, Ui.TEXT, true);
        copy.setBackground(Ui.ripple(Ui.round(this, Ui.CHIP_SEL, 14)));
        copy.setPadding(dp(16), dp(8), dp(16), dp(8));
        copy.setClickable(true);
        copy.setOnClickListener(v -> copyLog());
        bar.addView(copy, Ui.wrapLp(this, 0, 0, 8, 0));
        TextView clear = Ui.text(this, "Clear", 14, Ui.TEXT, true);
        clear.setBackground(Ui.ripple(Ui.round(this, Ui.CHIP_SEL, 14)));
        clear.setPadding(dp(16), dp(8), dp(16), dp(8));
        clear.setClickable(true);
        clear.setOnClickListener(v -> { logBuf.setLength(0); logView.setText(""); });
        bar.addView(clear);
        box.addView(bar, Ui.fullLp(this, 0, 8));

        logScroll = new ScrollView(this);
        logScroll.setBackground(Ui.round(this, 0xFF0F1115, 20));
        logView = Ui.mono(this, "", 11.5f, 0xFFC9D1D9);
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        box.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout in = Ui.hbox(this);
        stdinInput = new EditText(this);
        stdinInput.setHint("send a line to spd_dump (while running)");
        stdinInput.setHintTextColor(Ui.MUTED);
        stdinInput.setTextColor(Ui.TEXT);
        stdinInput.setSingleLine(true);
        stdinInput.setTextSize(14);
        stdinInput.setBackground(Ui.round(this, Ui.CARD_DARK, 16));
        stdinInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        in.addView(stdinInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = Ui.text(this, "Send", 15, Ui.ON_ACCENT, true);
        send.setBackground(Ui.ripple(Ui.round(this, Ui.ACCENT, 16)));
        send.setPadding(dp(18), dp(12), dp(18), dp(12));
        send.setClickable(true);
        send.setOnClickListener(v -> {
            final String line = stdinInput.getText().toString();
            stdinInput.setText("");
            append("> " + line + "\n");
            new Thread(() -> runner.sendLine(line), "spd-stdin").start();
        });
        in.addView(send, Ui.wrapLp(this, 8, 0, 0, 0));
        box.addView(in, Ui.fullLp(this, 8, 0));
        return box;
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("spd log", logBuf.toString()));
        toast("Log copied");
    }

    // ------------------------------------------------------------------ dialogs

    private void showSettings() {
        final EditText wait = dialogField("Seconds to wait for the device", String.valueOf(prefs.getInt("wait", 60)));
        wait.setInputType(InputType.TYPE_CLASS_NUMBER);
        final CheckBox keep = new CheckBox(this);
        keep.setText("Keep the screen on while a session runs");
        keep.setTextColor(Ui.TEXT);
        keep.setChecked(prefs.getBoolean("keepOn", true));
        new AlertDialog.Builder(this).setTitle("Settings").setView(dialogBox(wait, keep))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    int s = 60;
                    try { s = Integer.parseInt(wait.getText().toString().trim()); } catch (NumberFormatException ignored) { }
                    prefs.edit().putInt("wait", Math.max(5, Math.min(600, s))).putBoolean("keepOn", keep.isChecked()).apply();
                }).show();
    }

    private void showAbout() {
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(new Logo());
        LinearLayout lbox = dialogBox(logo);
        logo.setLayoutParams(Ui.lp(this, dp(72), dp(72), 0, 4, 0, 4));
        new AlertDialog.Builder(this).setTitle("SPD Flasher").setView(lbox)
                .setMessage("Front end for TomKing's spd_dump (bundled with libusb) for Unisoc/Spreadtrum phones.\n\n"
                        + "A wrong loader or partition can permanently brick a phone. Back up persist and the NV partitions "
                        + "first, and keep this screen open while a session runs.\n\n"
                        + "Backups: " + backupDir.getAbsolutePath())
                .setPositiveButton("OK", null).show();
    }

    private void confirm(String title, String msg, String yes, final Runnable ok) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Cancel", null)
                .setPositiveButton(yes, (d, w) -> ok.run()).show();
    }

    private void confirmTyped(String title, String msg, final String word, final Runnable ok) {
        final EditText input = dialogField("Type " + word + " to confirm", "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setView(dialogBox(input))
                .setNegativeButton("Cancel", null).setPositiveButton("Proceed", (d, w) -> ok.run()).create();
        dlg.show();
        gateOnWord(dlg, input, word);
    }

    private void gateOnWord(AlertDialog dlg, EditText input, final String word) {
        final android.widget.Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        pos.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { pos.setEnabled(s.toString().trim().equalsIgnoreCase(word)); }
        });
    }

    private EditText dialogField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private LinearLayout dialogBox(View... views) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        for (View v : views) box.addView(v, Ui.fullLp(this, 4, 4));
        return box;
    }

    // ------------------------------------------------------------------ SpdRunner.Listener / log

    @Override
    public void log(final String s) {
        main.post(() -> append(s));
    }

    @Override
    public void status(final String s) {
        main.post(() -> { statusText = s; updateStatusLine(); });
    }

    private void updateStatusLine() {
        if (statusLine == null) return;
        boolean show = busy || !statusText.isEmpty();
        statusLine.setVisibility(show ? View.VISIBLE : View.GONE);
        statusLine.setText(busy && statusText.isEmpty() ? "Session running..." : statusText);
    }

    /** UI thread only. Handles '\r' progress lines by overwriting the current line. */
    private void append(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') { pendingCr = true; continue; }
            if (pendingCr) {
                pendingCr = false;
                if (c != '\n') logBuf.setLength(logBuf.lastIndexOf("\n") + 1);
            }
            logBuf.append(c);
        }
        if (logBuf.length() > 200_000) {
            int cut = logBuf.indexOf("\n", 60_000);
            logBuf.delete(0, cut < 0 ? 60_000 : cut + 1);
        }
        if (!flushScheduled) {
            flushScheduled = true;
            main.postDelayed(() -> {
                flushScheduled = false;
                logView.setText(logBuf);
                logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            }, 120);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static List<String> list(String... a) {
        return new ArrayList<>(Arrays.asList(a));
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }

    /** "boot.img" -> "boot"; null if the result is not a valid partition name. */
    private static String partFromName(String file) {
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        return PART.matcher(base).matches() ? base : null;
    }

    private static String sizeText(long b) {
        if (b >= 1L << 30) return String.format(Locale.US, "%.2f GB", b / (double) (1L << 30));
        if (b >= 1L << 20) return String.format(Locale.US, "%.1f MB", b / (double) (1L << 20));
        if (b >= 1L << 10) return String.format(Locale.US, "%.0f KB", b / (double) (1L << 10));
        return b + " B";
    }

    /** Splits on spaces, keeping "double quoted" or 'single quoted' parts together. */
    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean has = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0; else cur.append(ch);
            } else if (ch == '"' || ch == '\'') {
                quote = ch; has = true;
            } else if (Character.isWhitespace(ch)) {
                if (cur.length() > 0 || has) { out.add(cur.toString()); cur.setLength(0); has = false; }
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0 || has) out.add(cur.toString());
        return out;
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    private void assetCopy(String asset, File dest) throws IOException {
        try (InputStream in = getAssets().open(asset); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    private void copy(Uri uri, File dest, long expected) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("cannot open file");
            byte[] buf = new byte[1 << 17];
            long total = 0, next = 256L << 20;
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                total += r;
                if (expected > 0 && total >= next) {
                    log("Copied " + (total >> 20) + " / " + (expected >> 20) + " MB\n");
                    next += 256L << 20;
                }
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) { }
        String s = uri.getLastPathSegment();
        return s != null ? s : "file.bin";
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) { }
        return -1;
    }
}
