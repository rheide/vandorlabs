package com.vandorlabs.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

public final class SpaceFramePixelsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String[] levels = {"low", "medium", "high"};
        int[] gutters = {0, 1, 8};
        for (int level = 0; level < levels.length; level++) {
            BufferedImage image = ImageIO.read(new File("texture-packs/space-doors/"
                    + levels[level] + "/double_frame_metal.png"));
            int n = image.getWidth(), gutter = gutters[level];
            int[] source = image.getRGB(0, 0, n, n, null, 0, n);
            int[] fixed = source.clone();
            check(SpaceFramePixels.repair(fixed, n, n) == (gutter > 0), "known source recognition");
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                int sx = Math.max(gutter, Math.min(n - gutter - 1, x));
                int sy = Math.max(gutter, Math.min(n - gutter - 1, y));
                check(fixed[y * n + x] == source[sy * n + sx], "gutter extension / intact interior");
                if (sx != x || sy != y) check(fixed[y * n + x] >>> 24 == 255, "opaque steel edge");
            }
            int[] again = fixed.clone();
            check(!SpaceFramePixels.repair(again, n, n), "repair is idempotent");
            check(Arrays.equals(again, fixed), "second load unchanged");
            int[] custom = source.clone();
            custom[n * n / 2] ^= 1;
            int[] before = custom.clone();
            check(!SpaceFramePixels.repair(custom, n, n), "custom resource pack excluded");
            check(Arrays.equals(before, custom), "custom frame unchanged");
        }
        System.out.println("Space frame pixels PASS: all native tiers, exact gutter repair, intact interiors and resource-pack overrides");
    }
}
