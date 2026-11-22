package com.vandorlabs.drives;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/** One shared, single-fuel reservoir served by the three input-only faces. */
public final class MaxwellTank {
    private static final String FUEL_TAG = "MaxwellFuel";
    private static final String BURN_TAG = "MaxwellBurnUnits";
    private FluidStack fuel;
    private int burnUnits;
    private final Runnable changed;

    public MaxwellTank(Runnable changed) { this.changed = changed; }

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
                        new FluidTankProperties(connected ? contents() : null, MaxwellEngine.CAPACITY, connected, false) {
                            @Override public boolean canFillFluidType(FluidStack resource) { return connected && accepts(resource); }
                            @Override public boolean canDrainFluidType(FluidStack resource) { return false; }
                        }
                };
            }

            @Override public int fill(FluidStack resource, boolean commit) {
                if (!available.getAsBoolean() || !accepts(resource)) return 0;
                int count = Math.min(resource.amount, MaxwellEngine.CAPACITY - amount());
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

    public void burn(int level) {
        if (fuel == null || level == 0) return;
        burnUnits += level;
        if (burnUnits < MaxwellEngine.BURN_UNITS_PER_MB) return;
        burnUnits -= MaxwellEngine.BURN_UNITS_PER_MB;
        if (--fuel.amount == 0) {
            fuel = null;
            burnUnits = 0;
        }
        changed.run();
    }

    public void purge() {
        if (fuel == null) return;
        fuel = null;
        burnUnits = 0;
        changed.run();
    }

    public void read(NBTTagCompound tag) {
        FluidStack saved = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(FUEL_TAG));
        fuel = fuel(saved) && saved.amount <= MaxwellEngine.CAPACITY ? saved : null;
        burnUnits = fuel == null ? 0 : Math.max(0, Math.min(MaxwellEngine.BURN_UNITS_PER_MB - 1, tag.getInteger(BURN_TAG)));
    }

    public void write(NBTTagCompound tag) {
        if (fuel != null) tag.setTag(FUEL_TAG, fuel.writeToNBT(new NBTTagCompound()));
        if (burnUnits != 0) tag.setInteger(BURN_TAG, burnUnits);
    }
}
