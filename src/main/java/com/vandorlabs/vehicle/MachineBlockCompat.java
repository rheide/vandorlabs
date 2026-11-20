package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;

/** Standalone machine owners only. Linked/remote inventories and native grids are separate adapters. */
public final class MachineBlockCompat {
    private static final Set<String> MEKANISM=new HashSet<>(Arrays.asList(
        "AdvancedFactory","EliteFactory","Factory","EnergizedSmelter","EnrichmentChamber","Crusher","Combiner",
        "OsmiumCompressor","PurificationChamber","ChemicalInjectionChamber","PrecisionSawmill","MetallurgicInfuser",
        "ChemicalCrystallizer","ChemicalDissolutionChamber","ChemicalInfuser","ChemicalOxidizer","ChemicalWasher",
        "ElectrolyticSeparator","RotaryCondensentrator","PRC","FormulaicAssemblicator","Oredictionificator",
        "ResistiveHeater","FuelwoodHeater","AmbientAccumulator"));
    public static boolean supported(TileEntity tile){
        if(tile==null)return false;String type=tile.getClass().getName();
        if(type.startsWith("mekanism.common.tile.TileEntity"))return MEKANISM.contains(type.substring("mekanism.common.tile.TileEntity".length()));
        if(!type.startsWith("cofh.thermalexpansion.block."))return false;
        for(Class<?> parent=tile.getClass();parent!=null;parent=parent.getSuperclass()){
            String name=parent.getName();
            if(name.equals("cofh.thermalexpansion.block.machine.TileMachineBase")||name.equals("cofh.thermalexpansion.block.dynamo.TileDynamoBase"))return true;
        }
        return type.equals("cofh.thermalexpansion.block.storage.TileCell")||type.equals("cofh.thermalexpansion.block.storage.TileTank")||type.equals("cofh.thermalexpansion.block.storage.TileCache");
    }
    public static void rotate(TileEntity tile,NBTTagCompound data,Rotation rotation){
        if(!supported(tile)||!tile.getClass().getName().startsWith("cofh."))return;
        if(data.hasKey("Facing",1)){int facing=data.getByte("Facing");if(facing<0||facing>=6)throw new IllegalArgumentException("Invalid Thermal machine facing");data.setByte("Facing",(byte)rotation.rotate(EnumFacing.getFront(facing)).getIndex());}
        if(data.hasKey("SideCache",7)){
            byte[] before=data.getByteArray("SideCache");if(before.length!=6)throw new IllegalArgumentException("Invalid Thermal side configuration");byte[] after=new byte[6];
            for(EnumFacing side:EnumFacing.values())after[rotation.rotate(side).getIndex()]=before[side.getIndex()];data.setByteArray("SideCache",after);
        }
    }
    private MachineBlockCompat(){}
}
