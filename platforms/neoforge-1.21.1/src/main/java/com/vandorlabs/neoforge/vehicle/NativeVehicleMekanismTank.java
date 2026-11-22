package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

/** Single-cell Mekanism tanks have one physical fluid owner, including when stacked. */
final class NativeVehicleMekanismTank {
    static final String TANKS = "fluid_tanks";
    private static final Set<String> TIERS = Set.of("basic_fluid_tank", "advanced_fluid_tank",
        "elite_fluid_tank", "ultimate_fluid_tank");

    static boolean supports(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("mekanism") && TIERS.contains(id.getPath());
    }

    static boolean supports(BlockEntity tile) {
        return supports(tile.getBlockState()) && tile.getClass().getName().equals("mekanism.common.tile.TileEntityFluidTank");
    }

    static void requireOwner(CompoundTag raw, UUID controllerOwner) {
        var security = raw.getCompound("component_security");
        if (security.contains("owner") && (!security.hasUUID("owner") || !security.getUUID("owner").equals(controllerOwner))) {
            throw new IllegalArgumentException("A Mekanism tank belongs to a different owner");
        }
    }

    static int capacity(NativeRawCell cell, Level world) {
        // Query the installed tier and server configuration, without looking up or ticking a physical tile.
        var tile = ((EntityBlock) cell.state.getBlock()).newBlockEntity(cell.pos, cell.state);
        if (tile == null || !supports(tile)) throw new IllegalArgumentException("Missing Mekanism tank factory");
        var handler = Capabilities.FluidHandler.BLOCK.getCapability(world, cell.pos, cell.state, tile, Direction.UP);
        if (handler == null || handler.getTanks() != 1 || handler.getTankCapacity(0) <= 0) {
            throw new IllegalArgumentException("Unsupported Mekanism tank capacity");
        }
        return handler.getTankCapacity(0);
    }

    static FluidStack contents(CompoundTag raw, HolderLookup.Provider registries) {
        if (!raw.contains(TANKS)) return FluidStack.EMPTY;
        if (!(raw.get(TANKS) instanceof ListTag tanks) || tanks.size() != 1
                || !(tanks.get(0) instanceof CompoundTag entry) || !entry.getAllKeys().equals(Set.of("tank", "stored"))
                || !entry.contains("tank", Tag.TAG_BYTE) || entry.getByte("tank") != 0 || !entry.contains("stored", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Unknown Mekanism fluid tank storage");
        }
        var fluid = FluidStack.parse(registries, entry.getCompound("stored"))
            .orElseThrow(() -> new IllegalArgumentException("Invalid Mekanism tank fluid"));
        if (fluid.isEmpty() || !fluid.save(registries).equals(entry.get("stored"))) {
            throw new IllegalArgumentException("Cannot preserve Mekanism fluid components");
        }
        return fluid;
    }

    static void write(CompoundTag raw, FluidStack contents, HolderLookup.Provider registries) {
        if (contents.isEmpty()) {
            raw.remove(TANKS);
            return;
        }
        var entry = new CompoundTag();
        entry.putByte("tank", (byte)0);
        entry.put("stored", contents.save(registries));
        var tanks = new ListTag();
        tanks.add(entry);
        raw.put(TANKS, tanks);
    }

    private NativeVehicleMekanismTank() {}
}
