package hashcheck;

import hashcheck.Aero.Ic;
import hashcheck.Aero.T;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static hashcheck.Aero.c;

/** Windows 7 flavoured menu bar, drop-downs and items. */
final class Menus {
    private Menus() { }

    static final Font F = Fonts.ui(11f, false);
    static final int GUTTER = 20;

    static final class AMenuBar extends JMenuBar {
        AMenuBar() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 22); }

        @Override protected void paintComponent(Graphics g) {
            g.setColor(c(0xF0F2F5));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(c(0xD1D5DB));
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
        }
    }

    static JPopupMenu popup(JPopupMenu p) {
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c(0x9BAAB9)), BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        p.setBackground(c(0xF4F6F9));
        p.setOpaque(true);
        p.setLightWeightPopupEnabled(false);
        return p;
    }

    private static int mnIndex(JMenuItem it) {
        int m = it.getMnemonic();
        if (m == 0) return -1;
        return it.getText().toLowerCase().indexOf(Character.toLowerCase((char) m));
    }

    private static String accel(JMenuItem it) {
        Object o = it.getClientProperty("accel");
        return o == null ? "" : o.toString();
    }

    private static Dimension sz(JMenuItem it, boolean gutter, boolean arrow) {
        float w = (gutter ? GUTTER : 8) + T.width(F, it.getText(), 0) + 28 + T.width(F, accel(it), 0) + (arrow ? 12 : 8);
        return new Dimension(Math.max(190, (int) Math.ceil(w)), 21);
    }

    private static void paintItem(Graphics g0, JMenuItem it, boolean gutter, boolean arrow, int kind) {
        Graphics2D g = Aero.aa(g0);
        int w = it.getWidth(), h = it.getHeight();
        boolean en = it.isEnabled();
        boolean hot = en && (it.isArmed() || it.isSelected() && it instanceof JMenu);
        if (kind == 1 && it.isSelected()) { /* check items use isSelected as the checked state */ hot = en && it.isArmed(); }
        if (kind == 2 && it.isSelected()) hot = en && it.isArmed();
        Color fg = !en ? c(0x9AA3AE) : hot ? Color.WHITE : c(0x111111);
        if (hot) {
            Aero.vgrad(g, Aero.rr(0, 0, w, h, 2), new float[]{0, 1}, 0x3B8BEB, 0x2D7BE5);
        }
        float cy = h / 2f;
        int tx = gutter ? GUTTER : 8;
        if (kind == 1 && it.isSelected()) Ic.draw(g, "check", 4, cy - 6, 12, fg);
        if (kind == 2 && it.isSelected()) {
            g.setColor(fg);
            g.fill(new java.awt.geom.Ellipse2D.Float(8, cy - 2.5f, 5, 5));
        }
        T.drawMn(g, it.getText(), mnIndex(it), tx, cy, F, fg);
        String a = accel(it);
        if (!a.isEmpty()) {
            float aw = T.width(F, a, 0);
            T.draw(g, a, w - aw - 10, T.baseline(F, cy), F, hot ? Color.WHITE : (en ? c(0x777777) : c(0xB0B6BE)), 0);
        }
        if (arrow) {
            g.setColor(fg);
            Path2D.Float p = new Path2D.Float();
            p.moveTo(w - 11, cy - 3.5);
            p.lineTo(w - 11, cy + 3.5);
            p.lineTo(w - 7, cy);
            p.closePath();
            g.fill(p);
        }
    }

    static class AItem extends JMenuItem {
        boolean gutter;

        AItem(String t, boolean gutter) {
            super(t);
            this.gutter = gutter;
            setOpaque(false);
            setBorder(null);
            setFocusPainted(false);
        }

        @Override public Dimension getPreferredSize() { return sz(this, gutter, false); }

        @Override protected void paintComponent(Graphics g) { paintItem(g, this, gutter, false, 0); }
    }

    static class ACheckItem extends JCheckBoxMenuItem {
        ACheckItem(String t, boolean sel) {
            super(t, sel);
            setOpaque(false);
            setBorder(null);
        }

        @Override public Dimension getPreferredSize() { return sz(this, true, false); }

        @Override protected void paintComponent(Graphics g) { paintItem(g, this, true, false, 1); }
    }

    static class ARadioItem extends JRadioButtonMenuItem {
        ARadioItem(String t, boolean sel) {
            super(t, sel);
            setOpaque(false);
            setBorder(null);
        }

        @Override public Dimension getPreferredSize() { return sz(this, true, false); }

        @Override protected void paintComponent(Graphics g) { paintItem(g, this, true, false, 2); }
    }

    /** Top-level (menu bar) menu or a cascading sub-menu. */
    static class AMenu extends JMenu {
        boolean gutter = true;

        AMenu(String t) {
            super(t);
            setOpaque(false);
            setBorder(null);
            setFocusPainted(false);
            popup(getPopupMenu());
        }

        @Override public Dimension getPreferredSize() {
            if (isTopLevelMenu()) return new Dimension((int) Math.ceil(T.width(F, getText(), 0)) + 16, 20);
            return sz(this, gutter, true);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g0) {
            if (!isTopLevelMenu()) { paintItem(g0, this, gutter, true, 0); return; }
            Graphics2D g = Aero.aa(g0);
            int w = getWidth(), h = getHeight();
            if (isSelected() || getModel().isRollover()) {
                g.setColor(c(0xE0EDF8));
                g.fill(Aero.rr(0, 0, w, h, 2));
                Aero.border(g, c(0x72B2E4), 0, 0, w, h, 2, 1);
            }
            T.drawMn(g, getText(), mnIndex(this), 8, h / 2f, F, c(0x111111));
        }
    }

    static class ASep extends JPopupMenu.Separator {
        @Override public Dimension getPreferredSize() { return new Dimension(10, 5); }

        @Override protected void paintComponent(Graphics g) {
            g.setColor(c(0xD0D6DE));
            g.fillRect(2, getHeight() / 2, getWidth() - 4, 1);
        }
    }
}
