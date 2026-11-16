package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;
import hashcheck.Widgets.AButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import static hashcheck.Aero.c;

/** Custom Aero window chrome (title bar, caption buttons, rounded border, edge resizing). */
final class AeroFrame extends JFrame {
    final Chrome chrome;

    AeroFrame(String title) {
        setUndecorated(true);
        chrome = new Chrome(this, title, true);
        setContentPane(chrome);
        setIconImage(appIcon());
        setTitle(title);
        setMinimumSize(new Dimension(860, 580));
        setSize(1000, 640);
        setLocationRelativeTo(null);
        try {
            if (GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                setBackground(new Color(0, 0, 0, 0));
                chrome.rounded = true;
            }
        } catch (Throwable ignored) { }
        ResizePane rp = new ResizePane(this, chrome);
        setGlassPane(rp);
        rp.setVisible(true);
    }

    void setTitleText(String t) {
        chrome.title = t;
        setTitle(t);
        chrome.bar.repaint();
    }

    JPanel client() { return chrome.client; }

    static Image appIcon() {
        BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Aero.aa(im.createGraphics());
        g.setColor(c(0x1D4ED8));
        g.fill(new Ellipse2D.Float(1, 1, 30, 30));
        Ic.draw(g, "verified_user", 5, 5, 22, Color.WHITE);
        g.dispose();
        return im;
    }

    // ================================================================ chrome
    static final class Chrome extends JPanel {
        final Window win;
        final boolean isFrame;
        String title;
        boolean rounded = false, maximized = false;
        Rectangle restore;
        final JPanel client = new JPanel(new BorderLayout());
        final TitleBar bar;

        Chrome(Window w, String title, boolean isFrame) {
            super(new BorderLayout());
            this.win = w;
            this.title = title;
            this.isFrame = isFrame;
            setOpaque(false);
            client.setOpaque(true);
            client.setBackground(c(0xF0F2F5));
            bar = new TitleBar(this);
            add(bar, BorderLayout.NORTH);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setBorder(BorderFactory.createEmptyBorder(0, 1, 1, 1));
            wrap.add(client);
            add(wrap, BorderLayout.CENTER);
            setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        }

        int arc() { return rounded && !maximized ? 6 : 0; }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            g.setColor(c(0xF0F2F5));
            g.fill(Aero.rr(0, 0, getWidth(), getHeight(), arc()));
        }

