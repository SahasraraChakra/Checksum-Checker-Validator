package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;
import hashcheck.Widgets.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import static hashcheck.Aero.c;

/** Chrome shared by both tabs: toolbar, tab strip and the multi-pane status bar. */
final class Shell {
    private Shell() { }

    // ================================================================ toolbar
    static final class Toolbar extends JPanel {
        final AButton open, folder, paste, copy, clear;
        final ACombo preset;
        final Pulse pulse = new Pulse();
        final Lbl indicator;

        Toolbar(String indicatorText) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            open = tool("Open File", "folder_open", c(0xC28C00), "Open File (Ctrl+O)");
            folder = tool("Folder", "drive_file_move", c(0xD9A726), "Open Target Folder (Ctrl+F)");
            paste = tool("Paste", "content_paste", c(0x4F5B66), "Paste Hash (Ctrl+V)");
            copy = tool("Copy", "content_copy", c(0x4F5B66), "Copy Result (Ctrl+C)");
            clear = tool("Clear", "delete", c(0xB83838), "Clear All");
            preset = new ACombo(new String[]{"SHA-256 (Default)", "All Algorithms", "MD5 Legacy", "SHA-512 High Entropy"},
                    Fonts.ui(10.5f, false), c(0x0F172A), 20);
            Lbl pl = new Lbl("Preset:", Fonts.ui(11f, false), c(0x555555));
            JPanel pw = Widgets.hbox(4, pl, preset);
            pw.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            JPanel left = Widgets.hbox(4, open, folder, Widgets.vsep(c(0xCBD3DC), 14), paste, copy, clear, Widgets.vsep(c(0xCBD3DC), 14), pw);
            add(left, BorderLayout.WEST);
            indicator = new Lbl(indicatorText, Fonts.mono(10.5f, false), c(0x334155));
            JPanel right = Widgets.hbox(8, pulse, indicator);
            right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
            add(right, BorderLayout.EAST);
        }

