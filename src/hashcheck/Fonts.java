package hashcheck;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Font resolution. The design asks for 'Product Sans' (UI, falls back to 'Open Sans')
 * and 'JetBrains Mono' (data). Any .ttf/.otf dropped into a "fonts" folder next to the
 * application (or bundled on the classpath under /fonts) is registered automatically.
 */
final class Fonts {
    static String UI = Font.DIALOG;
    static String MONO = Font.MONOSPACED;
    private static final Map<String, Font> CACHE = new HashMap<>();

    private static final String[] BUNDLED = {
            "ProductSans-Regular.ttf", "ProductSans-Bold.ttf", "ProductSans-Medium.ttf",
            "OpenSans-Regular.ttf", "OpenSans-Bold.ttf", "OpenSans-SemiBold.ttf",
            "JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf", "JetBrainsMono-Medium.ttf"
    };

    static void init() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        File[] dirs = {new File("fonts"), new File(appDir(), "fonts")};
        for (File d : dirs) {
            File[] fs = d.listFiles((dir, n) -> {
                String l = n.toLowerCase();
                return l.endsWith(".ttf") || l.endsWith(".otf");
            });
            if (fs == null) continue;
            for (File f : fs) {
                try {
                    ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, f));
                } catch (Exception ignored) { }
            }
        }
        for (String n : BUNDLED) {
            try (InputStream in = Fonts.class.getResourceAsStream("/fonts/" + n)) {
                if (in != null) ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
            } catch (Exception ignored) { }
        }
        Set<String> fams = new HashSet<>(List.of(ge.getAvailableFontFamilyNames()));
        UI = pick(fams, Font.DIALOG, "Product Sans", "Open Sans", "Segoe UI", "Inter", "Noto Sans", "DejaVu Sans");
        MONO = pick(fams, Font.MONOSPACED, "JetBrains Mono", "Consolas", "DejaVu Sans Mono", "Cascadia Mono");
    }

    private static File appDir() {
        try {
            return new File(Fonts.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            return new File(".");
        }
    }

    private static String pick(Set<String> have, String fallback, String... want) {
        for (String w : want) if (have.contains(w)) return w;
        return fallback;
    }

    static Font ui(float px, boolean bold) {
        return get(UI, px, bold);
    }

    static Font mono(float px, boolean bold) {
        return get(MONO, px, bold);
    }

    private static Font get(String fam, float px, boolean bold) {
        String key = fam + "|" + px + "|" + bold;
        return CACHE.computeIfAbsent(key, k -> new Font(fam, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(px));
    }
}
