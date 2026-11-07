package com.vandorlabs.vehicle;

import java.util.Collections;
import net.minecraft.init.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** The actual durable-record encoder must retain airborne recovery state. */
public final class VehicleSafeParkJournalChecks {
    public static void main(String[] args)throws Exception {
        Bootstrap.register();
        com.vandorlabs.blocks.BlockPilotSeat seat=new com.vandorlabs.blocks.BlockPilotSeat();
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.registries.IForgeRegistryEntry.Impl.class,seat,null,"registryName");seat.setRegistryName("vandorlabs","pilot_seat");
        VehicleStructure structure=new VehicleStructure(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,seat.getDefaultState(),null)),BlockPos.ORIGIN,EnumFacing.NORTH,3);
        EntityGroundVehicle craft=new EntityGroundVehicle(null);craft.structure=structure;craft.hover=true;craft.hoverTarget=137.5;craft.rotationYaw=37;craft.setPosition(20,130,20);
        java.lang.reflect.Method encoder=VehicleTransfer.class.getDeclaredMethod("record",VehicleStructure.class,BlockPos.class,EntityGroundVehicle.class,boolean.class);encoder.setAccessible(true);
        NBTTagCompound tag=(NBTTagCompound)encoder.invoke(null,structure,new BlockPos(24,65,20),craft,true);
        if(!tag.hasKey("HoverMode",1)||!tag.getBoolean("HoverMode")||!tag.hasKey("HoverTarget",6)||tag.getDouble("HoverTarget")!=137.5)throw new AssertionError("Parking journal loses original Hover mode or target height");
        if(tag.getInteger("Schema")!=1||tag.getFloat("Yaw")!=37||tag.getDouble("Y")!=130||!tag.getCompoundTag("Craft").equals(structure.write()))throw new AssertionError("Existing parking recovery schema/pose/source changed");
        craft.hover=false;craft.hoverTarget=0;VehicleTransfer.restoreParkingFlight(craft,tag);if(!craft.hover||craft.hoverTarget!=137.5)throw new AssertionError("Cold parking rollback loses Hover state");
        NBTTagCompound old=tag.copy();old.removeTag("HoverMode");old.removeTag("HoverTarget");craft.hover=false;craft.hoverTarget=0;VehicleTransfer.restoreParkingFlight(craft,old);if(craft.hover||craft.hoverTarget!=0)throw new AssertionError("Old schema-one journal changed interpretation");
        NBTTagCompound bad=tag.copy();bad.setDouble("HoverTarget",Double.NaN);boolean refused=false;try{VehicleTransfer.restoreParkingFlight(craft,bad);}catch(java.io.IOException expected){refused=true;}if(!refused||craft.hover||craft.hoverTarget!=0)throw new AssertionError("Malformed flight state partially installed");
        System.out.println("Safe Park journal PASS actual durable record retains original Hover mode/target, source, yaw and position; old schema-one optional fields and malformed-target refusal");
    }
}
