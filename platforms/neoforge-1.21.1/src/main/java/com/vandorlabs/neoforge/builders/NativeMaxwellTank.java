package com.vandorlabs.neoforge.builders;

import com.vandorlabs.drives.DriveControlPolicy;
import com.vandorlabs.drives.MaxwellEngine;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** One bounded fuel owner, shared by all three inlets and by carried drive snapshots. */
public final class NativeMaxwellTank {
    public static final String FUEL_TAG = "MaxwellFuel";
    public static final String ACTIVE_TAG = "MaxwellActiveLevel";
    public static final String BURN_TAG = "MaxwellBurnUnits";
    public static final TagKey<Fluid> FUELS = TagKey.create(Registries.FLUID, ResourceLocation.parse("vandorlabs:maxwell_fuels"));
    private static final Set<String> REGISTERED_NAMES = Set.of(
            "refined_fuel", "ethanol", "fuelium", "rocket_fuel", "rocketfuel", "fuelgc");

    private FluidStack fuel = FluidStack.EMPTY;
    private int burnUnits, active;
    private long lastTick = Long.MIN_VALUE;

    public int amount() { return fuel.getAmount(); }
    public int activeLevel() { return active; }
    public FluidStack contents() { return fuel.copy(); }

    public static boolean acceptsFluid(FluidStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return stack.is(FUELS) || REGISTERED_NAMES.contains(id.getPath())
                || id.getPath().equals("fuel") && (id.getNamespace().equals("galacticraft") || id.getNamespace().equals("galacticraftcore"));
    }

    private boolean compatible(FluidStack stack) {
        return acceptsFluid(stack) && (fuel.isEmpty() || FluidStack.isSameFluidSameComponents(fuel, stack));
    }

    public int throttle(NativeDriveSettings settings, int signal, boolean flightDemand) {
        return fuel.isEmpty() ? 0 : MaxwellEngine.throttle(settings.redstone(), settings.levels(), settings.inverted(),
                DriveControlPolicy.clampLevel(signal), flightDemand);
    }

    /** Returns whether fuel quantity or the public throttle changed this tick. */
    public boolean tick(int level, long gameTime) {
        if (gameTime == lastTick) return false;
        lastTick = gameTime;
        int previousLevel = active, previousAmount = amount();
        active = fuel.isEmpty() ? 0 : DriveControlPolicy.clampLevel(level);
        if (active != 0) {
            burnUnits += active;
            if (burnUnits >= MaxwellEngine.BURN_UNITS_PER_MB) {
                burnUnits -= MaxwellEngine.BURN_UNITS_PER_MB;
                fuel.shrink(1);
                if (fuel.isEmpty()) purge();
            }
        }
        return previousLevel != active || previousAmount != amount();
    }

    public void stop() { active = 0; }

    public void purge() {
        fuel = FluidStack.EMPTY;
        burnUnits = 0;
        active = 0;
    }

    public static NativeMaxwellTank read(CompoundTag tag, HolderLookup.Provider registries) {
        var tank = new NativeMaxwellTank();
        if (tag.contains(FUEL_TAG)) {
            if (!tag.contains(FUEL_TAG, Tag.TAG_COMPOUND)) throw invalid();
            var saved = tag.getCompound(FUEL_TAG);
            tank.fuel = FluidStack.parse(registries, saved).orElseThrow(NativeMaxwellTank::invalid);
            if (!acceptsFluid(tank.fuel) || tank.amount() > MaxwellEngine.CAPACITY || !tank.fuel.save(registries).equals(saved))
                throw invalid();
        }
        tank.burnUnits = scalar(tag, BURN_TAG, MaxwellEngine.BURN_UNITS_PER_MB - 1);
        tank.active = scalar(tag, ACTIVE_TAG, 15);
        if (tank.fuel.isEmpty() && (tank.burnUnits != 0 || tank.active != 0)) throw invalid();
        return tank;
    }

    private static int scalar(CompoundTag tag, String key, int maximum) {
        if (!tag.contains(key)) return 0;
        int value = tag.getInt(key);
        if (!tag.contains(key, Tag.TAG_INT) || value <= 0 || value > maximum) throw invalid();
        return value;
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.remove(FUEL_TAG);
        tag.remove(BURN_TAG);
        tag.remove(ACTIVE_TAG);
        if (!fuel.isEmpty()) tag.put(FUEL_TAG, fuel.save(registries));
        if (burnUnits != 0) tag.putInt(BURN_TAG, burnUnits);
        if (active != 0) tag.putInt(ACTIVE_TAG, active);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Maxwell Drive fuel data");
    }

    public IFluidHandler inlet(BooleanSupplier available, Runnable changed) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? MaxwellEngine.CAPACITY : 0; }
            @Override public FluidStack getFluidInTank(int tank) {
                return tank == 0 && available.getAsBoolean() ? contents() : FluidStack.EMPTY;
            }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return tank == 0 && available.getAsBoolean() && compatible(stack);
            }
            @Override public int fill(FluidStack stack, FluidAction action) {
                if (!available.getAsBoolean() || !compatible(stack)) return 0;
                int count = Math.min(stack.getAmount(), MaxwellEngine.CAPACITY - amount());
                if (count > 0 && action.execute()) {
                    if (fuel.isEmpty()) fuel = stack.copyWithAmount(count);
                    else fuel.grow(count);
                    changed.run();
                }
                return count;
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
        };
    }
}
