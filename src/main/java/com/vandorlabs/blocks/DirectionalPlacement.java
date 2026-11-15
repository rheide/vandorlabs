package com.vandorlabs.blocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;

/** Shared player-relative placement policies for horizontal and six-direction blocks. */
public final class DirectionalPlacement {
    private DirectionalPlacement() {}
    static EnumFacing alongPlayer(EntityLivingBase placer) { return placer.getHorizontalFacing(); }
    static EnumFacing towardPlayer(EntityLivingBase placer) { return alongPlayer(placer).getOpposite(); }
    public static EnumFacing towardView(EntityLivingBase placer) {
        return EnumFacing.getFront(com.vandorlabs.placement.PlayerViewFacing.towardView(
                placer.rotationPitch, placer.getHorizontalFacing().getIndex()));
    }
}
