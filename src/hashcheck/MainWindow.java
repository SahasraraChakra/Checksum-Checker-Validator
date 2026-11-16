package hashcheck;

import hashcheck.Aero.T;
import hashcheck.AeroFrame.AeroDialog;
import hashcheck.HashEngine.*;
import hashcheck.Menus.*;
import hashcheck.ResultsTab.V;
import hashcheck.Widgets.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import static hashcheck.Aero.c;

/** Application controller: owns state, builds the shared chrome and drives hashing. */
final class MainWindow {
    static final String VERSION = "v2.4";
    final AeroFrame frame;          // null only in the offscreen test harness
    final JPanel clientPanel;
    final JRootPane root;
    String titleText = "";
    final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    // views
    SetupTab setup;
    ResultsTab results;
    Shell.TabStrip tabs;
    Shell.Toolbar toolbar;
    Shell.StatusBar status;
    Menus.AMenuBar menuBar;
    JPanel cards;
    CardLayout cl = new CardLayout();
    int tabIdx = 0;

    // settings
    Algo algo = Algo.SHA256;
    boolean multi, autoSwitch = true, upper, asyncIo = true;
    int chunk = 1 << 20;
    final List<String> recent = new ArrayList<>();

    // state
    Path file;
    boolean fileOk;
    long fileSize;
    Job job;
    boolean running;
    Result last;
    String verifiedExpected = "";
    Algo primary = Algo.SHA256;
    V lastVerdict = V.NONE;
    String lastStamp = "";
    final StringBuilder log = new StringBuilder();
    boolean syncing;
    int ticks;

    // menu items that change state
    AItem miVerify, miCompare, miPause, miCancel, miSaveReport, miSfv, miSaveDigest;
    ACheckItem miMulti, miAuto, miUpper, miToolbar, miStatus, miTop;
    final ARadioItem[] miAlgo = new ARadioItem[4];
    AMenu miRecent;

