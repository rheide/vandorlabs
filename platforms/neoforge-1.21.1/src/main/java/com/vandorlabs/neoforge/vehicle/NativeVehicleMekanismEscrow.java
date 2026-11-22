package com.vandorlabs.neoforge.vehicle;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The explicitly supported finite Mekanism owners share the Vehicle family's physical hold. */
public final class NativeVehicleMekanismEscrow {
    private static final String CUBE_TYPE = "mekanism.common.tile.TileEntityEnergyCube";
    private static final Set<String> ENERGY_TIERS = Set.of("basic_energy_cube", "advanced_energy_cube",
        "elite_energy_cube", "ultimate_energy_cube");

    static boolean cube(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("mekanism") && ENERGY_TIERS.contains(id.getPath());
    }

    static boolean cube(BlockEntity tile) {
        return tile.getClass().getName().equals(CUBE_TYPE) && cube(tile.getBlockState());
    }

    public static boolean tickHeld(BlockEntity tile) {
        return tile.getLevel() instanceof ServerLevel world && (cube(tile) || NativeVehicleMekanismTank.supports(tile))
            && NativeVehicleTransfer.holdsTerrain(world, tile.getBlockPos());
    }

    /** Sided handlers may survive both a physical ownership hold and the original tile. */
    public static boolean blocksSided(BlockEntity tile) {
        if (!cube(tile) && !NativeVehicleMekanismTank.supports(tile)) return false;
        if (tile.isRemoved()) return true;
        if (!(tile.getLevel() instanceof ServerLevel world)) return false;
        var pos = tile.getBlockPos();
        if (NativeVehicleTransfer.holdsTerrain(world, pos)) return true;
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null || chunk.getBlockEntities().get(pos) != tile;
    }

    private NativeVehicleMekanismEscrow() {}
}
