package com.vandorlabs.drives;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * One shared, single-fuel reservoir served by the three input-only faces. Capacity follows the drive size;
 * fuel saved above it (from a 3.0 tank of 1,000 mB) is kept and burns down normally, but inlets add none
 * until the level falls below capacity.
 */
public final class MaxwellTank {
    private static final String FUEL_TAG = "MaxwellFuel";
    /** 3.0 partial-millibucket counter in throttle level-ticks; read for migration only. */
    private static final String LEGACY_BURN_TAG = "MaxwellBurnUnits";
    public static final String BURN_TAG = "MaxwellBurnMicro";
    private static final int MICRO_PER_MB = com.vandorlabs.physics.FlightTuning.MAXWELL_BURN_MICRO_PER_MB;
    private FluidStack fuel;
    private int burnUnits;
    private final Runnable changed;
    private final IntSupplier capacity;

    public MaxwellTank(Runnable changed, IntSupplier capacity) { this.changed = changed; this.capacity = capacity; }

    public int capacity() { return capacity.getAsInt(); }

    public int amount() { return fuel == null ? 0 : fuel.amount; }

    public FluidStack contents() { return fuel == null ? null : fuel.copy(); }

    private static boolean fuel(FluidStack resource) {
        if (resource == null || resource.amount <= 0) return false;
        switch (resource.getFluid().getName().toLowerCase(Locale.ROOT)) {
            case "refined_fuel":
            case "ethanol":
            case "fuelium":
            case "rocketfuel":
            case "fuelgc": return true;
            case "fuel": return Loader.isModLoaded("galacticraftcore");
            default: return false;
        }
    }

    private boolean accepts(FluidStack resource) {
        return fuel(resource) && (fuel == null || fuel.isFluidEqual(resource));
    }

    public IFluidHandler inlet(BooleanSupplier available) {
        return new IFluidHandler() {
            @Override public IFluidTankProperties[] getTankProperties() {
                boolean connected = available.getAsBoolean();
                return new IFluidTankProperties[]{
                        new FluidTankProperties(connected ? contents() : null, capacity(), connected, false) {
                            @Override public boolean canFillFluidType(FluidStack resource) { return connected && accepts(resource); }
                            @Override public boolean canDrainFluidType(FluidStack resource) { return false; }
                        }
                };
            }

            @Override public int fill(FluidStack resource, boolean commit) {
                if (!available.getAsBoolean() || !accepts(resource)) return 0;
                int count = Math.max(0, Math.min(resource.amount, capacity() - amount()));
                if (commit && count > 0) {
                    if (fuel == null) fuel = new FluidStack(resource, count);
                    else fuel.amount += count;
                    changed.run();
                }
                return count;
            }

            @Override public FluidStack drain(FluidStack resource, boolean commit) { return null; }
            @Override public FluidStack drain(int maximum, boolean commit) { return null; }
        };
    }

    /** Burns {@code microUnits} millionths of a millibucket; see {@link MaxwellEngine#burnMicroUnits}. */
    public void burn(long microUnits) {
        if (fuel == null || microUnits <= 0) return;
        long total = burnUnits + microUnits;
        long whole = total / MICRO_PER_MB;
        burnUnits = (int) (total % MICRO_PER_MB);
        if (whole == 0) return;
        if (whole >= fuel.amount) {
            fuel = null;
            burnUnits = 0;
        } else fuel.amount -= (int) whole;
        changed.run();
    }

    public void purge() {
        if (fuel == null) return;
        fuel = null;
        burnUnits = 0;
        changed.run();
    }

    public void read(NBTTagCompound tag) {
        FluidStack saved = tag.hasKey(FUEL_TAG, 10) ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(FUEL_TAG)) : null;
        fuel = fuel(saved) && saved.amount <= MaxwellEngine.maxSavedFuel() ? saved : null;
        if (fuel == null) burnUnits = 0;
        else if (tag.hasKey(BURN_TAG, 3)) burnUnits = Math.max(0, Math.min(MICRO_PER_MB - 1, tag.getInteger(BURN_TAG)));
        else burnUnits = MaxwellEngine.legacyBurnToMicro(Math.min(MaxwellEngine.BURN_UNITS_PER_MB - 1, tag.getInteger(LEGACY_BURN_TAG)));
    }

    public void write(NBTTagCompound tag) {
        if (fuel != null) tag.setTag(FUEL_TAG, fuel.writeToNBT(new NBTTagCompound()));
        if (burnUnits != 0) tag.setInteger(BURN_TAG, burnUnits);
    }
}
