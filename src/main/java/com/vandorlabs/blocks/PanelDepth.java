package com.vandorlabs.blocks;

/** Three four-pixel-wide panel positions; zero preserves existing worlds. */
public final class PanelDepth {
    private PanelDepth() {}

    public static int fromHit(float coordinate) {
        return com.vandorlabs.render.FlatPanelPlacement.depth(coordinate);
    }

    /** Convert a world-space click to the local front-to-back panel coordinate. */
    public static int fromHit(net.minecraft.util.EnumFacing facing, float hitX, float hitZ) {
        return com.vandorlabs.render.FlatPanelPlacement.depth(facing.getIndex(),hitX,hitZ);
    }

    public static int start(int depth) {
        return com.vandorlabs.render.FlatPanelPlacement.near(depth);
    }

    public static int offset(int depth) {
        return start(depth) - 6;
    }
}
