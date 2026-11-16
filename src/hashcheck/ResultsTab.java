package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;
import hashcheck.Widgets.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

import static hashcheck.Aero.c;

/** Tab 2: calculated digests, stream progress, character alignment and the verdict. */
final class ResultsTab extends JPanel {
    final MainWindow app;

    enum V {
        NONE(0x64748B, 0xC5CDD7, 0xF8FAFC, "info", 1),
        MATCH(0x0055D4, 0x0055D4, 0xF4F8FE, "verified", 2),
        MISMATCH(0xC00000, 0xC00000, 0xFFF5F5, "warning", 2),
        COMPUTED(0x1E3A8A, 0x7F9DB9, 0xF8FAFC, "info", 2),
        ERROR(0xC00000, 0xC00000, 0xFFF5F5, "error", 2),
        CANCELLED(0xB45309, 0xD97706, 0xFFFBEB, "stop", 2);

        final int fg, border, bg, bw;
        final String icon;

        V(int fg, int border, int bg, String icon, int bw) {
            this.fg = fg; this.border = border; this.bg = bg; this.icon = icon; this.bw = bw;
        }
    }

    record Row(String algo, String hash, String status, String statusIcon, Color statusColor, String speed, boolean primary) { }

    // widgets
    final Lbl fileName, fileSize, expLabel, expHash;
    final Chip charsChip;
    final AButton copyExpBtn, copyCalcBtn, sfvBtn, backBtn, reverifyBtn, copyHashBtn, saveBtn, clipCompareBtn;
    final ListView list;
    final Lbl streamLbl, elapsedLbl, pctLbl, etaLbl, blockLbl;
    final AProgress bar;
    final DiffView diff;
    final GroupBox diffBox, resultBox;
    final Lbl diffSummary, diffHamming;
    final Lbl mainLbl, subLbl, stampLbl;
    private V verdict = V.NONE;
    private JComponent bigIcon;

