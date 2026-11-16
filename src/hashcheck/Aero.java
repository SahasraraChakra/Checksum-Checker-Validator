package hashcheck;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Painting helpers shared by all custom widgets. */
final class Aero {
    private Aero() { }

    static Color c(int rgb) { return new Color(rgb); }

    static Color a(int rgb, float alpha) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(alpha * 255));
    }

    static Graphics2D aa(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Vertical multi-stop gradient fill of a shape. */
    static void vgrad(Graphics2D g, Shape s, float[] f, int... cols) {
        Rectangle2D b = s.getBounds2D();
        Color[] cc = new Color[cols.length];
        for (int i = 0; i < cols.length; i++) cc[i] = new Color(cols[i]);
        g.setPaint(new LinearGradientPaint((float) b.getX(), (float) b.getY(),
                (float) b.getX(), (float) (b.getY() + Math.max(1, b.getHeight())), f, cc));
        g.fill(s);
    }

    static RoundRectangle2D rr(double x, double y, double w, double h, double arc) {
        return new RoundRectangle2D.Double(x, y, w, h, arc * 2, arc * 2);
    }

    /** 1px crisp border drawn inside the given bounds. */
    static void border(Graphics2D g, Color c, double x, double y, double w, double h, double arc, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        double o = width / 2.0;
        g.draw(rr(x + o, y + o, w - width, h - width, arc));
    }

    static void hline(Graphics2D g, Color c, int x, int y, int w) {
        g.setColor(c);
        g.fillRect(x, y, w, 1);
    }

    static void vline(Graphics2D g, Color c, int x, int y, int h) {
        g.setColor(c);
        g.fillRect(x, y, 1, h);
    }

    // ---------------------------------------------------------------- text
    static final class T {
        static final FontRenderContext FRC = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        private static final Map<Font, Font> FB = new HashMap<>();

        private static Font fb(Font f) {
            return FB.computeIfAbsent(f, k -> new Font(Font.DIALOG, k.getStyle(), 1).deriveFont(k.getSize2D()));
        }

        static float width(Font f, String s, float track) {
            if (s == null || s.isEmpty()) return 0;
            float w = 0;
            int i = 0;
            Font fb = null;
            while (i < s.length()) {
                boolean ok = f.canDisplay(s.codePointAt(i));
                int j = i;
                while (j < s.length() && f.canDisplay(s.codePointAt(j)) == ok) j += Character.charCount(s.codePointAt(j));
                Font ff = ok ? f : (fb == null ? (fb = fb(f)) : fb);
                w += (float) ff.getStringBounds(s, i, j, FRC).getWidth();
                i = j;
            }
            return w + track * s.codePointCount(0, s.length());
        }

        static float baseline(Font f, float cy) {
            LineMetrics lm = f.getLineMetrics("Ag", FRC);
            return cy + (lm.getAscent() - lm.getDescent()) / 2f;
        }

        static float draw(Graphics2D g, String s, float x, float y, Font f, Color col, float track) {
            if (s == null || s.isEmpty()) return 0;
            g.setColor(col);
            float x0 = x;
            Font fb = null;
            int i = 0;
            while (i < s.length()) {
                boolean ok = f.canDisplay(s.codePointAt(i));
                int j = i;
                while (j < s.length() && f.canDisplay(s.codePointAt(j)) == ok) j += Character.charCount(s.codePointAt(j));
                Font ff = ok ? f : (fb == null ? (fb = fb(f)) : fb);
                g.setFont(ff);
                String run = s.substring(i, j);
                if (track == 0) {
                    g.drawString(run, x, y);
                    x += (float) ff.getStringBounds(run, FRC).getWidth();
                } else {
                    for (int k = 0; k < run.length(); k++) {
                        String ch = run.substring(k, k + 1);
                        g.drawString(ch, x, y);
                        x += (float) ff.getStringBounds(ch, FRC).getWidth() + track;
                    }
                }
                i = j;
            }
            return x - x0;
        }

        /** Draw vertically centred on cy. */
        static float drawC(Graphics2D g, String s, float x, float cy, Font f, Color col) {
            return draw(g, s, x, baseline(f, cy), f, col, 0);
        }

        static String ellipsize(Font f, String s, float max) {
            if (width(f, s, 0) <= max) return s;
            String e = "\u2026";
            int n = s.length();
            while (n > 0 && width(f, s.substring(0, n) + e, 0) > max) n--;
            return s.substring(0, n) + e;
        }

        /** Middle-ellipsis ("abcd...wxyz") that fits into max pixels. */
        static String middle(Font f, String s, float max) {
            if (width(f, s, 0) <= max) return s;
            String e = "...";
            int keep = s.length();
            while (keep > 2) {
                keep--;
                int a = (keep + 1) / 2, b = keep / 2;
                String t = s.substring(0, a) + e + s.substring(s.length() - b);
                if (width(f, t, 0) <= max) return t;
            }
            return e;
        }

        /** Menu-style text with one underlined mnemonic character. */
        static float drawMn(Graphics2D g, String s, int mnIdx, float x, float cy, Font f, Color col) {
            float base = baseline(f, cy);
            float w = draw(g, s, x, base, f, col, 0);
            if (mnIdx >= 0 && mnIdx < s.length()) {
                float x1 = x + width(f, s.substring(0, mnIdx), 0);
                float x2 = x1 + width(f, s.substring(mnIdx, mnIdx + 1), 0);
                g.setColor(col);
                g.setStroke(new BasicStroke(1f));
                g.draw(new Line2D.Float(x1, base + 1.5f, x2, base + 1.5f));
            }
            return w;
        }
    }

    // ---------------------------------------------------------------- icons
    static final class Ic {
        private static final Map<String, String[]> DEF = new HashMap<>();
        private static final Map<String, Path2D.Float[]> CACHE = new HashMap<>();

        static {
            d("verified_user", "M12 2.5 L4.5 5.5 V11 C4.5 16 7.7 20 12 21.5 C16.3 20 19.5 16 19.5 11 V5.5 Z M8.7 11.8 L11 14 L15.5 9.2", "");
            d("folder", "M3 6.5 H9.5 L11.5 8.5 H21 V19 H3 Z", "");
            d("folder_open", "M3 19 V5.5 H9.5 L11.5 7.5 H19.5 V10.5 M3 19 L6 10.5 H22.5 L19.5 19 Z", "");
            d("drive_file_move", "M3 19 V5.5 H9.5 L11.5 7.5 H21 V19 Z M8 13 H16 M13.5 10.5 L16 13 L13.5 15.5", "");
            d("content_paste", "R5 4.5 14 17 1.5 M9 2.5 H15 V6.5 H9 Z", "");
            d("content_copy", "R8.5 7.5 11 13.5 1.5 M15.5 7.5 V4.5 H4.5 V16 H8.5", "");
            d("delete", "M5 7 H19 M9 7 V4 H15 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10 10.5 V16.5 M14 10.5 V16.5", "");
            d("tune", "M4 7 H12 M16 7 H20 O14 7 2 M4 12 H8 M12 12 H20 O10 12 2 M4 17 H12 M16 17 H20 O14 17 2", "");
            d("fingerprint", "M6 9 C7 5.5 9.5 4 12 4 C14.5 4 17 5.5 18 9 M8.5 11 C8.5 8.5 10 7.5 12 7.5 C14 7.5 15.5 8.5 15.5 11 V13 C15.5 16 14.5 18 13 20 M11 11 V14 C11 16.5 10.5 18 9.5 20 M12 13 V15 C12 17 12.3 18.5 12.8 20 M4.5 14 C4.5 12 4.5 11 5 10", "");
            d("memory", "R6.5 6.5 11 11 1 R9.5 9.5 5 5 0.5 M9.5 3.5 V6.5 M12 3.5 V6.5 M14.5 3.5 V6.5 M9.5 17.5 V20.5 M12 17.5 V20.5 M14.5 17.5 V20.5 M3.5 9.5 H6.5 M3.5 12 H6.5 M3.5 14.5 H6.5 M17.5 9.5 H20.5 M17.5 12 H20.5 M17.5 14.5 H20.5", "");
            d("disc_full", "O12 12 9 O12 12 2", "");
            d("file_open", "M6 2.5 H14 L19 7.5 V21.5 H6 Z M14 2.5 V7.5 H19 M9 15 H15 M12.5 12.5 L15 15 L12.5 17.5", "");
            d("check_circle", "O12 12 9.2 M7.8 12.3 L10.7 15.2 L16.3 9.2", "");
            d("error", "O12 12 9.2 M12 7.2 V13 M12 16 V16.2", "");
            d("warning", "M12 3.5 L21.5 20 H2.5 Z M12 9.5 V14.5 M12 17.3 V17.5", "");
            d("info", "O12 12 9.2 M12 11 V17 M12 7.5 V7.7", "");
            d("play_arrow", "", "M8 5 V19 L19 12 Z");
            d("stop", "", "M6 6 H18 V18 H6 Z");
            d("pause", "", "M7 5 H10.5 V19 H7 Z M13.5 5 H17 V19 H13.5 Z");
            d("compare_arrows", "M4 8.5 H19 M15.5 5 L19 8.5 L15.5 12 M20 15.5 H5 M8.5 12 L5 15.5 L8.5 19", "");
            d("clear_all", "M4 7 H20 M6 12 H20 M8 17 H20", "");
            d("description", "M6 2.5 H14 L19 7.5 V21.5 H6 Z M14 2.5 V7.5 H19 M9 12.5 H16 M9 16 H16", "");
            d("list_alt", "R3.5 4.5 17 15 1 M7 9 H8.5 M10.5 9 H17 M7 12 H8.5 M10.5 12 H17 M7 15 H8.5 M10.5 15 H17", "");
            d("save", "M4.5 3.5 H16.5 L19.5 6.5 V20.5 H4.5 Z M8 3.5 V8.5 H15 V3.5 M7.5 20.5 V13.5 H16.5 V20.5", "");
            d("arrow_back", "M20 12 H5 M11 6 L5 12 L11 18", "");
            d("swap_horiz", "M3.5 8 H17 M13.5 4.5 L17 8 L13.5 11.5 M20.5 16 H7 M10.5 12.5 L7 16 L10.5 19.5", "");
            d("check", "M5 12.5 L9.5 17 L19 7.5", "");
            d("close", "M6 6 L18 18 M18 6 L6 18", "");
        }

        private static void d(String n, String stroke, String fill) {
            DEF.put(n, new String[]{stroke, fill});
        }

        private static Path2D.Float parse(String d) {
            Path2D.Float p = new Path2D.Float(Path2D.WIND_NON_ZERO);
            if (d.isEmpty()) return p;
            Matcher m = Pattern.compile("[A-Za-z]|-?\\d*\\.?\\d+").matcher(d);
            java.util.List<String> tk = new java.util.ArrayList<>();
            while (m.find()) tk.add(m.group());
            int i = 0;
            float cx = 0, cy = 0, sx = 0, sy = 0;
            char cmd = 'M';
            while (i < tk.size()) {
                String t = tk.get(i);
                if (Character.isLetter(t.charAt(0))) { cmd = t.charAt(0); i++; if (cmd == 'Z' || cmd == 'z') { p.closePath(); cx = sx; cy = sy; } continue; }
                switch (cmd) {
                    case 'M' -> { cx = f(tk, i); cy = f(tk, i + 1); i += 2; p.moveTo(cx, cy); sx = cx; sy = cy; cmd = 'L'; }
                    case 'L' -> { cx = f(tk, i); cy = f(tk, i + 1); i += 2; p.lineTo(cx, cy); }
                    case 'H' -> { cx = f(tk, i); i++; p.lineTo(cx, cy); }
                    case 'V' -> { cy = f(tk, i); i++; p.lineTo(cx, cy); }
                    case 'C' -> { p.curveTo(f(tk, i), f(tk, i + 1), f(tk, i + 2), f(tk, i + 3), cx = f(tk, i + 4), cy = f(tk, i + 5)); i += 6; }
                    case 'O' -> { float x = f(tk, i), y = f(tk, i + 1), r = f(tk, i + 2); i += 3; p.append(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false); }
                    case 'R' -> { float x = f(tk, i), y = f(tk, i + 1), w = f(tk, i + 2), h = f(tk, i + 3), r = f(tk, i + 4); i += 5; p.append(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2), false); }
                    default -> i++;
                }
            }
            return p;
        }

        private static float f(java.util.List<String> t, int i) { return Float.parseFloat(t.get(i)); }

        private static Path2D.Float[] get(String n) {
            return CACHE.computeIfAbsent(n, k -> {
                String[] s = DEF.get(k);
                if (s == null) return new Path2D.Float[]{new Path2D.Float(), new Path2D.Float()};
                return new Path2D.Float[]{parse(s[0]), parse(s[1])};
            });
        }

        static void draw(Graphics2D g0, String name, float x, float y, float size, Color col) {
            draw(g0, name, x, y, size, col, 0);
        }

        /** rot = radians, used by the spinner. */
        static void draw(Graphics2D g0, String name, float x, float y, float size, Color col, double rot) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            float s = size / 24f;
            g.translate(x, y);
            g.scale(s, s);
            if (rot != 0) g.rotate(rot, 12, 12);
            g.setColor(col);
            float sw = Math.max(1.0f, size / 12f) / s;
            g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "verified" -> {
                    Path2D.Float p = new Path2D.Float();
                    int n = 24;
                    for (int i = 0; i < n; i++) {
                        double ang = Math.PI * 2 * i / n;
                        double r = (i % 2 == 0) ? 10.3 : 9.0;
                        double px = 12 + r * Math.cos(ang), py = 12 + r * Math.sin(ang);
                        if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
                    }
                    p.closePath();
                    g.draw(p);
                    Path2D.Float ck = parse("M8 12.3 L10.7 15 L16.2 9.3");
                    g.draw(ck);
                }
                case "sync" -> {
                    g.draw(new Arc2D.Float(5, 5, 14, 14, 50, 250, Arc2D.OPEN));
                    Path2D.Float h = parse("M17.5 2.5 V7.8 H12.2");
                    g.translate(0, 0);
                    g.draw(h);
                }
                default -> {
                    Path2D.Float[] p = get(name);
                    g.draw(p[0]);
                    g.fill(p[1]);
                }
            }
            g.dispose();
        }
    }
}
