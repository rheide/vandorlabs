package com.vandorlabs.neoforge.vehicle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Resolve the optional public security API only when an installed Mekanism block is captured. */
final class NativeVehicleMekanismAccess {
    private static final MethodHandle CAN_ACCESS = lookup();

    private static MethodHandle lookup() {
        try {
            var service = Class.forName("mekanism.api.security.IBlockSecurityUtils");
            var method = MethodType.methodType(boolean.class, UUID.class, Level.class, BlockPos.class, BlockState.class, BlockEntity.class);
            return MethodHandles.publicLookup().findVirtual(service, "canAccess", method)
                .bindTo(service.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    static void require(UUID actor, Level world, BlockPos pos, BlockState state, BlockEntity tile) {
        if (CAN_ACCESS == null || actor == null) throw new IllegalArgumentException("Cannot verify Mekanism vehicle access");
        boolean allowed;
        try {
            allowed = (boolean)CAN_ACCESS.invokeExact(actor, world, pos, state, tile);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalArgumentException("Cannot verify Mekanism vehicle access", failure);
        }
        if (!allowed) throw new IllegalArgumentException("Mekanism security refused access to the block at " + pos.toShortString());
    }

    private NativeVehicleMekanismAccess() {}
}
