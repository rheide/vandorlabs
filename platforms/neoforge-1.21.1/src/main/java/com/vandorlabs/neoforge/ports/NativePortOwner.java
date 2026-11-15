package com.vandorlabs.neoforge.ports;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A settings session exposes choices and the exact installed tile, never a second resource owner. */
public interface NativePortOwner {
    BlockEntity portBlockEntity();
    NativePortKind portKind();
    NativePortData.Configuration configuration();
    boolean configure(NativePortData.Configuration choices);
    boolean inert();
    default BlockPos getBlockPos(){return portBlockEntity().getBlockPos();}
}
