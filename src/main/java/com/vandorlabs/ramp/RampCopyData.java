package com.vandorlabs.ramp;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Clipboard tools relocate tile x/y/z but leave the ramp's private journal intact. */
public final class RampCopyData {
    public static final String ORIGIN="RampJournalOrigin";
    private RampCopyData(){}
    public static NBTTagCompound relocated(NBTTagCompound source){
        if(!source.hasKey(ORIGIN,4))return source;
        BlockPos at=new BlockPos(source.getInteger("x"),source.getInteger("y"),source.getInteger("z"));
        BlockPos saved=BlockPos.fromLong(source.getLong(ORIGIN));
        if(at.equals(saved))return source;
        NBTTagCompound result=com.vandorlabs.vehicle.VehicleTileData.translated(source,at.subtract(saved));
        result.setInteger("x",at.getX());result.setInteger("y",at.getY());result.setInteger("z",at.getZ());
        return result;
    }
}
