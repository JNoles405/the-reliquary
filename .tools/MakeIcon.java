import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates a multi-size Windows .ico of The Reliquary mark: the teal gem with a
 * white facet on the app's dark background — the same logo shown in the title bar.
 * Usage: java MakeIcon.java <output.ico>
 */
public class MakeIcon {
    static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws Exception {
        List<byte[]> pngs = new ArrayList<>();
        for (int sz : SIZES) pngs.add(render(sz));

        File out = new File(args[0]);
        out.getParentFile().mkdirs();
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            writeShortLE(o, 0);            // reserved
            writeShortLE(o, 1);            // type = icon
            writeShortLE(o, SIZES.length); // image count
            int offset = 6 + 16 * SIZES.length;
            for (int i = 0; i < SIZES.length; i++) {
                int sz = SIZES[i];
                byte[] png = pngs.get(i);
                o.writeByte(sz >= 256 ? 0 : sz); // width  (0 means 256)
                o.writeByte(sz >= 256 ? 0 : sz); // height (0 means 256)
                o.writeByte(0);                  // palette
                o.writeByte(0);                  // reserved
                writeShortLE(o, 1);              // color planes
                writeShortLE(o, 32);             // bits per pixel
                writeIntLE(o, png.length);
                writeIntLE(o, offset);
                offset += png.length;
            }
            for (byte[] png : pngs) o.write(png);
        }
        System.out.println("Wrote " + out.getAbsolutePath());
    }

    static byte[] render(int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = size / 256.0;

        g.setColor(new Color(0x0E, 0x14, 0x13)); // app background
        double arc = size * 0.22;
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, arc, arc));

        g.setColor(new Color(0x14, 0xB8, 0xA6)); // teal gem
        g.fill(diamond(new double[][]{{128, 70}, {192, 128}, {128, 200}, {64, 128}}, s));

        g.setColor(Color.WHITE); // facet
        g.fill(diamond(new double[][]{{128, 100}, {170, 128}, {128, 184}, {86, 128}}, s));

        g.dispose();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

    static Path2D diamond(double[][] pts, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0][0] * s, pts[0][1] * s);
        for (int i = 1; i < pts.length; i++) p.lineTo(pts[i][0] * s, pts[i][1] * s);
        p.closePath();
        return p;
    }

    static void writeShortLE(DataOutputStream o, int v) throws IOException {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
    }

    static void writeIntLE(DataOutputStream o, int v) throws IOException {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
        o.writeByte((v >> 16) & 0xFF);
        o.writeByte((v >> 24) & 0xFF);
    }
}
