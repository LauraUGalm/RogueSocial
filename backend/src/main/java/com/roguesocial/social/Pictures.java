package com.roguesocial.social;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Turns an upload into the one picture that is kept: shrunk, and saved as a JPEG. The original
 * file is never stored, so whatever else it carried (location data from a phone camera, a
 * second image hidden in it) is gone too.
 */
final class Pictures {

    /** Profile pictures are cut to a square this many pixels across. */
    static final int PROFILE_SIDE = 256;
    /** Gallery pictures are shrunk to fit within this many pixels each way. */
    static final int GALLERY_SIDE = 1024;

    private static final Set<String> FORMATS = Set.of("jpeg", "png", "webp");
    /**
     * The most pixels an upload may claim. A small file can say it is 50,000 pixels square and
     * take gigabytes to open; the size is read from the header and checked before decoding.
     */
    private static final long MAX_PIXELS = 50_000_000;
    /** The page's background, behind any transparent parts, since JPEG has no transparency. */
    private static final Color BACKGROUND = new Color(0x12, 0x12, 0x18);

    private Pictures() {
    }

    /** A {@code side} x {@code side} JPEG, cut from the middle of the picture. */
    static byte[] square(InputStream upload, int side) {
        BufferedImage image = read(upload);
        int crop = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - crop) / 2;
        int y = (image.getHeight() - crop) / 2;
        int out = Math.min(side, crop);
        return jpeg(image, x, y, crop, crop, out, out);
    }

    /** A JPEG no more than {@code side} pixels wide or tall, the same shape as the original. */
    static byte[] fit(InputStream upload, int side) {
        BufferedImage image = read(upload);
        double scale = Math.min(1.0, (double) side / Math.max(image.getWidth(), image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return jpeg(image, 0, 0, image.getWidth(), image.getHeight(), w, h);
    }

    private static BufferedImage read(InputStream upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(upload)) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("That isn't a picture we can read: use JPEG, PNG or WebP");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
                    throw new IllegalArgumentException("Pictures can be JPEG, PNG or WebP");
                }
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new IllegalArgumentException("That picture is too many pixels across");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("That picture couldn't be read");
        }
    }

    /** Draws the part of {@code image} at x, y, w, h into a w2 x h2 JPEG. */
    private static byte[] jpeg(BufferedImage image, int x, int y, int w, int h, int w2, int h2) {
        BufferedImage out = new BufferedImage(w2, h2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w2, h2, x, y, x + w, y + h, BACKGROUND, null);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream os = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.setOutput(os);
            writer.write(null, new IIOImage(out, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save the picture", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
