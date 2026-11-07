package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Immutable candidate for one enclosing vehicle ownership transaction, never a second arm journal. */
public final class ArmGridPlacement {
    /** Native cells in the containing craft's coordinate frame, in original payload order. */
    public final List<VehicleStructure.Cell> cells;
    private final NBTTagCompound payload;
    ArmGridPlacement(List<VehicleStructure.Cell> cells,NBTTagCompound payload){this.cells=Collections.unmodifiableList(new ArrayList<>(cells));this.payload=payload.copy();}
    /** Detached materialized arm metadata; callers cannot edit this candidate through its result. */
    public NBTTagCompound payloadTag(){return payload.copy();}
}