        @Override public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.clip(Aero.rr(0, 0, getWidth(), getHeight(), arc()));
            super.paint(g);
            g.dispose();
            Graphics2D b = Aero.aa(g0.create());
            Aero.border(b, c(0x5A7B9E), 0, 0, getWidth(), getHeight(), arc(), 1);
            b.dispose();
        }

        void toggleMax() {
            if (!(win instanceof JFrame f)) return;
            if (!maximized) {
                restore = f.getBounds();
                GraphicsConfiguration gc = f.getGraphicsConfiguration();
                Rectangle sb = gc.getBounds();
                Insets si = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                f.setBounds(sb.x + si.left, sb.y + si.top, sb.width - si.left - si.right, sb.height - si.top - si.bottom);
                maximized = true;
            } else {
                if (restore != null) f.setBounds(restore);
                maximized = false;
            }
            repaint();
        }
    }

    // ================================================================ title bar
    static final class TitleBar extends JPanel {
        final Chrome ch;
        final Cap min, max, close;
        private Point dragStart, winStart;

        TitleBar(Chrome ch) {
            this.ch = ch;
            setLayout(null);
            setOpaque(false);
            setPreferredSize(new Dimension(100, 28));
            min = new Cap(0);
            max = new Cap(1);
            close = new Cap(2);
            if (ch.isFrame) { add(min); add(max); }
            add(close);
            min.addActionListener(e -> ((JFrame) ch.win).setExtendedState(Frame.ICONIFIED));
            max.addActionListener(e -> ch.toggleMax());
            close.addActionListener(e -> ch.win.dispatchEvent(new WindowEvent(ch.win, WindowEvent.WINDOW_CLOSING)));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragStart = e.getLocationOnScreen();
                    winStart = ch.win.getLocation();
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart == null || ch.maximized) return;
                    Point p = e.getLocationOnScreen();
                    ch.win.setLocation(winStart.x + p.x - dragStart.x, winStart.y + p.y - dragStart.y);
                }

                @Override public void mouseClicked(MouseEvent e) {
                    if (ch.isFrame && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) ch.toggleMax();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override public void doLayout() {
            int x = getWidth() - 4;
            Cap[] order = {close, max, min};
            for (Cap c : order) {
                if (c.getParent() != this) continue;
                x -= 26;
                c.setBounds(x, 5, 26, 18);
                x -= 2;
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            Graphics2D gc = (Graphics2D) g.create();
            Aero.vgrad(gc, new Rectangle(0, 0, w, h), new float[]{0, .48f, .5f, 1}, 0xEBF1F8, 0xD8E4F2, 0xC7D8EB, 0xD8E4F2);
            gc.dispose();
            Aero.hline(g, c(0x9BB0C7), 0, h - 1, w);
            Ic.draw(g, "verified_user", 8, h / 2f - 7.5f, 15, c(0x0055EA));
            Font f = Fonts.ui(11.5f, true);
            float max = Math.max(0, getWidth() - 8 - 15 - 6 - (ch.isFrame ? 3 : 1) * 28 - 14);
            String t = T.ellipsize(f, ch.title, max);
            T.draw(g, t, 29, T.baseline(f, h / 2f), f, c(0x1E395B), -0.1f);
        }
    }

    static final class Cap extends JButton {
        final int kind;
        private boolean hover, press;

        Cap(int kind) {
            this.kind = kind;
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { press = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            Shape s = Aero.rr(0, 0, w, h, 2);
            Color glyph;
            if (kind == 2) {
                if (hover) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, press ? 0xE86A6A : 0xFDBABA, press ? 0xD62828 : 0xF77676, press ? 0xC81C1C : 0xE83232, press ? 0xE05050 : 0xF46C6C);
                else Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xFDE2E2, 0xF8B4B4, 0xEE7878, 0xF6A3A3);
                Aero.border(g, c(0xB87272), 0, 0, w, h, 2, 1);
                glyph = hover ? Color.WHITE : c(0x4C0000);
            } else {
                if (press) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xD2E3F5, 0xBFD6EE, 0xAFCBE9, 0xC4DAF0);
                else if (hover) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xFFFFFF, 0xF0F5FA, 0xDCEDF9, 0xEDF5FD);
                else Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xFFFFFF, 0xE2EAF4, 0xCCDBEC, 0xE0EAF5);
                Aero.border(g, hover ? c(0x557AA9) : c(0x8FA3BA), 0, 0, w, h, 2, 1);
                glyph = c(0x333333);
            }
            g.setColor(glyph);
            float cx = w / 2f, cy = h / 2f;
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            switch (kind) {
                case 0 -> g.fill(new Rectangle2D.Float(cx - 4, cy + 2, 8, 2));
                case 1 -> {
                    Chrome ch = (Chrome) SwingUtilities.getAncestorOfClass(Chrome.class, this);
                    if (ch != null && ch.maximized) {
                        g.draw(new Rectangle2D.Float(cx - 4, cy - 2, 6, 5));
                        g.fill(new Rectangle2D.Float(cx - 2, cy - 4.5f, 7, 1.6f));
                        g.fill(new Rectangle2D.Float(cx + 3.5f, cy - 4.5f, 1.6f, 5));
                    } else {
                        g.draw(new Rectangle2D.Float(cx - 4, cy - 3.2f, 8, 6.4f));
                        g.fill(new Rectangle2D.Float(cx - 4, cy - 3.2f, 8, 2));
                    }
                }
                default -> {
                    g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Float(cx - 3.2f, cy - 3.2f, cx + 3.2f, cy + 3.2f));
                    g.draw(new Line2D.Float(cx + 3.2f, cy - 3.2f, cx - 3.2f, cy + 3.2f));
                }
            }
        }
    }

    // ================================================================ edge resizing
    static final class ResizePane extends JComponent {
        private static final int E = 5;
        private final JFrame f;
        private final Chrome ch;
        private int dir;
        private Point start;
        private Rectangle startB;

        ResizePane(JFrame f, Chrome ch) {
            this.f = f;
            this.ch = ch;
            setOpaque(false);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { setCursor(Cursor.getPredefinedCursor(cursorFor(hit(e.getX(), e.getY())))); }

                @Override public void mousePressed(MouseEvent e) {
                    dir = hit(e.getX(), e.getY());
                    start = e.getLocationOnScreen();
                    startB = f.getBounds();
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (dir == 0 || start == null) return;
                    Point p = e.getLocationOnScreen();
                    int dx = p.x - start.x, dy = p.y - start.y;
                    Rectangle b = new Rectangle(startB);
                    Dimension min = f.getMinimumSize();
                    if ((dir & 1) != 0) { b.x += dx; b.width -= dx; }     // west
                    if ((dir & 2) != 0) b.width += dx;                     // east
                    if ((dir & 4) != 0) { b.y += dy; b.height -= dy; }     // north
                    if ((dir & 8) != 0) b.height += dy;                    // south
                    if (b.width < min.width) { if ((dir & 1) != 0) b.x -= min.width - b.width; b.width = min.width; }
                    if (b.height < min.height) { if ((dir & 4) != 0) b.y -= min.height - b.height; b.height = min.height; }
                    f.setBounds(b);
                }

                @Override public void mouseReleased(MouseEvent e) { dir = 0; }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int hit(int x, int y) {
            if (ch.maximized) return 0;
            int w = getWidth(), h = getHeight(), d = 0;
            if (x < E) d |= 1;
            if (x >= w - E) d |= 2;
            if (y < E) d |= 4;
            if (y >= h - E) d |= 8;
            return d;
        }

        private int cursorFor(int d) {
            return switch (d) {
                case 1 -> Cursor.W_RESIZE_CURSOR;
                case 2 -> Cursor.E_RESIZE_CURSOR;
                case 4 -> Cursor.N_RESIZE_CURSOR;
                case 8 -> Cursor.S_RESIZE_CURSOR;
                case 5 -> Cursor.NW_RESIZE_CURSOR;
                case 6 -> Cursor.NE_RESIZE_CURSOR;
                case 9 -> Cursor.SW_RESIZE_CURSOR;
                case 10 -> Cursor.SE_RESIZE_CURSOR;
                default -> Cursor.DEFAULT_CURSOR;
            };
        }

        /** Only the edge band captures the mouse; everything else falls through to the UI below. */
        @Override public boolean contains(int x, int y) { return hit(x, y) != 0 || dir != 0; }
    }

    // ================================================================ dialogs
    static final class AeroDialog extends JDialog {
        final Chrome chrome;
        boolean ok;

        AeroDialog(Window owner, String title, JComponent content, java.util.List<AButton> buttons, int w, int h) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            setUndecorated(true);
            chrome = new Chrome(this, title, false);
            setContentPane(chrome);
            try {
                if (GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                    setBackground(new Color(0, 0, 0, 0));
                    chrome.rounded = true;
                }
            } catch (Throwable ignored) { }
            JPanel body = chrome.client;
            content.setOpaque(true);
            content.setBackground(Color.WHITE);
            body.add(content, BorderLayout.CENTER);
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8)) {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(c(0xECEFF3));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(c(0xC4CBD4));
                    g.fillRect(0, 0, getWidth(), 1);
                }
            };
            bar.setOpaque(false);
            for (AButton b : buttons) bar.add(b);
            body.add(bar, BorderLayout.SOUTH);
            getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
            setSize(w, h);
            setLocationRelativeTo(owner);
        }

        static AButton btn(String text, Runnable r) {
            AButton b = new AButton(text, Widgets.St.T1, null, null).size(75, 24);
            b.bfont = Fonts.ui(11.5f, false);
            b.addActionListener(e -> r.run());
            return b;
        }

        static void message(Window owner, String title, String msg, String icon, Color iconColor) {
            AeroDialog[] d = new AeroDialog[1];
            AButton okb = btn("OK", () -> d[0].dispose());
            d[0] = new AeroDialog(owner, title, messageBody(msg, icon, iconColor), java.util.List.of(okb), 420, 170 + Math.max(0, msg.split("\n").length - 2) * 14);
            d[0].getRootPane().setDefaultButton(okb);
            d[0].setVisible(true);
        }

        static boolean confirm(Window owner, String title, String msg) {
            AeroDialog[] d = new AeroDialog[1];
            AButton yes = btn("Yes", () -> { d[0].ok = true; d[0].dispose(); });
            AButton no = btn("No", () -> d[0].dispose());
            d[0] = new AeroDialog(owner, title, messageBody(msg, "info", c(0x2B78E4)), java.util.List.of(yes, no), 420, 170);
            d[0].getRootPane().setDefaultButton(yes);
            d[0].setVisible(true);
            return d[0].ok;
        }

        static JComponent messageBody(String msg, String icon, Color col) {
            JPanel p = new JPanel(new BorderLayout(12, 0));
            p.setBorder(BorderFactory.createEmptyBorder(16, 18, 10, 18));
            JComponent ic = new JComponent() {
                @Override protected void paintComponent(Graphics g) { Ic.draw((Graphics2D) g, icon, 0, 0, 32, col); }
            };
            ic.setPreferredSize(new Dimension(32, 32));
            JPanel icw = new JPanel(new BorderLayout());
            icw.setOpaque(false);
            icw.add(ic, BorderLayout.NORTH);
            JTextArea t = new JTextArea(msg);
            t.setEditable(false);
            t.setLineWrap(true);
            t.setWrapStyleWord(true);
            t.setOpaque(false);
            t.setFont(Fonts.ui(12f, false));
            t.setForeground(c(0x1A1A1A));
            t.setFocusable(false);
            p.add(icw, BorderLayout.WEST);
            p.add(t, BorderLayout.CENTER);
            return p;
        }
    }
}
