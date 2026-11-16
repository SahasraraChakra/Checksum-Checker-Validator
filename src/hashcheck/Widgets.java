package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import static hashcheck.Aero.c;

/** Vista / Windows 7 style widgets, all custom painted. */
final class Widgets {
    private Widgets() { }

    static boolean kbd = false;

    static {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof KeyEvent ke && ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_TAB) kbd = true;
            else if (e instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) kbd = false;
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    // ================================================================= label
    static class Lbl extends JComponent {
        String text, icon;
        Font font;
        Color fg, iconColor;
        float iconSize = 13, track = 0;
        int align = SwingConstants.LEFT;
        boolean hoverUnderline;
        private boolean hover;

        Lbl(String text, Font font, Color fg) {
            this.text = text;
            this.font = font;
            this.fg = fg;
            setOpaque(false);
        }

        Lbl icon(String name, Color col, float size) { icon = name; iconColor = col; iconSize = size; return this; }

        Lbl align(int a) { align = a; return this; }

        Lbl track(float t) { track = t; return this; }

        Lbl link(Runnable r) {
            hoverUnderline = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { r.run(); }
            });
            return this;
        }

        void set(String t) {
            if (!java.util.Objects.equals(t, text)) { text = t; revalidate(); repaint(); }
        }

        void setColor(Color col) { fg = col; repaint(); }

        void setIcon(String n, Color col) { icon = n; iconColor = col; repaint(); }

        @Override public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            float w = (icon != null ? iconSize + 4 : 0) + T.width(font, text, track);
            LineMetricsH h = new LineMetricsH(font);
            return new Dimension((int) Math.ceil(w) + 1, Math.max(Math.round(h.h), icon != null ? (int) iconSize : 0));
        }

        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            float cy = getHeight() / 2f;
            float x = 0;
            float avail = getWidth();
            float iw = icon != null ? iconSize + 4 : 0;
            String s = T.ellipsize(font, text, Math.max(0, avail - iw));
            float tw = T.width(font, s, track);
            float total = iw + tw;
            if (align == SwingConstants.RIGHT) x = avail - total;
            else if (align == SwingConstants.CENTER) x = (avail - total) / 2f;
            if (icon != null) { Ic.draw(g, icon, x, cy - iconSize / 2f, iconSize, iconColor); x += iw; }
            T.draw(g, s, x, T.baseline(font, cy), font, fg, track);
            if (hoverUnderline && hover) {
                g.setColor(fg);
                g.fill(new Rectangle2D.Float(x, T.baseline(font, cy) + 1.5f, tw, 1));
            }
        }
    }

    private static final class LineMetricsH {
        final float h;
        LineMetricsH(Font f) {
            java.awt.font.LineMetrics lm = f.getLineMetrics("Ag", T.FRC);
            h = lm.getAscent() + lm.getDescent() + lm.getLeading();
        }
    }

    // ================================================================= button
    enum St { T1, T2, VERIFY, PRIMARY, TOOL }

    static class AButton extends JButton {
        final St st;
        String icon;
        Color iconColor;
        float iconSize = 13;
        Font bfont;
        Color fg;
        Color borderOverride;
        int padX = 10, minW = 0, minH = 24;
        private boolean hover, press;

        AButton(String text, St st, String icon, Color iconColor) {
            super(text);
            this.st = st;
            this.icon = icon;
            this.iconColor = iconColor;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            bfont = Fonts.ui(11f, st == St.PRIMARY || st == St.VERIFY);
            fg = st == St.T2 ? c(0x1A1A1A) : st == St.TOOL ? c(0x222222) : c(0x1E293B);
            if (st == St.VERIFY) fg = Color.WHITE;
            if (st == St.PRIMARY) fg = c(0x003380);
            if (st == St.TOOL) { padX = 6; minH = 22; }
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { press = false; repaint(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        AButton font(Font f) { bfont = f; revalidate(); return this; }

        AButton fg(Color col) { fg = col; return this; }

        AButton size(int minW, int minH) { this.minW = minW; this.minH = minH; revalidate(); return this; }

        AButton pad(int px) { padX = px; revalidate(); return this; }

        AButton icon(float sz) { iconSize = sz; revalidate(); return this; }

        AButton border(Color col) { borderOverride = col; return this; }

        void setIconName(String n, Color col) { icon = n; iconColor = col; repaint(); }

        @Override public void setText(String t) { super.setText(t); revalidate(); repaint(); }

        private float contentW() {
            String t = getText();
            return (icon != null ? iconSize + (t == null || t.isEmpty() ? 0 : 4) : 0) + T.width(bfont, t == null ? "" : t, 0);
        }

        @Override public Dimension getPreferredSize() {
            int w = (int) Math.ceil(contentW()) + padX * 2 + 2;
            return new Dimension(Math.max(minW, w), minH);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            boolean en = isEnabled();
            boolean ro = hover && en && !press;
            boolean pr = (press && hover || getModel().isPressed()) && en;
            double arc = st == St.T1 || st == St.VERIFY ? 2 : 3;
            Color border;
            Color textC = fg;
            Color icn = iconColor;
            if (st == St.TOOL) {
                if (en && (hover || pr)) {
                    Shape s = Aero.rr(0, 0, w, h, 2);
                    if (pr) Aero.vgrad(g, s, new float[]{0, 1}, 0xD6EBFA, 0xBCDDF4);
                    else Aero.vgrad(g, s, new float[]{0, 1}, 0xF0F7FD, 0xD8EDFA);
                    Aero.border(g, pr ? c(0x5B9FCF) : c(0x7BB7E2), 0, 0, w, h, 2, 1);
                }
            } else {
                Shape s = Aero.rr(0, 0, w, h, arc);
                if (st == St.VERIFY && en) {
                    g.setColor(Aero.a(0x0B4CB2, 0.16f));
                    g.fill(Aero.rr(0, 2, w, h, arc));
                    g.setColor(Aero.a(0x0B4CB2, 0.12f));
                    g.fill(Aero.rr(1, 3, w - 2, h, arc));
                }
                if (!en) {
                    Aero.vgrad(g, s, new float[]{0, 1}, 0xF5F5F5, 0xE6E6E6);
                    border = c(0xADB2B5);
                    textC = c(0x8A8A8A);
                    icn = c(0xA0A0A0);
                } else if (st == St.VERIFY) {
                    if (pr) Aero.vgrad(g, s, new float[]{0, 1}, 0x0947A6, 0x073D8E);
                    else if (ro) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0x3D89F3, 0x206CD9, 0x1057C2, 0x0F4EB0);
                    else Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0x2A79E8, 0x155DC6, 0x094CB2, 0x08439E);
                    border = ro ? c(0x063070) : c(0x06347A);
                } else if (st == St.PRIMARY) {
                    if (pr) Aero.vgrad(g, s, new float[]{0, 1}, 0xCCE2F7, 0xB2D5F5);
                    else if (ro) Aero.vgrad(g, s, new float[]{0, .5f, 1}, 0xF0F6FF, 0xD5E7FC, 0xADD1F8);
                    else Aero.vgrad(g, s, new float[]{0, .5f, 1}, 0xE9F2FE, 0xC4DCFA, 0x9BC5F6);
                    border = ro ? c(0x175DA4) : c(0x2872BD);
                } else if (st == St.T2) {
                    if (pr) Aero.vgrad(g, s, new float[]{0, 1}, 0xCCE2F7, 0xB2D5F5);
                    else if (ro) Aero.vgrad(g, s, new float[]{0, .5f, 1}, 0xF4F9FD, 0xD7EBFC, 0xBDDCFA);
                    else Aero.vgrad(g, s, new float[]{0, .5f, 1}, 0xFFFFFF, 0xEAECEF, 0xDCE0E5);
                    border = ro || pr ? c(0x3884C7) : c(0x828E9A);
                    if (ro) textC = c(0x003366);
                } else {
                    if (pr) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xCDE9FB, 0xBFE5FC, 0xA1D8F8, 0x87C6F1);
                    else if (ro) Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xECF6FD, 0xDAEFFC, 0xBEE5FC, 0xA9DBF7);
                    else Aero.vgrad(g, s, new float[]{0, .49f, .5f, 1}, 0xF8F9FA, 0xECEEF1, 0xDFE2E6, 0xD3D7DD);
                    border = pr ? c(0x276896) : ro ? c(0x3886BC) : c(0x8B98A6);
                }
                if (borderOverride != null && en && !ro && !pr) border = borderOverride;
                Aero.border(g, border, 0, 0, w, h, arc, 1);
                if (en) {
                    if (pr && st != St.VERIFY) {
                        g.setColor(Aero.a(0x000000, 0.18f));
                        g.fillRect(2, 1, w - 4, 1);
                        g.fillRect(1, 2, 1, h - 4);
                    } else {
                        g.setColor(Aero.a(0xFFFFFF, st == St.VERIFY ? 0.5f : 0.85f));
                        g.fillRect(2, 1, w - 4, 1);
                    }
                }
            }
            if (!en && st == St.TOOL) { textC = c(0x8A8A8A); icn = c(0xA0A0A0); }
            // content
            String t = getText() == null ? "" : getText();
            float cw = contentW();
            float x = (w - cw) / 2f;
            float cy = h / 2f;
            if (icon != null) {
                Ic.draw(g, icon, x, cy - iconSize / 2f, iconSize, icn);
                x += iconSize + (t.isEmpty() ? 0 : 4);
            }
            if (st == St.VERIFY && en) T.draw(g, t, x, T.baseline(bfont, cy) + 1, bfont, Aero.a(0x000000, 0.4f), 0);
            T.draw(g, t, x, T.baseline(bfont, cy), bfont, textC, 0);
            if (isFocusOwner() && kbd && en) {
                g.setColor(Aero.a(0x000000, 0.55f));
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{1f, 1f}, 0f));
                g.drawRect(3, 3, w - 7, h - 7);
            }
        }
    }

    // ================================================================= group box
    static class GroupBox extends JPanel {
        final JPanel body = new JPanel(new BorderLayout());
        private final Lbl legend;
        private JComponent legendRight;
        Color borderC = c(0xC5CDD7), fill = Color.WHITE;
        float bw = 1;
        final Insets pad;
        int legendX = 10;

        GroupBox(String icon, String title, Color iconColor, Font legendFont, Color legendColor, Insets pad) {
            this.pad = pad;
            legend = new Lbl(title, legendFont, legendColor);
            if (icon != null) legend.icon(icon, iconColor, 13);
            legend.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
            body.setOpaque(false);
            setOpaque(false);
            setLayout(new LayoutManager() {
                @Override public void addLayoutComponent(String n, Component comp) { }
                @Override public void removeLayoutComponent(Component comp) { }
                @Override public Dimension preferredLayoutSize(Container p) {
                    Dimension b = body.getPreferredSize();
                    int lh = legendH();
                    return new Dimension(b.width + pad.left + pad.right + 2, lh + pad.top + b.height + pad.bottom + 1);
                }
                @Override public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }
                @Override public void layoutContainer(Container p) {
                    int lh = legendH();
                    Dimension ld = legend.getPreferredSize();
                    legend.setBounds(legendX, (lh - ld.height) / 2, ld.width + 10, ld.height);
                    if (legendRight != null) {
                        Dimension rd = legendRight.getPreferredSize();
                        legendRight.setBounds(p.getWidth() - rd.width - legendX, Math.max(0, (lh - rd.height) / 2), rd.width, rd.height);
                    }
                    body.setBounds(pad.left + 1, lh + pad.top, p.getWidth() - pad.left - pad.right - 2,
                            p.getHeight() - lh - pad.top - pad.bottom - 1);
                }
            });
            add(legend);
            add(body);
        }

        private int legendH() {
            int h = legend.getPreferredSize().height;
            if (legendRight != null) h = Math.max(h, legendRight.getPreferredSize().height);
            return Math.max(14, h);
        }

        void setLegendRight(JComponent c) {
            legendRight = c;
            add(c);
        }

        void setLegend(String s) { legend.set(s); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            int top = legendH() / 2;
            Shape box = Aero.rr(0, top, w, h - top, 3);
            g.setColor(fill);
            g.fill(box);
            Area clip = new Area(new Rectangle(0, 0, w, h));
            Rectangle lb = legend.getBounds();
            clip.subtract(new Area(new Rectangle(lb.x, 0, lb.width, legendH())));
            if (legendRight != null) {
                Rectangle rb = legendRight.getBounds();
                clip.subtract(new Area(new Rectangle(rb.x - 4, 0, rb.width + 8, legendH())));
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(clip);
            Aero.border(g2, borderC, 0, top, w, h - top, 3, bw);
            g2.dispose();
        }
    }

    // ================================================================= text fields
    static class FieldBorder extends AbstractBorder {
        final Insets in;

        FieldBorder(Insets in) { this.in = in; }

        @Override public Insets getBorderInsets(Component c) { return (Insets) in.clone(); }

        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(in.top, in.left, in.bottom, in.right);
            return i;
        }

        @Override public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = Aero.aa(g0);
            boolean f = c.isFocusOwner() || (c instanceof JComboBox<?> cb && (cb.isPopupVisible() || cb.isFocusOwner()));
            Aero.border(g, f ? Aero.c(0x2563EB) : Aero.c(0x84A1BF), x, y, w, h, 2, 1);
            if (f) Aero.border(g, Aero.a(0x2563EB, 0.4f), x + 1, y + 1, w - 2, h - 2, 1, 1);
            else {
                g.setColor(Aero.a(0x000000, 0.10f));
                g.fillRect(x + 1, y + 1, w - 2, 1);
                g.fillRect(x + 1, y + 2, 1, h - 3);
            }
        }
    }

    static class AField extends JTextField {
        AField(String text, Font f, int padL, int padR) {
            super(text);
            setFont(f);
            setForeground(c(0x0F172A));
            setBackground(Color.WHITE);
            setOpaque(true);
            setBorder(new FieldBorder(new Insets(2, padL, 2, padR)));
            setSelectionColor(c(0x3399FF));
            setSelectedTextColor(Color.WHITE);
            setCaretColor(Color.BLACK);
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }
    }

    static class AArea extends JTextArea {
        AArea(String text, Font f) {
            super(text);
            setFont(f);
            setForeground(c(0x0F172A));
            setBackground(Color.WHITE);
            setLineWrap(true);
            setWrapStyleWord(false);
            setBorder(new FieldBorder(new Insets(3, 5, 3, 5)));
            setSelectionColor(c(0x3399FF));
            setSelectedTextColor(Color.WHITE);
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }
    }

    // ================================================================= combo
    static class ACombo extends JComboBox<String> {
        final int fixedH;
        private final Font cf;

        ACombo(String[] items, Font f, Color fg, int h) {
            super(items);
            fixedH = h;
            cf = f;
            setFont(f);
            setForeground(fg);
            setBackground(Color.WHITE);
            setOpaque(true);
            setFocusable(true);
            setBorder(new FieldBorder(new Insets(1, 5, 1, 0)));
            setUI(new BasicComboBoxUI() {
                @Override protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override protected void paintComponent(Graphics g0) {
                            Graphics2D g = Aero.aa(g0);
                            int cx = getWidth() / 2 + 1, cy = getHeight() / 2;
                            g.setColor(c(0x6B7280));
                            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g.draw(new Path2D.Float() {{ moveTo(cx - 3.5, cy - 1.5); lineTo(cx, cy + 2); lineTo(cx + 3.5, cy - 1.5); }});
                        }
                    };
                    b.setBorder(null);
                    b.setContentAreaFilled(false);
                    b.setFocusable(false);
                    b.setPreferredSize(new Dimension(18, 10));
                    return b;
                }

                @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

                @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                    super.paintCurrentValue(g, bounds, false);
                }

                @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
                    BasicComboPopup p = new BasicComboPopup(comboBox);
                    p.setBorder(BorderFactory.createLineBorder(c(0x84A1BF)));
                    return p;
                }
            });
            setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                    super.getListCellRendererComponent(l, v, i, sel, false);
                    setFont(cf);
                    setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                    if (i >= 0 && sel) { setBackground(c(0xCCE8FF)); setForeground(Color.BLACK); }
                    else { setBackground(Color.WHITE); setForeground(i < 0 ? ACombo.this.getForeground() : c(0x111111)); }
                    return this;
                }
            });
        }

        @Override public Dimension getPreferredSize() {
            float w = 0;
            for (int i = 0; i < getItemCount(); i++) w = Math.max(w, T.width(cf, getItemAt(i), 0));
            return new Dimension((int) w + 5 + 18 + 14, fixedH);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    }

    // ================================================================= checkbox / radio
    private static class BoxIcon implements Icon {
        final boolean radio, checked;

        BoxIcon(boolean radio, boolean checked) { this.radio = radio; this.checked = checked; }

        @Override public int getIconWidth() { return 12; }

        @Override public int getIconHeight() { return 12; }

        @Override public void paintIcon(Component cmp, Graphics g0, int x, int y) {
            Graphics2D g = Aero.aa(g0);
            boolean en = cmp.isEnabled();
            Color bc = checked ? c(0x0055EA) : c(0x6B7280);
            if (!en) bc = c(0xB5BBC4);
            Shape s = radio ? new Ellipse2D.Float(x + .5f, y + .5f, 11, 11) : new Rectangle2D.Float(x + .5f, y + .5f, 11, 11);
            g.setColor(checked ? (en ? c(0x0055EA) : c(0xB5BBC4)) : Color.WHITE);
            g.fill(s);
            g.setColor(bc);
            g.setStroke(new BasicStroke(1f));
            g.draw(s);
            if (checked) {
                g.setColor(Color.WHITE);
                if (radio) g.fill(new Ellipse2D.Float(x + 3.6f, y + 3.6f, 4.8f, 4.8f));
                else {
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(x + 2.8, y + 6.2);
                    p.lineTo(x + 5.0, y + 8.4);
                    p.lineTo(x + 9.2, y + 3.8);
                    g.draw(p);
                }
            }
        }
    }

    static JCheckBox check(String text, boolean sel, Font f, Color fg) {
        JCheckBox b = new JCheckBox(text, sel);
        style(b, false, f, fg);
        return b;
    }

    static JRadioButton radio(String text, boolean sel, Font f, Color fg) {
        JRadioButton b = new JRadioButton(text, sel);
        style(b, true, f, fg);
        return b;
    }

    private static void style(AbstractButton b, boolean radio, Font f, Color fg) {
        b.setIcon(new BoxIcon(radio, false));
        b.setSelectedIcon(new BoxIcon(radio, true));
        b.setFont(f);
        b.setForeground(fg);
        b.setOpaque(false);
        b.setFocusPainted(false);
        b.setIconTextGap(4);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    // ================================================================= progress
    static class AProgress extends JComponent {
        private double value;
        boolean paused;
        final int h;

        AProgress(int h) { this.h = h; setOpaque(false); }

        void setValue(double v) { value = Math.max(0, Math.min(1, v)); repaint(); }

        double getValue() { return value; }

        @Override public Dimension getPreferredSize() { return new Dimension(100, h); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), hh = getHeight();
            g.setColor(c(0xE2E5E8));
            g.fill(Aero.rr(0, 0, w, hh, 1));
            int fw = (int) Math.round((w - 2) * value);
            if (fw > 0) {
                Shape fs = new Rectangle2D.Float(1, 1, fw, hh - 2);
                if (paused) Aero.vgrad(g, fs, new float[]{0, .48f, .5f, 1}, 0xFFE58A, 0xF2C230, 0xE3B010, 0xF0C020);
                else Aero.vgrad(g, fs, new float[]{0, .48f, .5f, 1}, 0x4DE469, 0x17B83D, 0x0CA130, 0x16BA3C);
                g.setColor(paused ? c(0xB88A00) : c(0x088C26));
                g.fillRect(fw, 1, 1, hh - 2);
            }
            g.setColor(Aero.a(0x000000, 0.12f));
            g.fillRect(1, 1, w - 2, 1);
            Aero.border(g, c(0xA8B2BC), 0, 0, w, hh, 1, 1);
        }
    }

    // ================================================================= chip
    static class Chip extends JComponent {
        String text, icon;
        Color fg, bg, bd, iconColor;
        Font font;
        int padX = 6, padY = 1;
        float iconSize = 11;

        Chip(Font f) { font = f; setOpaque(false); }

        Chip set(String text, Color fg, Color bg, Color bd, String icon, Color ic) {
            this.text = text; this.fg = fg; this.bg = bg; this.bd = bd; this.icon = icon; this.iconColor = ic;
            revalidate();
            repaint();
            return this;
        }

        @Override public Dimension getPreferredSize() {
            float w = T.width(font, text == null ? "" : text, 0) + (icon != null ? iconSize + 4 : 0) + padX * 2 + 2;
            return new Dimension((int) Math.ceil(w), (int) (new LineMetricsH(font).h) + padY * 2 + 2);
        }

        @Override protected void paintComponent(Graphics g0) {
            if (text == null) return;
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            if (bg != null) { g.setColor(bg); g.fill(Aero.rr(0, 0, w, h, 2)); }
            if (bd != null) Aero.border(g, bd, 0, 0, w, h, 2, 1);
            float x = padX + 1;
            if (icon != null) { Ic.draw(g, icon, x, h / 2f - iconSize / 2f, iconSize, iconColor); x += iconSize + 4; }
            T.draw(g, text, x, T.baseline(font, h / 2f), font, fg, 0);
        }
    }

    // ================================================================= misc
    static JComponent vsep(Color col, int h) {
        JComponent s = new JComponent() {
            @Override protected void paintComponent(Graphics g) { g.setColor(col); g.fillRect(0, 0, 1, getHeight()); }
        };
        s.setPreferredSize(new Dimension(1, h));
        s.setMinimumSize(new Dimension(1, h));
        s.setMaximumSize(new Dimension(1, h));
        return s;
    }

    static JPanel hbox(int gap, Component... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (i > 0 && gap > 0) p.add(Box.createHorizontalStrut(gap));
            ((JComponent) cs[i]).setAlignmentY(0.5f);
            p.add(cs[i]);
        }
        return p;
    }

    /** Vertical layout; leftover space goes to the component flagged "flex", else spread between children. */
    static class VFlex implements LayoutManager {
        final int gap, maxGap;

        VFlex(int gap, int maxGap) { this.gap = gap; this.maxGap = maxGap; }

        @Override public void addLayoutComponent(String n, Component c) { }

        @Override public void removeLayoutComponent(Component c) { }

        private int used(Container p) {
            int n = 0, h = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) { n++; h += c.getPreferredSize().height; }
            return h + gap * Math.max(0, n - 1);
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            Insets in = p.getInsets();
            int w = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) w = Math.max(w, c.getPreferredSize().width);
            return new Dimension(w + in.left + in.right, used(p) + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int avail = p.getHeight() - in.top - in.bottom;
            int extra = Math.max(0, avail - used(p));
            java.util.List<Component> vis = new java.util.ArrayList<>();
            Component flex = null;
            for (Component c : p.getComponents()) if (c.isVisible()) {
                vis.add(c);
                if (Boolean.TRUE.equals(((JComponent) c).getClientProperty("flex"))) flex = c;
            }
            int n = vis.size();
            double g = gap;
            int flexExtra = 0;
            if (n > 1 && extra > 0) {
                double add = (double) extra / (n - 1);
                if (maxGap > 0 && gap + add > maxGap) add = Math.max(0, maxGap - gap);
                if (flex != null) {
                    g = gap + Math.min(add, 6);
                    flexExtra = (int) (extra - (g - gap) * (n - 1));
                } else g = gap + add;
            }
            double y = in.top;
            for (Component c : vis) {
                int h = c.getPreferredSize().height;
                if (c == flex) h += flexExtra;
                c.setBounds(in.left, (int) Math.round(y), p.getWidth() - in.left - in.right, h);
                y += h + g;
            }
        }
    }
}