    MainWindow(String[] args) {
        loadPrefs();
        Engine.init();
        if (!GraphicsEnvironment.isHeadless()) {
            frame = new AeroFrame(titleFor(null));
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { exit(); }
            });
            clientPanel = frame.client();
            root = frame.getRootPane();
        } else {
            frame = null;
            clientPanel = new JPanel();
            root = new JRootPane();
        }
        build();
        applySettingsToUi();
        resetResults();
        refreshStatusPanes();
        status.set("check", c(0x008000), "Ready: select a target file to begin.");
        new javax.swing.Timer(50, e -> tick()).start();
        if (args.length > 0) setFile(args[0]);
        if (args.length > 1) setup.expected.setText(HashEngine.extractHash(args[1], fileName()));
    }

    // ================================================================ construction
    private void build() {
        JPanel client = clientPanel;
        client.setLayout(new BorderLayout());
        menuBar = new Menus.AMenuBar();
        toolbar = new Shell.Toolbar(Engine.indicator + " Active");
        tabs = new Shell.TabStrip();
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        menuBar.setAlignmentX(0);
        toolbar.setAlignmentX(0);
        tabs.setAlignmentX(0);
        top.add(menuBar);
        top.add(toolbar);
        top.add(tabs);
        client.add(top, BorderLayout.NORTH);

        setup = new SetupTab(this);
        results = new ResultsTab(this);
        cards = new JPanel(cl);
        cards.setOpaque(true);
        cards.setBackground(Color.WHITE);
        cards.add(setup, "t0");
        cards.add(results, "t1");
        client.add(cards, BorderLayout.CENTER);
        status = new Shell.StatusBar();
        client.add(status, BorderLayout.SOUTH);

        buildMenus();
        wireToolbar();
        tabs.listeners.add(this::showTab);
        setup.expected.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { trimExpected(); }
        });

        TransferHandler th = new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }

            @Override public boolean importData(TransferSupport s) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> fl = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!fl.isEmpty() && !running) { setFile(fl.get(0).getAbsolutePath()); showTab(0); return true; }
                } catch (Exception ignored) { }
                return false;
            }
        };
        client.setTransferHandler(th);
        setup.pathField.setTransferHandler(th);
        cards.setTransferHandler(th);
        setup.setTransferHandler(th);

        // global shortcuts
        bind("ctrl O", this::openFile);
        bind("ctrl F", this::openFolder);
        bind("ctrl L", this::loadChecksumFile);
        bind("ctrl S", this::saveReport);
        bind("ctrl V", this::pasteExpected);
        bind("ctrl C", () -> copyCalculated(null));
        bind("ctrl U", this::toggleCase);
        bind("ctrl shift X", this::clearAll);
        bind("F5", () -> startVerify(false));
        bind("ctrl ENTER", () -> startVerify(false));
        bind("ctrl K", () -> startVerify(true));
        bind("ctrl P", this::togglePause);
        bind("ESCAPE", this::cancel);
        bind("ctrl 1", () -> showTab(0));
        bind("ctrl 2", () -> showTab(1));
        bind("ctrl TAB", () -> showTab(1 - tabIdx));
        bind("F1", this::showGuide);
        bind("alt F4", this::exit);
        String mn = "FEAVOTH";
        for (int i = 0; i < mn.length(); i++) {
            final int idx = i;
            bind("alt " + mn.charAt(i), () -> {
                JMenu m = menuBar.getMenu(idx);
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menuBar, m, m.getPopupMenu()});
            });
        }
    }

    private void bind(String ks, Runnable r) {
        JRootPane rp = root;
        String key = "act-" + ks;
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(ks), key);
        rp.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    private void wireToolbar() {
        toolbar.open.addActionListener(e -> openFile());
        toolbar.folder.addActionListener(e -> openFolder());
        toolbar.paste.addActionListener(e -> pasteExpected());
        toolbar.copy.addActionListener(e -> copyCalculated(null));
        toolbar.clear.addActionListener(e -> clearAll());
        toolbar.preset.addActionListener(e -> {
            if (syncing) return;
            switch (toolbar.preset.getSelectedIndex()) {
                case 0 -> { multiChanged(false); algoChanged(Algo.SHA256, true); }
                case 1 -> multiChanged(true);
                case 2 -> { multiChanged(false); algoChanged(Algo.MD5, true); }
                default -> { multiChanged(false); algoChanged(Algo.SHA512, true); }
            }
        });
    }

    // ---------------------------------------------------------------- menus
    private AItem item(JMenu m, String text, char mn, String accel, Runnable r) {
        AItem it = new AItem(text, true);
        if (mn != 0) it.setMnemonic(mn);
        if (accel != null) it.putClientProperty("accel", accel);
        it.addActionListener(e -> r.run());
        m.add(it);
        return it;
    }

    private ACheckItem check(JMenu m, String text, char mn, boolean sel, java.util.function.Consumer<Boolean> r) {
        ACheckItem it = new ACheckItem(text, sel);
        if (mn != 0) it.setMnemonic(mn);
        it.addActionListener(e -> r.accept(it.isSelected()));
        m.add(it);
        return it;
    }

    private AMenu menu(String t, char mn) {
        AMenu m = new AMenu(t);
        m.setMnemonic(mn);
        menuBar.add(m);
        return m;
    }

    private void buildMenus() {
        AMenu f = menu("File", 'F');
        item(f, "Open Target File...", 'O', "Ctrl+O", this::openFile);
        item(f, "Open Target Folder...", 'F', "Ctrl+F", this::openFolder);
        f.add(new ASep());
        item(f, "Load Checksum (.sha256/.md5)...", 'L', "Ctrl+L", this::loadChecksumFile);
        miSaveReport = item(f, "Save Verification Report...", 'R', "Ctrl+S", this::saveReport);
        f.add(new ASep());
        miRecent = new AMenu("Recent Files");
        miRecent.setMnemonic('E');
        f.add(miRecent);
        f.add(new ASep());
        item(f, "Exit", 'x', "Alt+F4", this::exit);
        rebuildRecent();

        AMenu e = menu("Edit", 'E');
        item(e, "Paste Expected Hash", 'P', "Ctrl+V", this::pasteExpected);
        item(e, "Copy Calculated Hash", 'C', "Ctrl+C", () -> copyCalculated(null));
        item(e, "Copy Summary", 'S', null, this::copySummary);
        item(e, "Copy Expected Hash", 'E', null, this::copyExpected);
        e.add(new ASep());
        item(e, "Toggle UPPER / lower Case", 'U', "Ctrl+U", this::toggleCase);
        item(e, "Clear Expected Hash", 'x', null, this::clearExpected);
        item(e, "Clear All", 'A', "Ctrl+Shift+X", this::clearAll);

        AMenu a = menu("Action", 'A');
        miVerify = item(a, "Calculate & Verify Checksum", 'C', "F5", () -> startVerify(false));
        miCompare = item(a, "Compare Hashes", 'm', "Ctrl+K", () -> startVerify(true));
        a.add(new ASep());
        miPause = item(a, "Pause / Resume", 'P', "Ctrl+P", this::togglePause);
        miCancel = item(a, "Cancel Calculation", 'n', "Esc", this::cancel);
        a.add(new ASep());
        item(a, "Go to Tab 1: Target File & Configuration", '1', "Ctrl+1", () -> showTab(0));
        item(a, "Go to Tab 2: Calculated Hashes", '2', "Ctrl+2", () -> showTab(1));

        AMenu v = menu("View", 'V');
        miToolbar = check(v, "Toolbar", 'T', true, on -> toolbar.setVisible(on));
        miStatus = check(v, "Status Bar", 'S', true, on -> status.setVisible(on));
        v.add(new ASep());
        miTop = check(v, "Always on Top", 'A', false, on -> frame.setAlwaysOnTop(on));
        v.add(new ASep());
        item(v, "Next Tab", 'N', "Ctrl+Tab", () -> showTab(1 - tabIdx));

        AMenu o = menu("Options", 'O');
        AMenu am = new AMenu("Algorithm");
        am.setMnemonic('A');
        ButtonGroup bg = new ButtonGroup();
        for (Algo al : Algo.values()) {
            ARadioItem ri = new ARadioItem(al.label, al == algo);
            ri.addActionListener(ev -> algoChanged(al, true));
            bg.add(ri);
            am.add(ri);
            miAlgo[al.ordinal()] = ri;
        }
        o.add(am);
        miMulti = check(o, "Multi-hash Parallel Stream", 'M', multi, this::multiChanged);
        miAuto = check(o, "Switch to Tab 2 on Complete", 'S', autoSwitch, on -> { autoSwitch = on; setup.autoSwitchChk.setSelected(on); });
        miUpper = check(o, "Uppercase Digests", 'U', upper, on -> { if (on != upper) toggleCase(); });
        o.add(new ASep());
        item(o, "Settings...", 'e', null, this::showSettings);

        AMenu t = menu("Tools", 'T');
        miSaveDigest = item(t, "Save Digest to File...", 'D', null, this::saveDigestFile);
        miSfv = item(t, "Generate .sfv File...", 'G', null, this::generateSfv);
        t.add(new ASep());
        item(t, "Save Session Log...", 'L', null, this::saveLog);
        item(t, "Clear Recent Files", 'R', null, () -> { recent.clear(); rebuildRecent(); savePrefs(); });

        AMenu h = menu("Help", 'H');
        item(h, "Quick Guide", 'Q', "F1", this::showGuide);
        h.add(new ASep());
        item(h, "About Checksum Checker", 'A', null, this::showAbout);
    }

    private void rebuildRecent() {
        miRecent.removeAll();
        if (recent.isEmpty()) {
            AItem none = new AItem("(empty)", true);
            none.setEnabled(false);
            miRecent.add(none);
        }
        for (String p : recent) {
            AItem it = new AItem(p, true);
            it.addActionListener(ev -> { if (!running) setFile(p); });
            miRecent.add(it);
        }
    }

    // ================================================================ preferences
    private void loadPrefs() {
        try { algo = Algo.valueOf(prefs.get("algo", "SHA256")); } catch (Exception ignored) { }
        multi = prefs.getBoolean("multi", false);
        autoSwitch = prefs.getBoolean("autoSwitch", true);
        upper = prefs.getBoolean("upper", false);
        asyncIo = prefs.getBoolean("asyncIo", true);
        chunk = prefs.getInt("chunk", 1 << 20);
        for (String s : prefs.get("recent", "").split("\n")) if (!s.isBlank()) recent.add(s);
    }

    void savePrefs() {
        try {
            prefs.put("algo", algo.name());
            prefs.putBoolean("multi", multi);
            prefs.putBoolean("autoSwitch", autoSwitch);
            prefs.putBoolean("upper", upper);
            prefs.putBoolean("asyncIo", asyncIo);
            prefs.putInt("chunk", chunk);
            prefs.put("recent", String.join("\n", recent));
            prefs.flush();
        } catch (Exception ignored) { }
    }

    private void applySettingsToUi() {
        syncing = true;
        setup.algoCombo.setSelectedIndex(algo.ordinal());
        setup.multiChk.setSelected(multi);
        setup.autoSwitchChk.setSelected(autoSwitch);
        setup.ioAsync.setSelected(asyncIo);
        setup.ioBuffered.setSelected(!asyncIo);
        syncPreset();
        syncing = false;
        setup.extensionsVal.set(Engine.indicator + " (" + Runtime.getRuntime().availableProcessors() + " Threads)");
        setup.engineLbl.set(Engine.indicator.equals("Software SHA") ? "Software hashing engine active (no CPU SHA intrinsics detected)" : "Hashing engine active (" + Engine.indicator + ")");
        syncExpectedBadge();
    }

    private void syncPreset() {
        boolean was = syncing;
        syncing = true;
        int idx = multi ? 1 : algo == Algo.MD5 ? 2 : algo == Algo.SHA512 ? 3 : 0;
        if (!multi && algo == Algo.SHA1) idx = 0;
        toolbar.preset.setSelectedIndex(idx);
        syncing = was;
    }

    // ================================================================ title / status
    static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        if (a.contains("aarch64") || a.contains("arm64")) return "ARM64";
        if (a.contains("amd64") || a.contains("x86_64")) return "x64";
        return a.contains("86") ? "x86" : a;
    }

    private String titleFor(String path) {
        String t = "Checksum Checker " + VERSION + " (" + arch() + ")";
        return path == null || path.isEmpty() ? t : t + " - [" + path + "]";
    }

    private void refreshStatusPanes() {
        status.algo = multi ? "Algorithm: All (Parallel)" : "Algorithm: " + algo.label + (Engine.hw ? " (HW Intrinsics)" : " (Software)");
        int cores = Runtime.getRuntime().availableProcessors();
        status.threads = running && job != null ? "Threads: " + job.threads + "/" + cores + " Active" : "Threads: " + cores + " Ready";
        status.buffer = "Buffer: " + bufferText();
        String os = System.getProperty("os.name", "");
        status.os = (os.toLowerCase().startsWith("windows") ? "Windows" : os.split(" ")[0]) + " " + arch();
        status.repaint();
    }

    private String bufferText() {
        long b = (long) chunk * 6;
        return b >= 1 << 20 ? (b >> 20) + " MB" : (b >> 10) + " KB";
    }

    private String chunkText() {
        return chunk >= 1 << 20 ? (chunk >> 20) + " MB" : (chunk >> 10) + " KB";
    }

    private void tick() {
        ticks++;
        toolbar.pulse.phase += 0.13f;
        toolbar.pulse.repaint();
        if (status.spin) { status.spinAngle += 0.25; status.repaint(); }
        if (ticks % 20 == 0) {
            try {
                boolean n = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_NUM_LOCK);
                if (n != status.num) { status.num = n; status.repaint(); }
            } catch (Exception ignored) { }
        }
    }

    // ================================================================ tabs
    void showTab(int i) {
        tabIdx = i;
        cl.show(cards, "t" + i);
        tabs.select(i);
    }

    // ================================================================ file handling
    void setTitleText(String t) {
        titleText = t;
        if (frame != null) frame.setTitleText(t);
    }

    String fileName() { return file == null || file.getFileName() == null ? null : file.getFileName().toString(); }

    void openFile() {
        if (running) return;
        FileDialog fd = new FileDialog(frame, "Open Target File", FileDialog.LOAD);
        if (file != null && file.getParent() != null) fd.setDirectory(file.getParent().toString());
        fd.setVisible(true);
        if (fd.getFile() != null) setFile(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
    }

    void openFolder() {
        File dir = file != null && file.getParent() != null ? file.getParent().toFile() : null;
        if (dir == null || !dir.isDirectory()) { status.set("warning", c(0xC00000), "No target folder: select a target file first."); return; }
        try {
            Desktop.getDesktop().open(dir);
            status.set("check", c(0x008000), "Target folder opened: " + dir + File.separator);
        } catch (Exception ex) {
            status.set("warning", c(0xC00000), "Could not open folder: " + dir);
        }
    }

    void pathCommitted() {
        String t = setup.pathField.getText().trim();
        if (t.length() > 1 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        String cur = file == null ? "" : file.toString();
        if (!t.equals(cur)) setFile(t);
    }

    void setFile(String p) {
        if (running) return;
        Path np = null;
        if (p != null && !p.isBlank()) {
            try { np = Paths.get(p.trim()); } catch (InvalidPathException ex) { np = null; }
        }
        boolean changed = !Objects.equals(np, file);
        file = np;
        if (!setup.pathField.getText().equals(p == null ? "" : p)) setup.pathField.setText(p == null ? "" : p);
        refreshFileInfo();
        if (changed) {
            last = null;
            resetResults();
            if (fileOk) {
                addRecent(file.toString());
                autoLoadSibling();
            }
        }
        setTitleText(titleFor(file == null ? null : file.toString()));
        if (file == null) status.set("check", c(0x008000), "Ready: select a target file to begin.");
        else if (fileOk) status.set("check", c(0x008000), "Selected file: " + fileName());
        else status.set("warning", c(0xC00000), "File not found or not readable: " + file);
        updateEnabled();
    }

    private void refreshFileInfo() {
        fileOk = false;
        fileSize = 0;
        String d = "\u2014";
        if (file != null && Files.isRegularFile(file) && Files.isReadable(file)) {
            try {
                BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class);
                fileOk = true;
                fileSize = a.size();
                SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                setup.sizeVal.set(HashEngine.size(fileSize) + " (" + String.format("%,d", fileSize) + " bytes)");
                setup.createdVal.set(sf.format(new Date(a.creationTime().toMillis())));
                setup.modifiedVal.set(sf.format(new Date(a.lastModifiedTime().toMillis())));
                List<String> at = new ArrayList<>();
                try {
                    DosFileAttributes da = Files.readAttributes(file, DosFileAttributes.class);
                    if (da.isArchive()) at.add("Archive");
                    if (da.isReadOnly()) at.add("Read-only");
                    if (da.isHidden()) at.add("Hidden");
                    if (da.isSystem()) at.add("System");
                } catch (Exception ex) {
                    if (!Files.isWritable(file)) at.add("Read-only");
                    if (file.getFileName().toString().startsWith(".")) at.add("Hidden");
                }
                if (at.isEmpty() || (at.size() == 1 && at.get(0).equals("Archive"))) at.add("Normal");
                setup.attrVal.set(String.join(", ", at));
                return;
            } catch (Exception ignored) { }
        }
        setup.sizeVal.set(d);
        setup.createdVal.set(d);
        setup.modifiedVal.set(d);
        setup.attrVal.set(d);
    }

    private void autoLoadSibling() {
        if (!setup.expected.getText().isBlank()) return;
        for (Algo a : new Algo[]{Algo.SHA256, Algo.SHA512, Algo.SHA1, Algo.MD5}) {
            Path sib = file.resolveSibling(fileName() + "." + a.ext);
            if (Files.isRegularFile(sib)) {
                try {
                    String txt = Files.readString(sib, StandardCharsets.UTF_8);
                    String h = HashEngine.extractHash(txt.length() > 1 << 20 ? txt.substring(0, 1 << 20) : txt, fileName());
                    if (HashEngine.validate(h).state() == VState.VALID) {
                        setup.expected.setText(h);
                        status.set("check", c(0x008000), "Loaded expected checksum from " + sib.getFileName());
                        return;
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    private void addRecent(String p) {
        recent.remove(p);
        recent.add(0, p);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
        rebuildRecent();
        savePrefs();
    }

    // ================================================================ expected hash
    void expectedEdited() {
        syncExpectedBadge();
        String norm = HashEngine.extractHash(setup.expected.getText(), fileName());
        Validation v = HashEngine.validate(norm);
        if (v.state() == VState.VALID && !multi && v.algo() != algo && !running) {
            algoChanged(v.algo(), false);
            status.set("check", c(0x008000), "Auto-detected " + v.algo().label + " from hash length.");
        }
    }

    private void syncExpectedBadge() {
        if (setup == null) return;
        String norm = HashEngine.extractHash(setup.expected.getText(), fileName());
        Validation v = HashEngine.validate(norm);
        switch (v.state()) {
            case VALID -> setup.formatBadge.set(v.message(), c(0x065F46), c(0xECFDF5), c(0xA7F3D0), "check_circle", c(0x059669));
            case INVALID -> setup.formatBadge.set(v.message(), c(0x991B1B), c(0xFEF2F2), c(0xFECACA), "error", c(0xDC2626));
            default -> setup.formatBadge.set(v.message(), c(0x64748B), c(0xF1F5F9), c(0xCBD5E1), "info", c(0x64748B));
        }
    }

    private void trimExpected() {
        String t = setup.expected.getText();
        String n = HashEngine.extractHash(t, fileName());
        if (!n.equals(t) && HashEngine.validate(n).state() == VState.VALID) setup.expected.setText(upper ? n.toUpperCase() : n.toLowerCase());
    }

    void toggleCase() {
        upper = !upper;
        String t = setup.expected.getText();
        String n = HashEngine.extractHash(t, fileName());
        if (HashEngine.validate(n).state() == VState.VALID) setup.expected.setText(upper ? n.toUpperCase() : n.toLowerCase());
        miUpper.setSelected(upper);
        if (last != null && last.end == End.DONE) showResult(lastVerdict, verifiedExpected, lastStamp, true);
        status.set("check", c(0x008000), upper ? "Digests shown in UPPERCASE." : "Digests shown in lowercase.");
    }

    String clipboardText() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ex) {
            return "";
        }
    }

    void toClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    void pasteExpected() {
        if (running) return;
        String t = clipboardText();
        if (t == null || t.isBlank()) { status.set("warning", c(0xC00000), "Clipboard is empty."); return; }
        String n = HashEngine.extractHash(t, fileName());
        setup.expected.setText(HashEngine.validate(n).state() == VState.VALID ? (upper ? n.toUpperCase() : n.toLowerCase()) : n);
        status.set("check", c(0x008000), "Pasted checksum from clipboard.");
    }

    void loadChecksumFile() {
        if (running) return;
        FileDialog fd = new FileDialog(frame, "Load Checksum File", FileDialog.LOAD);
        if (file != null && file.getParent() != null) fd.setDirectory(file.getParent().toString());
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        Path p = new File(fd.getDirectory(), fd.getFile()).toPath();
        try {
            if (Files.size(p) > 4 << 20) throw new java.io.IOException("File is too large to be a checksum file.");
            String h = HashEngine.extractHash(Files.readString(p, StandardCharsets.UTF_8), fileName());
            Validation v = HashEngine.validate(h);
            if (v.state() != VState.VALID) throw new java.io.IOException("No valid checksum found in " + p.getFileName());
            setup.expected.setText(upper ? h.toUpperCase() : h.toLowerCase());
            status.set("check", c(0x008000), "Loaded checksum signature from " + p.getFileName());
        } catch (Exception ex) {
            status.set("warning", c(0xC00000), "Could not load checksum: " + ex.getMessage());
        }
    }

    void clearExpected() {
        setup.expected.setText("");
        status.set("check", c(0x008000), "Expected checksum cleared.");
    }

    void clearAll() {
        if (running) return;
        setup.expected.setText("");
        setup.pathField.setText("");
        file = null;
        last = null;
        refreshFileInfo();
        resetResults();
        setTitleText(titleFor(null));
        status.set("check", c(0x008000), "All fields cleared.");
        showTab(0);
        updateEnabled();
    }

    void algoChanged(Algo a, boolean user) {
        if (syncing) return;
        syncing = true;
        algo = a;
        setup.algoCombo.setSelectedIndex(a.ordinal());
        miAlgo[a.ordinal()].setSelected(true);
        syncing = false;
        syncPreset();
        refreshStatusPanes();
        syncExpectedBadge();
        savePrefs();
    }

    void multiChanged(boolean m) {
        if (syncing) return;
        syncing = true;
        multi = m;
        setup.multiChk.setSelected(m);
        miMulti.setSelected(m);
        syncing = false;
        syncPreset();
        refreshStatusPanes();
        savePrefs();
    }

    // ================================================================ hashing
    void startVerify(boolean compareOnly) {
        if (running) return;
        if (!fileOk) {
            refreshFileInfo();
            if (!fileOk) {
                status.set("warning", c(0xC00000), file == null ? "Select a target file first." : "File not found or not readable: " + file);
                showTab(0);
                if (file == null) AeroDialog.message(frame, "Checksum Checker", "Please select a target file before calculating a checksum.", "info", c(0x2B78E4));
                return;
            }
        }
        String norm = HashEngine.extractHash(setup.expected.getText(), fileName());
        Validation v = HashEngine.validate(norm);
        if (v.state() == VState.INVALID) {
            showTab(0);
            status.set("warning", c(0xC00000), "Expected checksum is not valid: " + v.message());
            setup.expected.requestFocusInWindow();
            return;
        }
        Algo target = v.state() == VState.VALID ? v.algo() : algo;
        if (compareOnly && last != null && last.end == End.DONE && Objects.equals(last.file, file) && last.hex.containsKey(target)) {
            primary = target;
            applyVerdict(last, v.state() == VState.EMPTY ? "" : norm.toLowerCase(), true);
            showTab(1);
            return;
        }
        EnumSet<Algo> set = EnumSet.of(target);
        if (multi) set = EnumSet.allOf(Algo.class);
        primary = target;
        verifiedExpected = v.state() == VState.EMPTY ? "" : norm.toLowerCase();
        launch(set);
    }

    private void launch(Set<Algo> set) {
        final Job[] self = new Job[1];
        self[0] = new Job(file, set, chunk, new Listener() {
            @Override public void progress(long done, long total, double bps, long ms, long block, long blocks) {
                SwingUtilities.invokeLater(() -> { if (job == self[0]) onProgress(done, total, bps, ms, block, blocks); });
            }

            @Override public void finished(Result r) {
                SwingUtilities.invokeLater(() -> { if (job == self[0]) onFinished(r); });
            }
        });
        job = self[0];
        running = true;
        last = null;
        setup.pauseBtn.setText("Pause");
        setup.verifyBtn.setText("Calculating Checksum...");
        results.setSummary(fileName(), HashEngine.size(fileSize), verifiedExpected, primary.label, verifiedExpected.isEmpty() || verifiedExpected.length() == primary.hexLen);
        List<ResultsTab.Row> rows = new ArrayList<>();
        for (Algo a : Algo.values()) {
            boolean on = set.contains(a);
            rows.add(new ResultsTab.Row(a.label, "\u2014", on ? "Calculating\u2026" : "Not computed", null, on ? c(0x0055D4) : c(0x999999), "\u2014", a == primary));
        }
        results.setRows(rows, primary.ordinal());
        results.setDiff(primary.label, verifiedExpected, "");
        results.setProgress(0, "Streaming: 0 B of " + HashEngine.size(fileSize) + " processed", "Elapsed: 00:00", "0%", "Estimated time remaining (ETA): --:--", blockText(0, Math.max(1, (fileSize + chunk - 1) / chunk)), false);
        results.setVerdict(V.NONE, "CALCULATING CHECKSUM\u2026", "Hashing " + fileName() + " \u2014 please wait. Press Esc to cancel.", "");
        tabs.setBadge("0%", c(0x0B4A9E), c(0xDBEAFE), c(0x93C5FD));
        status.set("sync", c(0x0055D4), "Hashing file blocks: streaming " + (asyncIo ? "asynchronous I/O" : "buffered I/O") + "...");
        updateEnabled();
        refreshStatusPanes();
        Thread t = new Thread(job, "hash-job");
        t.setDaemon(true);
        t.start();
    }

    private String blockText(long block, long blocks) {
        return String.format("%s Block: %,d / %,d (%s chunks) \u2022 %s", asyncIo ? "Direct I/O" : "Buffered I/O", block, blocks, chunkText(), Engine.hw ? "SHA Intrinsics" : "Software");
    }

    private void onProgress(long done, long total, double bps, long ms, long block, long blocks) {
        double frac = total == 0 ? 1 : (double) done / total;
        int pct = (int) Math.floor(frac * 100);
        String eta = bps > 0 ? HashEngine.clock((long) ((total - done) / bps * 1000)) : "--:--";
        boolean paused = job != null && job.isPaused();
        results.setProgress(frac, "Streaming: " + HashEngine.size(done) + " of " + HashEngine.size(total) + " (" + HashEngine.speed(bps) + ")",
                "Elapsed: " + HashEngine.clock(ms), pct + "% Completed",
                paused ? "Paused" : "Estimated time remaining (ETA): " + eta, blockText(block, blocks), paused);
        tabs.setBadge(pct + "%", null, null, null);
        if (!paused) status.set("sync", c(0x0055D4), "Hashing " + fileName() + ": " + pct + "% (" + HashEngine.speed(bps) + ")");
    }

    private void onFinished(Result r) {
        running = false;
        setup.verifyBtn.setText("Calculate & Verify Checksum");
        setup.pauseBtn.setText("Pause");
        results.bar.paused = false;
        updateEnabled();
        refreshStatusPanes();
        String stamp = new SimpleDateFormat("h:mm:ss a").format(new Date());
        if (r.end == End.ERROR) {
            last = null;
            results.setVerdict(V.ERROR, "\u2716 VERIFICATION FAILED", "Could not read the file: " + r.error, "Failed at " + stamp);
            results.setRows(emptyRows(), primary.ordinal());
            tabs.setBadge("Error", c(0x991B1B), c(0xFEE2E2), c(0xFCA5A5));
            status.set("warning", c(0xC00000), "Verification failed: " + r.error);
            appendLog("ERROR", r, "");
            if (autoSwitch) showTab(1);
            return;
        }
        if (r.end == End.CANCELLED) {
            last = null;
            results.setVerdict(V.CANCELLED, "\u25A0 OPERATION CANCELLED", "Hashing was stopped before the file was fully processed.", "Cancelled at " + stamp);
            results.setRows(emptyRows(), primary.ordinal());
            results.setProgress(results.bar.getValue(), "Cancelled", "", (int) (results.bar.getValue() * 100) + "% Completed", "Operation cancelled", blockText(0, r.blocks), false);
            tabs.setBadge("Cancelled", c(0xB45309), c(0xFEF3C7), c(0xFCD34D));
            status.set("warning", c(0xB45309), "Calculation cancelled.");
            return;
        }
        last = r;
        results.setProgress(1, "Streaming: " + HashEngine.size(r.size) + " of " + HashEngine.size(r.size) + " processed (" + HashEngine.speed(r.avgBps) + " avg)",
                "Elapsed: " + HashEngine.clock(r.elapsedMs), "100% Completed", "Operation completed (ETA: 00:00)", blockText(r.blocks, r.blocks), false);
        applyVerdict(r, verifiedExpected, false);
        if (autoSwitch) showTab(1);
    }

    private List<ResultsTab.Row> emptyRows() {
        List<ResultsTab.Row> rows = new ArrayList<>();
        for (Algo a : Algo.values()) rows.add(new ResultsTab.Row(a.label, "\u2014", "Not computed", null, c(0x999999), "\u2014", a == primary));
        return rows;
    }

    /** Decide MATCH / MISMATCH / COMPUTED for result r against expected (lower-case hex or empty). */
    private void applyVerdict(Result r, String expected, boolean recompare) {
        String calc = r.hex.get(primary);
        V v = expected.isEmpty() ? V.COMPUTED : expected.equalsIgnoreCase(calc) ? V.MATCH : V.MISMATCH;
        verifiedExpected = expected;
        lastVerdict = v;
        lastStamp = "Verified at " + new SimpleDateFormat("h:mm:ss a").format(new Date()) + " \u2022 " + Engine.indicator;
        showResult(v, expected, lastStamp, recompare);
        appendLog(v.name(), r, expected);
        savePrefs();
    }

    private void showResult(V v, String expected, String stamp, boolean recompare) {
        Result r = last;
        if (r == null) return;
        String calc = r.hex.get(primary);
        String disp = fmt(calc);
        String expDisp = fmt(expected);
        results.setSummary(fileName(), HashEngine.size(r.size), expDisp, primary.label, expected.isEmpty() || expected.length() == primary.hexLen);
        List<ResultsTab.Row> rows = new ArrayList<>();
        for (Algo a : Algo.values()) {
            String h = r.hex.get(a);
            if (h == null) {
                rows.add(new ResultsTab.Row(a.label, "\u2014", "Not computed", null, c(0x999999), "\u2014", false));
                continue;
            }
            boolean p = a == primary;
            String st = "Completed", ic = null;
            Color col = c(0x555555);
            if (p) {
                switch (v) {
                    case MATCH -> { st = "Match (Active)"; ic = "check_circle"; col = c(0x0055D4); }
                    case MISMATCH -> { st = "Mismatch"; ic = "warning"; col = c(0xC00000); }
                    default -> { st = "Completed"; ic = "check_circle"; col = c(0x0055D4); }
                }
            }
            rows.add(new ResultsTab.Row(a.label, fmt(h), st, ic, col, HashEngine.speed(r.bps.getOrDefault(a, 0.0)), p));
        }
        results.setRows(rows, primary.ordinal());
        results.setDiff(primary.label, expDisp, disp);
        switch (v) {
            case MATCH -> {
                results.setVerdict(v, "\u2714 CHECKSUM MATCHED (100% Identical)", "Calculated " + primary.label + " matches expected checksum exactly. File integrity is verified.", stamp);
                tabs.setBadge("Match 100%", c(0x1E293B), c(0xE2E8F0), c(0xCBD5E1));
                status.set("check", c(0x008000), "Verification complete: Hashes match perfectly.");
            }
            case MISMATCH -> {
                results.setVerdict(v, "\u2716 CHECKSUM MISMATCH (Hashes Do Not Match)", "Warning! The computed checksum differs from the expected value. The file may be corrupt or altered.", stamp);
                tabs.setBadge("Mismatch", c(0x991B1B), c(0xFEE2E2), c(0xFCA5A5));
                status.set("warning", c(0xC00000), "Verification failed: Checksum mismatch detected!");
            }
            default -> {
                results.setVerdict(v, "CHECKSUM CALCULATED (No Expected Value)", "No expected checksum was provided. Paste one on Tab 1 (or use Compare with Clipboard) to verify this file.", stamp);
                tabs.setBadge("Calculated", c(0x1E3A8A), c(0xDBEAFE), c(0x93C5FD));
                status.set("check", c(0x008000), "Calculation complete: " + primary.label + " digest ready.");
            }
        }
        updateEnabled();
    }

    private String fmt(String h) { return h == null ? "" : upper ? h.toUpperCase() : h.toLowerCase(); }

    private void resetResults() {
        verifiedExpected = "";
        lastVerdict = V.NONE;
        results.setSummary(file == null ? "No file selected" : fileName(), fileOk ? HashEngine.size(fileSize) : "", "", algo.label, true);
        results.setRows(emptyRows(), algo.ordinal());
        results.setDiff(algo.label, "", "");
        results.setProgress(0, "Streaming: idle", "Elapsed: 00:00", "0%", "Waiting to start", blockText(0, 0), false);
        results.setVerdict(V.NONE, "NO RESULT YET", "Select a file on Tab 1 and press \u201CCalculate & Verify Checksum\u201D.", "");
        tabs.setBadge(null, null, null, null);
        updateEnabled();
    }

    void togglePause() {
        if (!running || job == null) return;
        boolean p = !job.isPaused();
        job.setPaused(p);
        setup.pauseBtn.setText(p ? "Resume" : "Pause");
        results.bar.paused = p;
        results.bar.repaint();
        if (p) { status.set("warning", c(0xB45309), "Calculation paused."); results.etaLbl.set("Paused"); }
        else status.set("sync", c(0x0055D4), "Hashing resumed...");
    }

    void cancel() {
        if (running && job != null) job.cancel();
    }

    private void updateEnabled() {
        boolean can = !running;
        setup.verifyBtn.setEnabled(can);
        setup.compareBtn.setEnabled(can);
        setup.pauseBtn.setEnabled(running);
        setup.clearAllBtn.setEnabled(can);
        setup.browseBtn.setEnabled(can);
        setup.pasteBtn.setEnabled(can);
        setup.fromFileBtn.setEnabled(can);
        setup.clearBtn.setEnabled(can);
        setup.pathField.setEditable(can);
        results.setBusy(running);
        toolbar.open.setEnabled(can);
        toolbar.paste.setEnabled(can);
        toolbar.clear.setEnabled(can);
        boolean have = last != null && last.end == End.DONE;
        results.copyHashBtn.setEnabled(have);
        results.copyCalcBtn.setEnabled(have);
        results.saveBtn.setEnabled(have);
        results.sfvBtn.setEnabled(have);
        results.copyExpBtn.setEnabled(!verifiedExpected.isEmpty());
        toolbar.copy.setEnabled(have);
        if (miVerify != null) {
            miVerify.setEnabled(can);
            miCompare.setEnabled(can);
            miPause.setEnabled(running);
            miCancel.setEnabled(running);
            miSaveReport.setEnabled(have);
            miSfv.setEnabled(have);
            miSaveDigest.setEnabled(have);
        }
    }

    void compareWithClipboard() {
        if (running) return;
        String t = clipboardText();
        if (t == null || t.isBlank()) { status.set("warning", c(0xC00000), "Clipboard is empty."); return; }
        String n = HashEngine.extractHash(t, fileName());
        setup.expected.setText(HashEngine.validate(n).state() == VState.VALID ? fmt(n) : n);
        startVerify(true);
    }

    // ================================================================ clipboard / export
    void copyCalculated(String hash) {
        if (hash == null && last != null) hash = fmt(last.hex.get(primary));
        if (hash == null || hash.isEmpty()) { status.set("warning", c(0xC00000), "Nothing to copy: calculate a checksum first."); return; }
        toClipboard(hash);
        status.set("check", c(0x008000), "Calculated checksum copied to clipboard.");
    }

    void copyExpected() {
        if (verifiedExpected.isEmpty()) return;
        toClipboard(fmt(verifiedExpected));
        status.set("check", c(0x008000), "Expected checksum copied to clipboard.");
    }

    private String summaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Checksum Checker ").append(VERSION).append(" Summary\n");
        sb.append("File: ").append(file == null ? "-" : file).append('\n');
        if (last != null && last.end == End.DONE) {
            sb.append("Size: ").append(HashEngine.size(last.size)).append(" (").append(String.format("%,d", last.size)).append(" bytes)\n");
            sb.append("Algorithm: ").append(primary.label).append('\n');
            sb.append("Calculated: ").append(fmt(last.hex.get(primary))).append('\n');
            sb.append("Expected: ").append(verifiedExpected.isEmpty() ? "(none)" : fmt(verifiedExpected)).append('\n');
            sb.append("Status: ").append(switch (lastVerdict) {
                case MATCH -> "MATCH (100% Valid)";
                case MISMATCH -> "MISMATCH";
                default -> "CALCULATED (no expected value)";
            }).append('\n');
        } else sb.append("Status: no completed calculation\n");
        sb.append("Created: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
        return sb.toString();
    }

    void copySummary() {
        toClipboard(summaryText());
        status.set("check", c(0x008000), "Summary copied to clipboard.");
    }

    private void appendLog(String outcome, Result r, String expected) {
        log.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("  ").append(outcome)
                .append("  ").append(file).append('\n');
        if (r.end == End.DONE) {
            for (Algo a : Algo.values()) if (r.hex.containsKey(a)) log.append("    ").append(a.label).append(": ").append(r.hex.get(a)).append('\n');
            if (!expected.isEmpty()) log.append("    expected: ").append(expected).append('\n');
            log.append("    size: ").append(r.size).append(" bytes, ").append(HashEngine.speed(r.avgBps)).append(", ").append(HashEngine.clock(r.elapsedMs)).append('\n');
        } else if (r.error != null) log.append("    error: ").append(r.error).append('\n');
    }

    private Path saveDialog(String title, String name) {
        FileDialog fd = new FileDialog(frame, title, FileDialog.SAVE);
        if (file != null && file.getParent() != null) fd.setDirectory(file.getParent().toString());
        fd.setFile(name);
        fd.setVisible(true);
        if (fd.getFile() == null) return null;
        return new File(fd.getDirectory(), fd.getFile()).toPath();
    }

    private void write(Path p, String content, String ok) {
        try {
            Files.writeString(p, content, StandardCharsets.UTF_8);
            status.set("check", c(0x008000), ok + " " + p);
        } catch (Exception ex) {
            status.set("warning", c(0xC00000), "Could not save file: " + ex.getMessage());
            AeroDialog.message(frame, "Save failed", "The file could not be written:\n" + ex.getMessage(), "error", c(0xC00000));
        }
    }

    void saveLog() {
        if (log.length() == 0) { status.set("warning", c(0xC00000), "The session log is empty: run a verification first."); return; }
        Path p = saveDialog("Save Log", "checksum-verification.log");
        if (p != null) write(p, "Checksum Checker " + VERSION + " session log\n\n" + log, "Log saved to");
    }

    void saveReport() {
        if (last == null) return;
        Path p = saveDialog("Save Verification Report", fileName() + ".verification.txt");
        if (p == null) return;
        StringBuilder sb = new StringBuilder(summaryText()).append("\nAll computed digests:\n");
        for (Algo a : Algo.values()) if (last.hex.containsKey(a)) sb.append("  ").append(String.format("%-8s", a.label)).append(fmt(last.hex.get(a))).append('\n');
        sb.append("  CRC-32  ").append(last.crc32).append('\n');
        sb.append("\nElapsed: ").append(HashEngine.clock(last.elapsedMs)).append("  (").append(HashEngine.speed(last.avgBps)).append(")\n");
        write(p, sb.toString(), "Report saved to");
    }

    void saveDigestFile() {
        if (last == null || last.end != End.DONE) return;
        Path p = saveDialog("Save Digest to File", fileName() + "." + primary.ext);
        if (p != null) write(p, fmt(last.hex.get(primary)) + " *" + fileName() + "\n", "Checksum digest saved to");
    }

    void generateSfv() {
        if (last == null || last.end != End.DONE) return;
        Path p = saveDialog("Generate .sfv", fileName() + ".sfv");
        if (p == null) return;
        String s = "; Generated by Checksum Checker " + VERSION + "\n; " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n"
                + "; " + String.format("%12d", last.size) + "  " + fileName() + "\n" + fileName() + " " + last.crc32 + "\n";
        write(p, s, "SFV file saved to");
    }

    // ================================================================ dialogs
    void showSettings() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 0, 4, 10);
        String[] sizes = {"64 KB", "256 KB", "1 MB", "4 MB", "16 MB"};
        int[] vals = {1 << 16, 1 << 18, 1 << 20, 1 << 22, 1 << 24};
        ACombo cc = new ACombo(sizes, Fonts.ui(11.5f, false), c(0x0F172A), 24);
        for (int i = 0; i < vals.length; i++) if (vals[i] == chunk) cc.setSelectedIndex(i);
        g.gridx = 0; g.gridy = 0;
        p.add(new Lbl("Read chunk size:", Fonts.ui(11.5f, true), c(0x334155)), g);
        g.gridx = 1;
        p.add(cc, g);
        JCheckBox up = Widgets.check("Show digests in UPPERCASE", upper, Fonts.ui(11.5f, false), c(0x334155));
        JCheckBox au = Widgets.check("Switch to Tab 2 when calculation completes", autoSwitch, Fonts.ui(11.5f, false), c(0x334155));
        JCheckBox mu = Widgets.check("Calculate all algorithms in one parallel pass", multi, Fonts.ui(11.5f, false), c(0x334155));
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        p.add(up, g);
        g.gridy = 2;
        p.add(au, g);
        g.gridy = 3;
        p.add(mu, g);
        g.gridy = 4;
        p.add(new Lbl("Engine: " + Engine.indicator + " \u2022 " + Runtime.getRuntime().availableProcessors() + " logical processors", Fonts.mono(10.5f, false), c(0x64748B)), g);
        AeroDialog[] d = new AeroDialog[1];
        AButton ok = AeroDialog.btn("OK", () -> {
            chunk = vals[cc.getSelectedIndex()];
            if (up.isSelected() != upper) toggleCase();
            autoSwitch = au.isSelected();
            setup.autoSwitchChk.setSelected(autoSwitch);
            miAuto.setSelected(autoSwitch);
            multiChanged(mu.isSelected());
            refreshStatusPanes();
            savePrefs();
            d[0].dispose();
        });
        AButton cancel = AeroDialog.btn("Cancel", () -> d[0].dispose());
        d[0] = new AeroDialog(frame, "Settings", p, List.of(ok, cancel), 440, 260);
        d[0].getRootPane().setDefaultButton(ok);
        d[0].setVisible(true);
    }

    void showGuide() {
        String s = """
                Ctrl+O  Open target file          Ctrl+F  Open target folder
                Ctrl+L  Load checksum file        Ctrl+V  Paste expected hash
                F5      Calculate & verify        Ctrl+K  Compare hashes
                Ctrl+P  Pause / resume            Esc     Cancel calculation
                Ctrl+1 / Ctrl+2  Switch tab       Ctrl+U  Toggle hash case
                Ctrl+C  Copy calculated hash      Ctrl+S  Save report

                You can also drop a file onto the window. Pasted checksums may be bare hashes, "hash *file" lines, BSD-style lines or a whole SHA256SUMS manifest.""";
        JTextArea t = new JTextArea(s);
        t.setEditable(false);
        t.setFont(Fonts.mono(11f, false));
        t.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        AeroDialog[] d = new AeroDialog[1];
        d[0] = new AeroDialog(frame, "Quick Guide", t, List.of(AeroDialog.btn("OK", () -> d[0].dispose())), 560, 290);
        d[0].setVisible(true);
    }

    void showAbout() {
        AeroDialog.message(frame, "About Checksum Checker",
                "Checksum Checker " + VERSION + " (" + arch() + ")\nStreaming MD5 / SHA-1 / SHA-256 / SHA-512 verification.\n\nJava " + Runtime.version()
                        + "\nHashing engine: " + Engine.indicator, "verified_user", c(0x0055EA));
    }

    void exit() {
        if (running && !AeroDialog.confirm(frame, "Checksum Checker", "A calculation is still running. Exit anyway?")) return;
        if (job != null) job.cancel();
        savePrefs();
        frame.dispose();
        System.exit(0);
    }

    // ================================================================ engine description
    static final class Engine {
        static String indicator = "Software SHA";
        static boolean hw;

        static void init() {
            try {
                var bean = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
                boolean sha = flag(bean, "UseSHA256Intrinsics") || flag(bean, "UseSHA512Intrinsics") || flag(bean, "UseSHA1Intrinsics");
                int avx = 0;
                try { avx = Integer.parseInt(bean.getVMOption("UseAVX").getValue()); } catch (Exception ignored) { }
                String simd = avx >= 3 ? "AVX-512" : avx == 2 ? "AVX2" : avx == 1 ? "AVX" : "";
                hw = sha;
                indicator = sha ? "SHA Intrinsics" + (simd.isEmpty() ? "" : " / " + simd) : "Software SHA" + (simd.isEmpty() ? "" : " / " + simd);
            } catch (Throwable ignored) { }
        }

        private static boolean flag(com.sun.management.HotSpotDiagnosticMXBean b, String n) {
            try { return Boolean.parseBoolean(b.getVMOption(n).getValue()); } catch (Exception e) { return false; }
        }
    }
}
