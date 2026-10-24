package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;

/** Converts an underside click to the model's front-to-back ceiling slot. */
final class CeilingPlacement {
    private CeilingPlacement() { }

    static int position(EnumFacing facing,float hitX,float hitZ) {
        return com.vandorlabs.render.ScreenFamilyPolicy.ceilingPosition(facing.getIndex(),hitX,hitZ);
    }
}