        private static AButton tool(String t, String icon, Color ic, String tip) {
            AButton b = new AButton(t, St.TOOL, icon, ic).font(Fonts.ui(11f, false)).icon(14);
            b.setToolTipText(tip);
            return b;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 28); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            Aero.vgrad(g, new Rectangle(0, 0, getWidth(), getHeight()), new float[]{0, 1}, 0xFAFAFB, 0xE9ECEF);
            Aero.hline(g, c(0xC4CBD2), 0, getHeight() - 1, getWidth());
        }
    }

    static final class Pulse extends JComponent {
        float phase;

        Pulse() { setPreferredSize(new Dimension(8, 8)); setMaximumSize(new Dimension(8, 8)); setOpaque(false); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            float a = 0.5f + 0.5f * (float) Math.sin(phase);
            g.setColor(Aero.a(0x15B63A, 0.45f + 0.55f * a));
            g.fill(new Ellipse2D.Float(0, 0, 8, 8));
        }
    }

    // ================================================================ tab strip
    static final class TabStrip extends JComponent {
        private final String[] titles = {"Tab 1: Target File & Configuration", "Tab 2: Calculated Hashes & Stream Verification"};
        private final String[] icons = {"tune", "verified"};
        private int sel = 0;
        private String badge;
        private Color bFg = c(0x1E293B), bBg = c(0xE2E8F0), bBd = c(0xCBD5E1);
        private final Rectangle[] rects = new Rectangle[2];
        final java.util.List<java.util.function.IntConsumer> listeners = new java.util.ArrayList<>();

        TabStrip() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    for (int i = 0; i < 2; i++) if (rects[i] != null && rects[i].contains(e.getPoint())) {
                        for (var l : listeners) l.accept(i);
                    }
                }
            });
        }

        void select(int i) { sel = i; repaint(); }

        void setBadge(String t, Color fg, Color bg, Color bd) {
            badge = t;
            if (fg != null) { bFg = fg; bBg = bg; bBd = bd; }
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 27); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(c(0xE2E6EA));
            g.fillRect(0, 0, w, h);
            Aero.hline(g, c(0x758696), 0, h - 1, w);
            int x = 6;
            for (int i = 0; i < 2; i++) {
                boolean act = i == sel;
                Font f = Fonts.ui(11f, act);
                float isz = act && i == 1 ? 14 : 13;
                float tw = T.width(f, titles[i], 0);
                float bw = 0;
                Font bf = Fonts.ui(9.5f, true);
                String bt = i == 1 ? badge : null;
                if (bt != null) bw = 4 + T.width(bf, bt, 0) + 8 + 2;
                int tabW = (int) Math.ceil(10 + isz + 4 + tw + bw + 10 + 2);
                Rectangle r = new Rectangle(x, 3, tabW, h - 3);
                rects[i] = r;
                Shape s = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height + 6, 6, 6);
                Graphics2D gc = (Graphics2D) g.create();
                gc.clipRect(r.x - 1, r.y, r.width + 2, act ? r.height : r.height - 1 + 0);
                gc.setColor(act ? Color.WHITE : c(0xD5DBE1));
                gc.fill(s);
                Aero.border(gc, act ? c(0x758696) : c(0x9CA8B4), r.x, r.y, r.width, r.height + 6, 3, 1);
                gc.dispose();
                if (act) { g.setColor(Color.WHITE); g.fillRect(r.x + 1, h - 1, r.width - 2, 1); }
                float cy = r.y + (r.height - 1) / 2f + (act ? 0 : 0.5f);
                float tx = r.x + 1 + 10;
                Color ic = act ? (i == 1 ? c(0x0055EA) : c(0x0B4A9E)) : c(0x555555);
                if (i == 0 && !act) ic = c(0x555555);
                Ic.draw(g, icons[i], tx, cy - isz / 2f, isz, ic);
                tx += isz + 4;
                T.draw(g, titles[i], tx, T.baseline(f, cy), f, act ? c(0x003FA8) : c(0x555555), 0);
                tx += tw;
                if (bt != null) {
                    tx += 4;
                    float cw = T.width(bf, bt, 0) + 8;
                    g.setColor(bBg);
                    g.fill(Aero.rr(tx, cy - 7, cw, 14, 3));
                    Aero.border(g, bBd, tx, cy - 7, cw, 14, 3, 1);
                    T.draw(g, bt, tx + 4, T.baseline(bf, cy), bf, bFg, 0);
                }
                x += tabW + 2;
            }
        }
    }

    // ================================================================ status bar
    static final class StatusBar extends JComponent {
        String text = "Ready", icon = "check", algo = "", threads = "", buffer = "", os = "";
        Color iconColor = c(0x008000), textColor = c(0x2D3748);
        boolean num = true, spin;
        double spinAngle;

        StatusBar() { setOpaque(false); }

        void set(String icon, Color col, String text) {
            this.icon = icon;
            this.iconColor = col;
            this.text = text;
            spin = "sync".equals(icon);
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 23); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(c(0xE8ECEF));
            g.fillRect(0, 0, w, h);
            Aero.hline(g, c(0xC4CBD2), 0, 0, w);
            int[] fixed = {208, 128, 112, 96};
            int gap = 3, x = w - 4;
            int[] px = new int[5], pw = new int[5];
            for (int i = 3; i >= 0; i--) { x -= fixed[i]; px[i + 1] = x; pw[i + 1] = fixed[i]; x -= gap; }
            px[0] = 4;
            pw[0] = Math.max(60, x + gap - 4 - gap);
            int y = 2, ph = h - 4;
            Font f = Fonts.ui(11f, false);
            float cy = y + ph / 2f + 0.5f;
            for (int i = 0; i < 5; i++) {
                g.setColor(c(0xECEFF3));
                g.fillRect(px[i], y, pw[i], ph);
                Aero.hline(g, c(0x9AA3AC), px[i], y, pw[i]);
                Aero.vline(g, c(0x9AA3AC), px[i], y, ph);
                Aero.hline(g, Color.WHITE, px[i], y + ph - 1, pw[i]);
                Aero.vline(g, Color.WHITE, px[i] + pw[i] - 1, y, ph);
            }
            float tx = px[0] + 7;
            Ic.draw(g, icon, tx, cy - 6, 12, iconColor, spin ? spinAngle : 0);
            tx += 16;
            T.draw(g, T.ellipsize(f, text, pw[0] - 30), tx, T.baseline(f, cy), f, textColor, 0);
            String[] s = {algo, threads, buffer};
            for (int i = 0; i < 3; i++)
                T.draw(g, T.ellipsize(f, s[i], pw[i + 1] - 14), px[i + 1] + 7, T.baseline(f, cy), f, c(0x2D3748), 0);
            Font fb = Fonts.ui(11f, true);
            T.draw(g, os, px[4] + 7, T.baseline(fb, cy), fb, c(0x0055D4), 0);
            if (num) {
                float nw = T.width(f, "NUM", 0);
                T.draw(g, "NUM", px[4] + pw[4] - nw - 7, T.baseline(f, cy), f, c(0x666666), 0);
            }
        }
    }
}