    ResultsTab(MainWindow app) {
        this.app = app;
        setOpaque(true);
        setBackground(Color.WHITE);
        setLayout(new Widgets.VFlex(4, 12));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ---------------------------------------------- summary strip
        JPanel strip = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Aero.aa(g0);
                g.setColor(c(0xF1F5F9));
                g.fill(Aero.rr(0, 0, getWidth(), getHeight(), 3));
                Aero.border(g, c(0xCBD5E1), 0, 0, getWidth(), getHeight(), 3, 1);
            }
        };
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        Font f105 = Fonts.ui(10.5f, false);
        Lbl docIcon = new Lbl("", f105, Color.BLACK).icon("description", c(0x0055EA), 14);
        docIcon.setPreferredSize(new Dimension(14, 16));
        fileName = new Lbl("", Fonts.mono(10.5f, false), c(0x0F172A));
        fileSize = new Lbl("", Fonts.ui(10f, false), c(0x64748B));
        expLabel = new Lbl("Expected SHA-256:", f105, c(0x64748B));
        expHash = new Lbl("", Fonts.mono(10.5f, true), c(0x003399));
        JPanel stripL = new JPanel(new BorderLayout(6, 0));
        stripL.setOpaque(false);
        stripL.add(docIcon, BorderLayout.WEST);
        JPanel fileBlock = new JPanel(new BorderLayout(4, 0));
        fileBlock.setOpaque(false);
        fileBlock.add(new Lbl("Target File:", Fonts.ui(10.5f, true), c(0x1E293B)), BorderLayout.WEST);
        JPanel fn = new JPanel(new BorderLayout(4, 0));
        fn.setOpaque(false);
        fn.add(fileName, BorderLayout.CENTER);
        fn.add(fileSize, BorderLayout.EAST);
        fileBlock.add(fn, BorderLayout.CENTER);
        JPanel expBlock = new JPanel(new BorderLayout(4, 0));
        expBlock.setOpaque(false);
        expBlock.add(expLabel, BorderLayout.WEST);
        expBlock.add(expHash, BorderLayout.CENTER);
        JPanel mid = new JPanel(new GridBagLayout());
        mid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 0.42;
        mid.add(fileBlock, gc);
        gc.weightx = 0;
        gc.insets = new Insets(0, 8, 0, 8);
        mid.add(Widgets.vsep(c(0xCBD5E1), 12), gc);
        gc.weightx = 0.58;
        gc.insets = new Insets(0, 0, 0, 0);
        mid.add(expBlock, gc);
        stripL.add(mid, BorderLayout.CENTER);
        strip.add(stripL, BorderLayout.CENTER);
        copyExpBtn = new AButton("Copy", St.T2, "content_copy", c(0x4F5B66)).font(Fonts.ui(10f, false)).size(0, 20).pad(6).icon(11);
        charsChip = new Chip(Fonts.mono(9.5f, true));
        strip.add(Widgets.hbox(6, copyExpBtn, charsChip), BorderLayout.EAST);
        add(strip);

        // ---------------------------------------------- list group
        GroupBox gl = new GroupBox("list_alt", "Calculated Checksums", c(0x0055EA), Fonts.ui(11f, true), c(0x0047AB), new Insets(2, 6, 4, 6));
        gl.borderC = c(0xC4CBD2);
        copyCalcBtn = new AButton("Copy Calculated", St.T2, "content_copy", c(0x2B78E4)).font(Fonts.ui(10f, false)).size(0, 19).pad(6).icon(11);
        sfvBtn = new AButton("Generate .sfv", St.T2, "verified_user", c(0x555555)).font(Fonts.ui(10f, false)).size(0, 19).pad(6).icon(11);
        gl.setLegendRight(Widgets.hbox(4, copyCalcBtn, sfvBtn));
        list = new ListView();
        gl.body.add(list);
        gl.putClientProperty("flex", Boolean.TRUE);
        add(gl);

        // ---------------------------------------------- progress
        JPanel pg = new JPanel(new GridLayout(3, 1, 0, 2)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Aero.aa(g0);
                g.setColor(c(0xF9FAFB));
                g.fill(Aero.rr(0, 0, getWidth(), getHeight(), 3));
                Aero.border(g, c(0xD5DCE2), 0, 0, getWidth(), getHeight(), 3, 1);
            }
        };
        pg.setOpaque(false);
        pg.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        streamLbl = new Lbl("", f105, c(0x222222));
        elapsedLbl = new Lbl("", Fonts.mono(10.5f, false), c(0x64748B));
        pctLbl = new Lbl("", Fonts.mono(10.5f, true), c(0x003399));
        JPanel l1 = new JPanel(new BorderLayout());
        l1.setOpaque(false);
        l1.add(streamLbl, BorderLayout.CENTER);
        l1.add(Widgets.hbox(8, elapsedLbl, pctLbl), BorderLayout.EAST);
        bar = new AProgress(10);
        JPanel barW = new JPanel(new GridBagLayout());
        barW.setOpaque(false);
        barW.add(bar, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        etaLbl = new Lbl("", Fonts.ui(9.5f, false), c(0x64748B));
        blockLbl = new Lbl("", Fonts.mono(9.5f, false), c(0x64748B)).align(SwingConstants.RIGHT);
        JPanel l3 = new JPanel(new BorderLayout());
        l3.setOpaque(false);
        l3.add(etaLbl, BorderLayout.WEST);
        l3.add(blockLbl, BorderLayout.CENTER);
        pg.add(l1);
        pg.add(barW);
        pg.add(l3);
        pg.setPreferredSize(new Dimension(100, 56));
        add(pg);

        // ---------------------------------------------- diff
        diffBox = new GroupBox("compare_arrows", "Character Alignment & Bit-Level Diff (SHA-256)", c(0x0055EA), Fonts.ui(11f, true), c(0x0047AB), new Insets(2, 6, 3, 6));
        diffBox.borderC = c(0xC4CBD2);
        diffSummary = new Lbl("", Fonts.mono(9.5f, true), c(0x065F46));
        diffHamming = new Lbl("", Fonts.mono(9.5f, false), c(0x64748B));
        diffBox.setLegendRight(Widgets.hbox(6, diffSummary, diffHamming));
        diff = new DiffView();
        diffBox.body.add(diff);
        add(diffBox);

        // ---------------------------------------------- result
        resultBox = new GroupBox("verified_user", "Verification Result", c(0x0055EA), Fonts.ui(11f, true), c(0x0047AB), new Insets(3, 8, 3, 8));
        resultBox.bw = 2;
        JPanel rb = new JPanel(new BorderLayout(10, 0));
        rb.setOpaque(false);
        bigIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Ic.draw((Graphics2D) g0, verdict.icon, 0, 3, 30, c(verdict.fg));
            }
        };
        bigIcon.setPreferredSize(new Dimension(30, 36));
        rb.add(bigIcon, BorderLayout.WEST);
        JPanel col = new JPanel(new BorderLayout(0, 1));
        col.setOpaque(false);
        mainLbl = new Lbl("", Fonts.ui(18.67f, true), c(0x0055D4)).track(-0.19f);
        subLbl = new Lbl("", Fonts.ui(10.5f, false), c(0x334155));
        col.add(mainLbl, BorderLayout.CENTER);
        col.add(subLbl, BorderLayout.SOUTH);
        rb.add(col, BorderLayout.CENTER);
        clipCompareBtn = new AButton("Compare with Clipboard", St.T2, "swap_horiz", c(0x555555)).font(Fonts.ui(10.5f, true)).size(0, 21).pad(8).icon(12);
        stampLbl = new Lbl("", Fonts.mono(9f, false), c(0x64748B)).align(SwingConstants.RIGHT);
        JPanel rr = new JPanel(new GridLayout(2, 1, 0, 2));
        rr.setOpaque(false);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(clipCompareBtn);
        rr.add(btnRow);
        rr.add(stampLbl);
        JPanel rrw = new JPanel(new GridBagLayout());
        rrw.setOpaque(false);
        rrw.add(rr, new GridBagConstraints());
        rb.add(rrw, BorderLayout.EAST);
        resultBox.body.add(rb);
        add(resultBox);

        // ---------------------------------------------- bottom
        JPanel bot = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(c(0xD9D9D9));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        backBtn = new AButton("\u00AB Back to Tab 1", St.T2, "arrow_back", c(0x333333)).font(Fonts.ui(11f, true)).size(0, 24).pad(12).icon(14);
        reverifyBtn = new AButton("Re-Verify / Re-Calculate", St.PRIMARY, "play_arrow", c(0x003380)).font(Fonts.ui(11f, true)).size(0, 24).pad(14).icon(14);
        copyHashBtn = new AButton("Copy Hash", St.T2, "content_copy", c(0x4F5B66)).font(Fonts.ui(11f, false)).size(0, 24).pad(12).icon(14);
        saveBtn = new AButton("Save to File...", St.T2, "save", c(0x2B78E4)).font(Fonts.ui(11f, false)).size(0, 24).pad(12).icon(14);
        bot.add(backBtn, BorderLayout.WEST);
        bot.add(Widgets.hbox(8, reverifyBtn, copyHashBtn, saveBtn), BorderLayout.EAST);
        add(bot);

        // wiring
        backBtn.addActionListener(e -> app.showTab(0));
        reverifyBtn.addActionListener(e -> app.startVerify(false));
        copyHashBtn.addActionListener(e -> app.copyCalculated(null));
        copyCalcBtn.addActionListener(e -> app.copyCalculated(list.selectedHash()));
        copyExpBtn.addActionListener(e -> app.copyExpected());
        saveBtn.addActionListener(e -> app.saveDigestFile());
        sfvBtn.addActionListener(e -> app.generateSfv());
        clipCompareBtn.addActionListener(e -> app.compareWithClipboard());
        setVerdict(V.NONE, "NO RESULT YET", "Select a file on Tab 1 and press \u201CCalculate & Verify Checksum\u201D.", "");
    }

    // -------------------------------------------------------------- setters
    void setSummary(String name, String size, String expectedHex, String algoLabel, boolean validLen) {
        fileName.set(name);
        fileSize.set(size.isEmpty() ? "" : "(" + size + ")");
        expLabel.set("Expected " + algoLabel + ":");
        if (expectedHex.isEmpty()) {
            expHash.set("(none)");
            charsChip.set("No expected value", c(0x64748B), c(0xF1F5F9), c(0xCBD5E1), null, null);
        } else {
            expHash.set(expectedHex);
            expHash.putClientProperty("full", expectedHex);
            charsChip.set(expectedHex.length() + " Chars (Hex)", validLen ? c(0x065F46) : c(0x991B1B),
                    validLen ? c(0xECFDF5) : c(0xFEF2F2), validLen ? c(0xA7F3D0) : c(0xFECACA),
                    validLen ? "check_circle" : "error", validLen ? c(0x059669) : c(0xDC2626));
        }
    }

    void setRows(List<Row> rows, int selected) {
        list.setRows(rows, selected);
    }

    void setProgress(double frac, String stream, String elapsed, String pct, String eta, String block, boolean paused) {
        bar.setValue(frac);
        bar.paused = paused;
        streamLbl.set(stream);
        elapsedLbl.set(elapsed);
        pctLbl.set(pct);
        etaLbl.set(eta);
        blockLbl.set(block);
    }

    void setDiff(String algoLabel, String exp, String calc) {
        diffBox.setLegend("Character Alignment & Bit-Level Diff (" + algoLabel + ")");
        diff.set(exp, calc);
        if (calc == null || calc.isEmpty()) {
            diffSummary.set("No digest yet");
            diffSummary.setColor(c(0x64748B));
            diffHamming.set("");
        } else if (exp == null || exp.isEmpty()) {
            diffSummary.set("No expected value");
            diffSummary.setColor(c(0x64748B));
            diffHamming.set("");
        } else {
            int d = HashEngine.charDiffs(exp, calc);
            int n = Math.max(exp.length(), calc.length());
            diffSummary.set((n - d) + "/" + n + " Matches (" + d + " Diffs)");
            diffSummary.setColor(d == 0 ? c(0x065F46) : c(0xC00000));
            diffHamming.set("\u2022 Hamming Dist: " + HashEngine.hamming(exp, calc));
        }
        diffSummary.setIcon(null, null);
        diffBox.repaint();
    }

    void setVerdict(V v, String main, String sub, String stamp) {
        verdict = v;
        resultBox.borderC = c(v.border);
        resultBox.bw = v.bw;
        resultBox.fill = c(v.bg);
        mainLbl.set(main);
        mainLbl.setColor(c(v.fg));
        subLbl.set(sub);
        stampLbl.set(stamp);
        bigIcon.repaint();
        resultBox.repaint();
    }

    V verdict() { return verdict; }

    void setBusy(boolean busy) {
        reverifyBtn.setEnabled(!busy);
        clipCompareBtn.setEnabled(!busy);
    }

    // ================================================================ list view
    static final class ListView extends JComponent {
        private List<Row> rows = new ArrayList<>();
        private int selected = 0;
        private static final int[] COLS = {85, 0, 130, 100};
        private final Font hf = Fonts.ui(11f, true), mono = Fonts.mono(11f, false), monoB = Fonts.mono(11f, true),
                ui = Fonts.ui(11f, false), uiB = Fonts.ui(11f, true);

        ListView() {
            setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i >= 0) { selected = i; repaint(); }
                }

                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && rowAt(e.getY()) >= 0) {
                        Container p = SwingUtilities.getAncestorOfClass(ResultsTab.class, ListView.this);
                        if (p instanceof ResultsTab rt) rt.app.copyCalculated(selectedHash());
                    }
                }
            });
        }

        void setRows(List<Row> r, int sel) {
            rows = r;
            selected = sel;
            repaint();
        }

        String selectedHash() {
            if (selected < 0 || selected >= rows.size()) return null;
            String h = rows.get(selected).hash();
            return h.startsWith("\u2014") ? null : h;
        }

        private int rowAt(int y) {
            int i = (y - 19) / 20;
            return y >= 19 && i < rows.size() ? i : -1;
        }

        @Override public String getToolTipText(MouseEvent e) {
            int i = rowAt(e.getY());
            if (i < 0) return null;
            Row r = rows.get(i);
            return r.hash().startsWith("\u2014") ? null : r.algo() + ": " + r.hash() + "  (double-click to copy)";
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 19 + 20 * 4 + 1); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(Color.WHITE);
            g.fill(Aero.rr(0, 0, w, h, 2));
            int c1 = COLS[0], c3 = COLS[2], c4 = COLS[3], c2 = w - c1 - c3 - c4 - 2;
            int[] x = {1, 1 + c1, 1 + c1 + c2, 1 + c1 + c2 + c3};
            int[] cw = {c1, c2, c3, c4};
            Aero.vgrad(g, new Rectangle(1, 1, w - 2, 18), new float[]{0, .5f, 1}, 0xFFFFFF, 0xEAEEF3, 0xDBE1E7);
            Aero.hline(g, c(0xA4B6C9), 1, 18, w - 2);
            String[] hd = {"Algorithm", "Calculated Hash Value (Digest)", "Status", "Speed"};
            for (int i = 0; i < 4; i++) {
                T.draw(g, hd[i], x[i] + 6, T.baseline(hf, 10), hf, c(0x222222), 0);
                if (i < 3) Aero.vline(g, c(0xBAC7D5), x[i] + cw[i] - 1, 3, 14);
            }
            Shape oldClip = g.getClip();
            g.clipRect(1, 19, w - 2, h - 20);
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int y = 19 + i * 20;
                if (i == selected) { g.setColor(c(0xDDEEFE)); g.fillRect(1, y, w - 2, 20); }
                else if (i % 2 == 1) { g.setColor(c(0xFAFCFE)); g.fillRect(1, y, w - 2, 20); }
                Aero.hline(g, c(0xF0F2F4), 1, y + 19, w - 2);
                float cy = y + 10;
                boolean sel = i == selected;
                T.draw(g, r.algo() + (r.primary() ? " *" : ""), x[0] + 6, T.baseline(uiB, cy), uiB, r.primary() ? c(0x003399) : c(0x444444), 0);
                String hs = T.middle(r.primary() ? monoB : mono, r.hash(), cw[1] - 14);
                T.draw(g, hs, x[1] + 6, T.baseline(mono, cy), r.primary() ? monoB : mono, r.primary() ? c(0x003399) : c(0x333333), 0);
                float sx = x[2] + 6;
                if (r.statusIcon() != null) { Ic.draw(g, r.statusIcon(), sx, cy - 6.5f, 13, r.statusColor()); sx += 17; }
                T.draw(g, T.ellipsize(r.primary() ? uiB : ui, r.status(), cw[2] - (sx - x[2]) - 6), sx, T.baseline(ui, cy), r.primary() ? uiB : ui, r.statusColor(), 0);
                T.draw(g, r.speed(), x[3] + 6, T.baseline(ui, cy), ui, c(0x444444), 0);
                for (int k = 0; k < 3; k++) Aero.vline(g, c(0xF0F2F4), x[k] + cw[k] - 1, y, 19);
            }
            g.setClip(oldClip);
            Aero.border(g, c(0x9CB0C4), 0, 0, w, h, 2, 1);
        }
    }

    // ================================================================ character diff
    static final class DiffView extends JComponent {
        private String exp = "", calc = "";

        DiffView() { setOpaque(false); }

        void set(String e, String c) {
            exp = e == null ? "" : e;
            calc = c == null ? "" : c;
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 50); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(c(0xF8FAFC));
            g.fill(Aero.rr(0, 0, w, h, 2));
            Aero.border(g, c(0xCBD5E1), 0, 0, w, h, 2, 1);
            Font lf = Fonts.ui(9.5f, true);
            int n = Math.max(1, Math.max(exp.length(), calc.length()));
            float avail = w - 8 - 64 - 10;
            float size = 10f, track = 0.8f;
            Font mf = Fonts.mono(size, false);
            float adv = T.width(mf, "0", 0);
            if (n * (adv + track) > avail) track = 0;
            if (n * adv > avail) { size = Math.max(6f, size * avail / (n * adv)); mf = Fonts.mono(size, false); adv = T.width(mf, "0", 0); }
            Font mfb = Fonts.mono(size, true);
            float step = adv + track;
            float x0 = 8 + 64;
            float rowH = (h - 4) / 3f;
            String[] lab = {"Expected:", "Calculated:", "Alignment:"};
            Color[] lc = {c(0x1E293B), c(0x1E293B), c(0x64748B)};
            for (int r = 0; r < 3; r++) {
                float cy = 2 + rowH * r + rowH / 2;
                T.draw(g, lab[r], 8, T.baseline(lf, cy), lf, lc[r], 0);
            }
            boolean haveExp = !exp.isEmpty();
            if (calc.isEmpty() && exp.isEmpty()) {
                T.draw(g, "\u2014", x0, T.baseline(mf, 2 + rowH / 2), mf, c(0x94A3B8), 0);
                T.draw(g, "\u2014", x0, T.baseline(mf, 2 + rowH * 1.5f), mf, c(0x94A3B8), 0);
                return;
            }
            float cyE = 2 + rowH / 2, cyC = 2 + rowH * 1.5f, cyA = 2 + rowH * 2.5f;
            for (int i = 0; i < n; i++) {
                float x = x0 + i * step;
                char e = i < exp.length() ? exp.charAt(i) : 0;
                char k = i < calc.length() ? calc.charAt(i) : 0;
                boolean same = haveExp && e != 0 && k != 0 && Character.toLowerCase(e) == Character.toLowerCase(k);
                boolean bad = haveExp && !calc.isEmpty() && !same;
                if (bad) {
                    g.setColor(c(0xFFD6D6));
                    g.fill(new Rectangle2D.Float(x - 0.5f, cyC - rowH / 2 + 1, adv + 1, rowH - 2));
                }
                if (e != 0) T.draw(g, String.valueOf(e), x, T.baseline(mf, cyE), bad ? mfb : mf, bad ? c(0xC00000) : c(0x0F172A), 0);
                if (k != 0) T.draw(g, String.valueOf(k), x, T.baseline(mf, cyC), bad ? mfb : mf, bad ? c(0xC00000) : c(0x003399), 0);
                if (haveExp && !calc.isEmpty()) {
                    String ind = same ? "|" : "\u00D7";
                    T.draw(g, ind, x + adv / 2 - T.width(mfb, ind, 0) / 2, T.baseline(mfb, cyA), mfb, same ? c(0x059669) : c(0xC00000), 0);
                }
            }
        }
    }
}
