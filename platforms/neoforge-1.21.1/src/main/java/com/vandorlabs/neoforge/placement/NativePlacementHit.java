package com.vandorlabs.neoforge.placement;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.Vec3;

/** Released placement callbacks receive hit fractions relative to the original clicked cell. */
public final class NativePlacementHit {
 private NativePlacementHit(){}
 public static Vec3 hit(BlockPlaceContext context){var clicked=context.replacingClickedOnBlock()?context.getClickedPos():context.getClickedPos().relative(context.getClickedFace().getOpposite());return context.getClickLocation().subtract(clicked.getX(),clicked.getY(),clicked.getZ());}
}
