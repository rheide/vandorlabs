package com.vandorlabs.blocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;

/** Shared player-relative placement policies for horizontal and six-direction blocks. */
final class DirectionalPlacement {
    private DirectionalPlacement() {}
    static EnumFacing alongPlayer(EntityLivingBase placer) { return placer.getHorizontalFacing(); }
    static EnumFacing towardPlayer(EntityLivingBase placer) { return EnumFacing.getFront(com.vandorlabs.render.ScreenFamilyPolicy.towardPlayer(alongPlayer(placer).getIndex())); }
    static EnumFacing towardView(EntityLivingBase placer) {
        return EnumFacing.getFront(com.vandorlabs.render.ScreenFamilyPolicy.towardView(alongPlayer(placer).getIndex(),placer.rotationPitch));
    }
}
