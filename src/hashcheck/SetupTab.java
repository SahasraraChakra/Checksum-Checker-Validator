package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;
import hashcheck.HashEngine.Algo;
import hashcheck.Widgets.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import static hashcheck.Aero.c;

/** Tab 1: target file, expected checksum, engine parameters, verify action. */
final class SetupTab extends JPanel {
    final MainWindow app;

    final AField pathField;
    final Lbl sizeVal, createdVal, modifiedVal, attrVal;
    final ACombo algoCombo;
    final JCheckBox multiChk, autoSwitchChk;
    final JRadioButton ioAsync, ioBuffered;
    final AArea expected;
    final Chip formatBadge;
    final AButton verifyBtn, compareBtn, clearAllBtn, viewTab2Btn, pauseBtn, browseBtn, folderBtn, pasteBtn, fromFileBtn, clearBtn;
    final Lbl engineLbl, extensionsVal;
    private JPopupMenu recentPopup;

    SetupTab(MainWindow app) {
        this.app = app;
        setOpaque(true);
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());

        Font lab = Fonts.ui(11.5f, true);
        Color labC = c(0x334155);

        // ------------------------------------------------ 1. target file
        GroupBox g1 = new GroupBox("folder", "1. Target File Input", c(0x0055EA), Fonts.ui(11.5f, true), c(0x0E489E), new Insets(6, 10, 6, 10));
        JPanel b1 = new JPanel(new BorderLayout(0, 6));
        b1.setOpaque(false);

        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);
        Lbl pl = new Lbl("File Path:", lab, labC).align(SwingConstants.RIGHT);
        pl.setPreferredSize(new Dimension(64, 25));
        row1.add(pl, BorderLayout.WEST);

        pathField = new AField("", Fonts.mono(11.5f, false), 24, 20) {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = Aero.aa(g0);
                Ic.draw(g, "disc_full", 6, getHeight() / 2f - 7.5f, 15, c(0x64748B));
                T.draw(g, "\u25BE", getWidth() - 15, T.baseline(Fonts.ui(10f, false), getHeight() / 2f), Fonts.ui(10f, false), c(0x666666), 0);
            }
        };
        JComponent fieldWrap = pathField;
        MouseAdapter drop = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getX() > pathField.getWidth() - 22) showRecent();
            }
        };
        pathField.addMouseListener(drop);
        pathField.setPreferredSize(new Dimension(100, 25));
        row1.add(fieldWrap, BorderLayout.CENTER);

        browseBtn = new AButton("Browse...", St.T1, "folder_open", c(0xC28C00)).font(Fonts.ui(11.5f, true)).fg(c(0x003399)).size(78, 25);
        folderBtn = new AButton("Folder", St.T1, "drive_file_move", c(0xD9A726)).font(Fonts.ui(11.5f, false)).size(70, 25);
        row1.add(Widgets.hbox(8, browseBtn, folderBtn), BorderLayout.EAST);
        b1.add(row1, BorderLayout.NORTH);

        JPanel info = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Aero.aa(g0);
                g.setColor(c(0xF8FAFC));
                g.fill(Aero.rr(0, 0, getWidth(), getHeight(), 2));
                Aero.border(g, c(0xCBD5E1), 0, 0, getWidth(), getHeight(), 2, 1);
            }
        };
        info.setOpaque(false);
        info.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        Font f105 = Fonts.ui(10.5f, false), m105 = Fonts.mono(10.5f, false), m105b = Fonts.mono(10.5f, true);
        sizeVal = new Lbl("\u2014", m105b, c(0x0F172A));
        createdVal = new Lbl("\u2014", m105, c(0x1E293B));
        modifiedVal = new Lbl("\u2014", m105, c(0x1E293B));
        attrVal = new Lbl("\u2014", m105b, c(0x0F172A)).align(SwingConstants.RIGHT);
        GridBagConstraints gb = new GridBagConstraints();
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.insets = new Insets(0, 0, 0, 12);
        gb.weightx = 0.36; info.add(pair("Size:", f105, sizeVal), gb);
        gb.weightx = 0.22; info.add(pair("Created:", f105, createdVal), gb);
        gb.weightx = 0.22; info.add(pair("Modified:", f105, modifiedVal), gb);
        JPanel attr = new JPanel(new BorderLayout(6, 0));
        attr.setOpaque(false);
        attr.add(new Lbl("Attributes:", f105, c(0x475569)), BorderLayout.WEST);
        attr.add(attrVal, BorderLayout.CENTER);
        gb.weightx = 0.20; gb.insets = new Insets(0, 0, 0, 0); info.add(attr, gb);
        JPanel infoWrap = new JPanel(new BorderLayout());
        infoWrap.setOpaque(false);
        infoWrap.setBorder(BorderFactory.createEmptyBorder(0, 72, 0, 0));
        infoWrap.add(info);
        b1.add(infoWrap, BorderLayout.CENTER);
        g1.body.add(b1);

        // ------------------------------------------------ 2. expected
        GroupBox g2 = new GroupBox("fingerprint", "2. Expected Checksum & Hash Algorithm", c(0x0055EA), Fonts.ui(11.5f, true), c(0x0E489E), new Insets(6, 10, 6, 10));
        JPanel b2 = new JPanel(new BorderLayout(0, 6));
        b2.setOpaque(false);
        JPanel r1 = new JPanel(new BorderLayout());
        r1.setOpaque(false);
        Lbl al = new Lbl("Algorithm:", lab, labC).align(SwingConstants.RIGHT);
        al.setPreferredSize(new Dimension(64, 24));
        String[] items = new String[Algo.values().length];
        for (int i = 0; i < items.length; i++) items[i] = Algo.values()[i].longLabel;
        algoCombo = new ACombo(items, Fonts.ui(11.5f, true), c(0x003399), 24);
        multiChk = Widgets.check("Multi-hash Parallel Stream", false, Fonts.ui(10.5f, false), c(0x475569));
        JPanel left = Widgets.hbox(0, al, Box.createHorizontalStrut(8), algoCombo, Box.createHorizontalStrut(16), multiChk);
        r1.add(left, BorderLayout.WEST);
        pasteBtn = new AButton("Paste Hash", St.T1, "content_paste", c(0x334155)).font(Fonts.ui(11f, true)).size(0, 23).icon(13);
        fromFileBtn = new AButton("From File...", St.T1, "file_open", c(0x2B78E4)).font(Fonts.ui(11f, false)).size(0, 23);
        clearBtn = new AButton("Clear", St.T1, null, null).font(Fonts.ui(11f, false)).size(0, 23);
        r1.add(Widgets.hbox(6, pasteBtn, fromFileBtn, clearBtn), BorderLayout.EAST);
        b2.add(r1, BorderLayout.NORTH);

        JPanel r2 = new JPanel(new BorderLayout(8, 0));
        r2.setOpaque(false);
        Lbl el = new Lbl("Expected:", lab, labC).align(SwingConstants.RIGHT);
        el.setPreferredSize(new Dimension(64, 20));
        JPanel elw = new JPanel(new BorderLayout());
        elw.setOpaque(false);
        elw.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        elw.add(el, BorderLayout.NORTH);
        r2.add(elw, BorderLayout.WEST);
        expected = new AArea("", Fonts.mono(11f, false));
        expected.setPreferredSize(new Dimension(100, 36));
        JPanel cen = new JPanel(new BorderLayout(0, 2));
        cen.setOpaque(false);
        cen.add(expected, BorderLayout.NORTH);
        formatBadge = new Chip(Fonts.mono(10f, true));
        JPanel under = new JPanel(new BorderLayout());
        under.setOpaque(false);
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(formatBadge);
        under.add(badgeWrap, BorderLayout.WEST);
        Lbl toggle = new Lbl("Toggle UPPER / lower", Fonts.ui(10f, false), c(0x0055EA)).link(app::toggleCase);
        Lbl dot = new Lbl("\u2022", Fonts.ui(10f, false), c(0x64748B));
        Lbl trim = new Lbl("Auto-trim active", Fonts.ui(10f, false), c(0x64748B));
        under.add(Widgets.hbox(8, toggle, dot, trim), BorderLayout.EAST);
        cen.add(under, BorderLayout.CENTER);
        r2.add(cen, BorderLayout.CENTER);
        b2.add(r2, BorderLayout.CENTER);
        g2.body.add(b2);

        // ------------------------------------------------ 3. engine
        GroupBox g3 = new GroupBox("memory", "Engine Acceleration Parameters", c(0x0055EA), Fonts.ui(11.5f, true), c(0x0E489E), new Insets(4, 10, 4, 10));
        JPanel b3 = new JPanel(new BorderLayout(8, 0));
        b3.setOpaque(false);
        Font sb = Fonts.ui(10.5f, true);
        ioAsync = Widgets.radio("Direct I/O Asynchronous (Bypass Cache)", true, f105, c(0x334155));
        ioBuffered = Widgets.radio("Buffered", false, f105, c(0x334155));
        ButtonGroup bg = new ButtonGroup();
        bg.add(ioAsync);
        bg.add(ioBuffered);
        b3.add(Widgets.hbox(8, new Lbl("I/O Stream:", sb, c(0x1E293B)), ioAsync, ioBuffered), BorderLayout.WEST);
        extensionsVal = new Lbl("", Fonts.mono(10.5f, true), c(0x0F172A));
        JPanel ext = Widgets.hbox(8, new Lbl("Extensions:", sb, c(0x1E293B)), extensionsVal);
        ext.setBorder(new SideBorder(c(0xE2E8F0), 6));
        b3.add(ext, BorderLayout.CENTER);
        autoSwitchChk = Widgets.check("Switch to Tab 2 on complete", true, f105, c(0x334155));
        JPanel aa = Widgets.hbox(8, new Lbl("Auto-Action:", sb, c(0x1E293B)), autoSwitchChk);
        aa.setBorder(new SideBorder(c(0xE2E8F0), 6));
        b3.add(aa, BorderLayout.EAST);
        g3.body.add(b3);

        // ------------------------------------------------ 4. verification pane
        JPanel pane = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Aero.aa(g0);
                Shape s = Aero.rr(0, 0, getWidth(), getHeight(), 4);
                g.setColor(Aero.a(0x000000, 0.06f));
                g.fill(Aero.rr(0, 2, getWidth(), getHeight() - 1, 4));
                Aero.vgrad(g, s, new float[]{0, .5f, 1}, 0xEFF6FF, 0xDBEAFE, 0xBFDBFE);
                Aero.border(g, c(0x1E40AF), 0, 0, getWidth(), getHeight() - 1, 4, 2);
            }
        };
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 11, 10));
        JComponent disc = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Aero.aa(g0);
                g.setColor(c(0x1D4ED8));
                g.fill(new Ellipse2D.Float(0, 0, 40, 40));
                Ic.draw(g, "verified", 8, 8, 24, Color.WHITE);
            }
        };
        disc.setPreferredSize(new Dimension(40, 40));
        JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 1));
        textCol.setOpaque(false);
        textCol.add(new Lbl("Checksum Calculation & Verification Pane", Fonts.ui(13.5f, true), c(0x0F172A)).track(-0.15f));
        textCol.add(new Lbl("Compute cryptographic stream hash and compare directly with expected signature.", Fonts.ui(11f, false), c(0x1E3A8A)));
        JPanel leftP = new JPanel(new BorderLayout(12, 0));
        leftP.setOpaque(false);
        leftP.add(disc, BorderLayout.WEST);
        leftP.add(textCol, BorderLayout.CENTER);
        JPanel discWrap = new JPanel(new GridBagLayout());
        discWrap.setOpaque(false);
        discWrap.add(leftP, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        pane.add(discWrap, BorderLayout.CENTER);
        verifyBtn = new AButton("Calculate & Verify Checksum", St.VERIFY, "play_arrow", Color.WHITE).font(Fonts.ui(13f, true)).size(0, 34).pad(12).icon(18);
        compareBtn = new AButton("Compare Hashes", St.T1, "compare_arrows", c(0x0055D4)).font(Fonts.ui(11.5f, true)).fg(c(0x0F172A)).size(0, 34).pad(8).icon(15);
        clearAllBtn = new AButton("Clear All", St.T1, "clear_all", c(0xDC2626)).font(Fonts.ui(11f, true)).fg(c(0x991B1B)).size(0, 34).pad(8).icon(14);
        viewTab2Btn = new AButton("View Tab 2 Results \u00BB", St.T1, null, null).font(Fonts.ui(11.5f, true)).fg(c(0x1E40AF)).border(c(0x1E40AF)).size(0, 34).pad(8);
        pane.add(Widgets.hbox(8, verifyBtn, compareBtn, clearAllBtn, viewTab2Btn), BorderLayout.EAST);

        JPanel stack = new JPanel(new Widgets.VFlex(6, 0));
        stack.setOpaque(false);
        stack.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        stack.add(g1);
        stack.add(g2);
        stack.add(g3);
        stack.add(pane);
        add(stack, BorderLayout.CENTER);

        // ------------------------------------------------ bottom command bar
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(c(0xECEFF3));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(c(0xC4CBD4));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        engineLbl = new Lbl("", Fonts.ui(11f, false), c(0x555555)).icon("memory", c(0x444444), 14);
        bar.add(engineLbl, BorderLayout.WEST);
        pauseBtn = new AButton("Pause", St.T1, null, null).font(Fonts.ui(11.5f, false)).size(70, 24);
        AButton copySum = new AButton("Copy Summary", St.T1, null, null).font(Fonts.ui(11.5f, false)).size(90, 24);
        AButton saveLog = new AButton("Save Log...", St.T1, null, null).font(Fonts.ui(11.5f, false)).size(75, 24);
        AButton settings = new AButton("Settings...", St.T1, null, null).font(Fonts.ui(11.5f, false)).size(70, 24);
        AButton close = new AButton("Close", St.T1, null, null).font(Fonts.ui(11.5f, false)).size(70, 24);
        bar.add(Widgets.hbox(6, pauseBtn, copySum, saveLog, settings, close), BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        // ------------------------------------------------ wiring
        browseBtn.addActionListener(e -> app.openFile());
        folderBtn.addActionListener(e -> app.openFolder());
        pasteBtn.addActionListener(e -> app.pasteExpected());
        fromFileBtn.addActionListener(e -> app.loadChecksumFile());
        clearBtn.addActionListener(e -> app.clearExpected());
        verifyBtn.addActionListener(e -> app.startVerify(false));
        compareBtn.addActionListener(e -> app.startVerify(true));
        clearAllBtn.addActionListener(e -> app.clearAll());
        viewTab2Btn.addActionListener(e -> app.showTab(1));
        pauseBtn.addActionListener(e -> app.togglePause());
        copySum.addActionListener(e -> app.copySummary());
        saveLog.addActionListener(e -> app.saveLog());
        settings.addActionListener(e -> app.showSettings());
        close.addActionListener(e -> app.exit());
        pathField.addActionListener(e -> app.pathCommitted());
        pathField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { app.pathCommitted(); }
        });
        expected.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { app.expectedEdited(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { app.expectedEdited(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { app.expectedEdited(); }
        });
        algoCombo.addActionListener(e -> app.algoChanged(Algo.values()[algoCombo.getSelectedIndex()], true));
        multiChk.addActionListener(e -> app.multiChanged(multiChk.isSelected()));
        autoSwitchChk.addActionListener(e -> app.autoSwitch = autoSwitchChk.isSelected());
        ioAsync.addActionListener(e -> app.asyncIo = true);
        ioBuffered.addActionListener(e -> app.asyncIo = false);
        expected.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { }
        });
    }

    void showRecent() {
        recentPopup = Menus.popup(new JPopupMenu());
        java.util.List<String> r = app.recent;
        if (r.isEmpty()) {
            Menus.AItem none = new Menus.AItem("(no recent files)", false);
            none.setEnabled(false);
            recentPopup.add(none);
        }
        for (String p : r) {
            Menus.AItem it = new Menus.AItem(p, false);
            it.addActionListener(e -> app.setFile(p));
            recentPopup.add(it);
        }
        recentPopup.show(pathField, 0, pathField.getHeight());
    }

    private static JPanel pair(String k, Font f, Lbl v) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        p.add(new Lbl(k, f, c(0x64748B)), BorderLayout.WEST);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    /** Thin left divider + padding. */
    private static final class SideBorder extends javax.swing.border.AbstractBorder {
        final Color col;
        final int pad;

        SideBorder(Color col, int pad) { this.col = col; this.pad = pad; }

        @Override public Insets getBorderInsets(Component c) { return new Insets(0, pad + 1, 0, 0); }

        @Override public void paintBorder(Component cmp, Graphics g, int x, int y, int w, int h) {
            g.setColor(col);
            g.fillRect(x, y + 2, 1, h - 4);
        }
    }
}
