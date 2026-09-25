package com.vandorlabs.blocks;

/** Three four-pixel-wide panel positions; zero preserves existing worlds. */
public final class PanelDepth {
    private PanelDepth() {}

    public static int fromHit(float coordinate) {
        return coordinate < 1F / 3F ? 1 : coordinate > 2F / 3F ? 2 : 0;
    }

    public static int start(int depth) {
        return depth == 1 ? 0 : depth == 2 ? 12 : 6;
    }

    public static int offset(int depth) {
        return start(depth) - 6;
    }
}
