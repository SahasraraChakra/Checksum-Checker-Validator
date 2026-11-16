package hashcheck;

import javax.swing.*;

public final class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        Fonts.init();
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow(args);
            w.frame.setVisible(true);
        });
    }
}
