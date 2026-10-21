package com.vandorlabs.blocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;

/** Shared player-relative placement policies for horizontal and six-direction blocks. */
final class DirectionalPlacement {
    private DirectionalPlacement() {}
    static EnumFacing alongPlayer(EntityLivingBase placer) { return placer.getHorizontalFacing(); }
    static EnumFacing towardPlayer(EntityLivingBase placer) { return alongPlayer(placer).getOpposite(); }
    static EnumFacing towardView(EntityLivingBase placer) {
        return placer.rotationPitch > 45 ? EnumFacing.UP
                : placer.rotationPitch < -45 ? EnumFacing.DOWN : towardPlayer(placer);
    }
}
