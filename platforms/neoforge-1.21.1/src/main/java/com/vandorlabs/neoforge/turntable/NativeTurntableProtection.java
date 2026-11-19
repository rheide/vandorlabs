package com.vandorlabs.neoforge.turntable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** An owner or failed envelope remains installed until its contents can be restored. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeTurntableProtection {
    public static boolean retained(Level level, BlockPos pos) {
        if (NativeTurntableBindings.block == null || !(level instanceof ServerLevel server)) return false;
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockState(pos).is(NativeTurntableBindings.block)
                && chunk.getBlockEntities().get(pos) instanceof NativeTurntableEntity owner && owner.protectsContents();
    }

    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel server && retained(server, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (NativeTurntableBindings.block != null) event.getAffectedBlocks().removeIf(pos -> retained(event.getLevel(), pos));
    }

    private NativeTurntableProtection() {}
}
