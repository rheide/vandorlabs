package com.vandorlabs.render;

import java.util.zip.CRC32;

/** Repairs the supplied pack's damaged opaque outer gutter, before mipmapping. */
public final class SpaceFramePixels {
    private SpaceFramePixels() {}

    public static boolean repair(int[] argb, int width, int height) {
        if (width != height || argb.length != width * height) return false;
        int gutter;
        long expected;
        if (width == 512) { gutter = 1; expected = 0x4766f359L; }
        else if (width == 1024) { gutter = 8; expected = 0x61ccd213L; }
        else return false;
        // Restrict this source-specific correction to the shipped artwork.
        // A resource pack with a different frame must keep all its own pixels.
        CRC32 crc = new CRC32();
        for (int pixel : argb) {
            crc.update(pixel >>> 24); crc.update(pixel >>> 16);
            crc.update(pixel >>> 8); crc.update(pixel);
        }
        if (crc.getValue() != expected) return false;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (x >= gutter && x < width - gutter && y >= gutter && y < height - gutter) continue;
            int sx = Math.max(gutter, Math.min(width - gutter - 1, x));
            int sy = Math.max(gutter, Math.min(height - gutter - 1, y));
            // Copy RGB AND alpha from intact steel. Merely setting alpha to 255
            // would expose the arbitrary RGB payload that caused these speckles.
            argb[y * width + x] = argb[sy * width + sx];
        }
        return true;
    }
}
