package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import java.lang.reflect.Constructor;
import java.util.Collections;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Detached receipt parsing must neither call factories nor weaken live admission. */
public final class ArmCouplerReceiptReadChecks {
    public static final class Probe extends TileEntity {
        static int reads;
        @Override public void readFromNBT(NBTTagCompound tag){reads++;super.readFromNBT(tag);}
    }
    private static int checks;
    private static void need(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void refuses(Runnable action){try{action.run();throw new AssertionError("Malformed/live-invalid receipt admitted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(Probe.class,new ResourceLocation("vandorlabs:receipt_probe"));
        Constructor<ArmPayload> constructor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);constructor.setAccessible(true);
        NBTTagCompound own=new NBTTagCompound();own.setString("id","vandorlabs:receipt_probe");own.setString("FutureOwn","retained");NBTTagCompound caps=new NBTTagCompound();caps.setString("Opaque","exact");own.setTag("ForgeCaps",caps);
        ArmPayload original=constructor.newInstance(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),own))),new BlockPos(1,2,3),false,new BlockPos(4,5,6));
        NBTTagCompound input=original.write();input.setString("FutureHeader","exact");NBTTagCompound before=input.copy();
        ArmPayload detached=ArmPayload.readForCouplerReceipt(input);need(Probe.reads==0,"detached parser never constructs a tile/capability");need(detached.write().equals(before)&&input.equals(before),"detached ownership/header/opaque data exact and source unchanged");
        ArmPayload.read(input);need(Probe.reads==1,"ordinary reader still invokes actual tile admission");
        input.getCompoundTag("Blocks").setString("UnknownContainerField","retained by original parser policy");
        NBTTagCompound malformed=before.copy();malformed.setString("Materialized","false");refuses(()->ArmPayload.readForCouplerReceipt(malformed));
        NBTTagCompound offset=before.copy();offset.setLong("Offset",new BlockPos(81,0,0).toLong());refuses(()->ArmPayload.readForCouplerReceipt(offset));
        NBTTagCompound mapping=before.copy();mapping.setInteger("GridTileMapping",1);mapping.setIntArray("GridTileBasis",new int[]{1,0,0,0,1,0,0,0,-1});refuses(()->ArmPayload.readForCouplerReceipt(mapping));
        NBTTagCompound missing=own.copy();missing.setString("id","vandorlabs:missing_receipt_factory");ArmPayload unavailable=constructor.newInstance(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),missing))),BlockPos.ORIGIN,false,BlockPos.ORIGIN);NBTTagCompound unavailableTag=unavailable.write();need(ArmPayload.readForCouplerReceipt(unavailableTag).write().equals(unavailableTag),"detached recipe can preserve unavailable opaque original without manufacturing a tile");refuses(()->ArmPayload.read(unavailableTag));need(Probe.reads==1,"malformed/unknown detached parsing has no hidden callbacks");
        input.setString("FutureHeader","caller changed");need(detached.write().equals(before),"detached result owns defensive header/cell copies");
        System.out.println("Arm Coupler receipt read PASS checks="+checks+" detached factory calls=0; ordinary live admission retained");
    }
}
