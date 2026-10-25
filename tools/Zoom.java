import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
/** java tools/Zoom.java entrada.png saida.png x y w h escala */
public class Zoom { public static void main(String[] a) throws Exception {
  BufferedImage s = ImageIO.read(new File(a[0])); int x=Integer.parseInt(a[2]),y=Integer.parseInt(a[3]),w=Integer.parseInt(a[4]),h=Integer.parseInt(a[5]),k=Integer.parseInt(a[6]);
  BufferedImage d = new BufferedImage(w*k,h*k,BufferedImage.TYPE_INT_RGB); Graphics2D g=d.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  g.drawImage(s.getSubimage(x,y,w,h),0,0,w*k,h*k,null); ImageIO.write(d,"png",new File(a[1])); } }
