package com.vandorlabs.armor;

/** Aspect-preserving center crop within the visible region of each vanilla armor face. */
public final class ArmorMaterialSkin {
    public static final int WIDTH = 256, HEIGHT = 128;

    @FunctionalInterface
    public interface Pixels {
        int at(int x, int y);
    }

    // Vanilla ModelBiped cuboid faces: head, torso, arms and legs, in 64x32 UV units.
    private static final int[][] FACES = {
        {8,0,8,8},{16,0,8,8},{0,8,8,8},{8,8,8,8},{16,8,8,8},{24,8,8,8},
        {20,16,8,4},{28,16,8,4},{16,20,4,12},{20,20,8,12},{28,20,4,12},{32,20,8,12},
        {44,16,4,4},{48,16,4,4},{40,20,4,12},{44,20,4,12},{48,20,4,12},{52,20,4,12},
        {4,16,4,4},{8,16,4,4},{0,20,4,12},{4,20,4,12},{8,20,4,12},{12,20,4,12}
    };
    public static int[] bake(Pixels source, int width, int height, Pixels mask, int maskWidth, int maskHeight) {
        if (width <= 0 || height <= 0 || maskWidth <= 0 || maskHeight <= 0) {
            throw new IllegalArgumentException("Invalid armor material image size");
        }
        int[] skin = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                skin[y * WIDTH + x] = mask.at(x * maskWidth / WIDTH, y * maskHeight / HEIGHT) & 0xFF000000;
            }
        }
        for (int[] face : FACES) {
            int left = face[0] * 4, top = face[1] * 4;
            int right = left + face[2] * 4, bottom = top + face[3] * 4;
            int x0 = right, y0 = bottom, x1 = left, y1 = top;
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    if ((skin[y * WIDTH + x] >>> 24) == 0) continue;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
            if (x0 >= x1 || y0 >= y1) continue;
            double scale = Math.max((double)(x1 - x0) / width, (double)(y1 - y0) / height);
            double centerX = (x0 + x1) / 2.0, centerY = (y0 + y1) / 2.0;
            for (int y = top; y < bottom; y++) {
                int sy = clamp((int)((y + .5 - centerY) / scale + height / 2.0), height);
                for (int x = left; x < right; x++) {
                    int sx = clamp((int)((x + .5 - centerX) / scale + width / 2.0), width);
                    int index = y * WIDTH + x;
                    skin[index] = (skin[index] & 0xFF000000) | (source.at(sx, sy) & 0xFFFFFF);
                }
            }
        }
        return skin;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private ArmorMaterialSkin() {}
}
