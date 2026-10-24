package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A real installed block entity, or a logical control owned by that block entity.
 * Owners unregister themselves and their logical controls from setRemoved and
 * onChunkUnloaded; installed idle members never require a per-tick scan.
 */
public interface ModernChannelMember extends ChannelMember {
    BlockEntity channelBlockEntity();
    /** Runs after deferred registration while the installed identity is still active. */
    default void channelLoaded() { }

    @Override default boolean isChannelMemberActive() {
        BlockEntity owner = channelBlockEntity();
        return owner != null && !owner.isRemoved() && owner.getLevel() instanceof ServerLevel level
                && level.hasChunkAt(owner.getBlockPos()) && level.getBlockEntity(owner.getBlockPos()) == owner;
    }
}
