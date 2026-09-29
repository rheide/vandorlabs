package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;

/** Converts an underside click to the model's front-to-back ceiling slot. */
final class CeilingPlacement {
    private CeilingPlacement() { }

    static int position(EnumFacing facing,float hitX,float hitZ) {
        float local=facing==EnumFacing.SOUTH?1-hitZ
                :facing==EnumFacing.EAST?1-hitX
                :facing==EnumFacing.WEST?hitX:hitZ;
        return local<1F/3F?0:local<2F/3F?1:2;
    }
}
