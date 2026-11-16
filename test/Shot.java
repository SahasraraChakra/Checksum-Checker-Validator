package hashcheck;
import javax.swing.*; import java.awt.*; import java.awt.image.*; import javax.imageio.ImageIO; import java.io.File;
public class Shot {
  static void lay(Component c){ c.invalidate(); if(c instanceof Container k){ k.doLayout(); for(Component x:k.getComponents()) lay(x);} }
  static AeroFrame.Chrome chrome; static MainWindow mw;
  static void shot(String n) throws Exception {
    BufferedImage im = new BufferedImage(1000, 640, BufferedImage.TYPE_INT_ARGB);
    chrome.setSize(1000,640); lay(chrome); lay(chrome);
    Graphics2D g = im.createGraphics(); g.setColor(new Color(0x26558e)); g.fillRect(0,0,1000,640); chrome.paint(g); g.dispose();
    ImageIO.write(im, "png", new File("/tmp/"+n+".png"));
  }
  public static void main(String[] a) throws Exception {
    Fonts.init();
    SwingUtilities.invokeAndWait(() -> {
      mw = new MainWindow(new String[0]);
      chrome = new AeroFrame.Chrome(null, "Checksum Checker v2.4 (x64)", true);
      chrome.rounded = true;
      chrome.client.add(mw.clientPanel);
    });
    Thread.sleep(300);
    SwingUtilities.invokeAndWait(() -> { try { shot("s0"); } catch (Exception e) { e.printStackTrace(); } });
    String exp = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/tmp/h.txt"))).trim();
    SwingUtilities.invokeAndWait(() -> { mw.setFile("/tmp/sample-ubuntu.iso"); mw.setup.expected.setText(exp); chrome.title="Checksum Checker v2.4 (x64) - [/tmp/sample-ubuntu.iso]"; });
    SwingUtilities.invokeAndWait(() -> { try { shot("s1"); } catch (Exception e) { e.printStackTrace(); } });
    SwingUtilities.invokeAndWait(() -> { mw.autoSwitch=true; mw.startVerify(false); });
    Thread.sleep(150);
    SwingUtilities.invokeAndWait(() -> { try { mw.showTab(1); shot("s2run"); } catch (Exception e) { e.printStackTrace(); } });
    Thread.sleep(6000);
    SwingUtilities.invokeAndWait(() -> { try { shot("s2"); } catch (Exception e) { e.printStackTrace(); } });
    SwingUtilities.invokeAndWait(() -> { mw.setup.expected.setText(exp.substring(0,60)+"9999"); mw.startVerify(true); });
    Thread.sleep(500);
    SwingUtilities.invokeAndWait(() -> { try { shot("s3"); } catch (Exception e) { e.printStackTrace(); } });
    System.out.println("fonts "+Fonts.UI+" / "+Fonts.MONO+" engine "+MainWindow.Engine.indicator+" verdict "+mw.lastVerdict);
    System.exit(0);
  }
}
