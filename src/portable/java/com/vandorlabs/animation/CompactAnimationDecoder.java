package com.vandorlabs.animation;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/** Decoder for Vandor Labs' versioned, base-relative animation format. */
public final class CompactAnimationDecoder {
    private static final int MAGIC = 0x564C5441; // VLTA
    private static final int VERSION = 2;
    // Prevent a corrupt resource from turning a small header into a huge image
    // allocation. Real Vandor Labs strips are several orders of magnitude smaller.
    private static final long MAX_STRIP_PIXELS = 64L * 1024L * 1024L;

    private CompactAnimationDecoder() { }

    public static final class Result {
        public final BufferedImage strip;
        public final int frameCount;

        private Result(BufferedImage strip, int frameCount) {
            this.strip = strip;
            this.frameCount = frameCount;
        }
    }

    public static Result decode(BufferedImage base, InputStream encoded) throws IOException {
        if (base == null || encoded == null) throw new NullPointerException();
        try (DataInputStream in = new DataInputStream(encoded)) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                throw new IllegalArgumentException("unsupported compact animation header");
            }
            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int frames = in.readUnsignedShort();
            int declaredPayload = in.readInt();
            long stripPixels = (long) width * height * frames;
            long expectedPayload = (long) (frames - 1) * width * height * 3L;
            if (width != base.getWidth() || height != base.getHeight() || frames < 2
                    || stripPixels > MAX_STRIP_PIXELS || expectedPayload > Integer.MAX_VALUE
                    || declaredPayload != expectedPayload) {
                throw new IllegalArgumentException("animation dimensions do not match base");
            }
            byte[] delta = new byte[(int) expectedPayload];
            try (InflaterInputStream inflated = new InflaterInputStream(in)) {
                int cursor = 0;
                while (cursor < delta.length) {
                    int count = inflated.read(delta, cursor, delta.length - cursor);
                    if (count < 0) throw new IllegalArgumentException("truncated animation payload");
                    if (count > 0) cursor += count;
                }
                if (inflated.read() >= 0) {
                    throw new IllegalArgumentException("oversized animation payload");
                }
            }

            BufferedImage strip = new BufferedImage(width, height * frames, BufferedImage.TYPE_INT_ARGB);
            int[] previous = base.getRGB(0, 0, width, height, null, 0, width);
            strip.setRGB(0, 0, width, height, previous, 0, width);
            int cursor = 0;
            int[] pixels = new int[width * height];
            for (int frame = 1; frame < frames; frame++) {
                for (int pixel = 0; pixel < pixels.length; pixel++) {
                    int original = previous[pixel];
                    int red = (((original >> 16) & 255) + (delta[cursor++] & 255)) & 255;
                    int green = (((original >> 8) & 255) + (delta[cursor++] & 255)) & 255;
                    int blue = ((original & 255) + (delta[cursor++] & 255)) & 255;
                    pixels[pixel] = 0xFF000000 | red << 16 | green << 8 | blue;
                }
                strip.setRGB(0, frame * height, width, height, pixels, 0, width);
                int[] reusable = previous; previous = pixels; pixels = reusable;
            }
            return new Result(strip, frames);
        }
    }
}
