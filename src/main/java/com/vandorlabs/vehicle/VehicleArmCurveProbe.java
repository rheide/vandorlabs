package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Gated actual native-tick reference for the carried arm's first retraction step. */
public final class VehicleArmCurveProbe {
    private static final boolean ACTIVE="1".equals(System.getenv("VANDOR_LABS_ARM_CARRIED_CURVE_ONLY"));
    private static final BlockPos AT=VehicleArmCouplerFixture.BASE.add(7,7,0);
    private static double reference=Double.NaN;
    public static boolean active(){return ACTIVE;}
    public static void setup(WorldServer world,EntityPlayerMP actor){
        if(!ACTIVE)return;if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Disposable curve probe only");
        ItemStack previous=actor.inventory.getStackInSlot(0).copy();world.setBlockState(AT.north(),Blocks.STONE.getDefaultState(),2);actor.inventory.setInventorySlotContents(0,new ItemStack(GearRegistry.GEAR_EXTENDER));if(actor.getHeldItemMainhand().onItemUse(actor,world,AT.north(),EnumHand.MAIN_HAND,EnumFacing.SOUTH,.5F,.5F,.5F)!=EnumActionResult.SUCCESS)throw new IllegalStateException("Native curve ordinary item placement");GearTileEntity nativeArm=(GearTileEntity)world.getTileEntity(AT);if(nativeArm==null||!nativeArm.rebuild())throw new IllegalStateException("Native curve arm did not form");nativeArm.operator(actor);NBTTagCompound settings=nativeArm.settings();settings.setInteger("Trigger",0);settings.setString("Channels","");settings.setBoolean("Deployed",false);nativeArm.configure(settings);actor.inventory.setInventorySlotContents(0,previous);actor.inventoryContainer.detectAndSendChanges();
    }
    public static boolean ready(WorldServer world){
        GearTileEntity nativeArm=(GearTileEntity)world.getTileEntity(AT);if(nativeArm==null)throw new IllegalStateException("Native curve owner disappeared");NBTTagCompound tag=nativeArm.writeToNBT(new NBTTagCompound());if(!tag.getBoolean("ArmRetractingStroke"))return false;
        if(Double.isNaN(reference)){reference=nativeArm.activeExtension();if(reference!=nativeArm.minimumExtension())throw new IllegalStateException("Native retraction curve differs from minimum");System.out.println("[vandorlabs][reprolab] arm-carried-curve native reference progress="+nativeArm.progress()+" extension="+reference+" flag=true");}return true;
    }
    public static void initial(GearTileEntity carried){if(carried.progress()!=1||carried.writeToNBT(new NBTTagCompound()).getBoolean("ArmRetractingStroke"))throw new IllegalStateException("Curve negative requires actual deployed carried flag=false");System.out.println("[vandorlabs][reprolab] arm-carried-curve initial carried progress=1 flag=false extension="+carried.activeExtension());}
    public static void check(GearTileEntity carried){if(carried.progress()>=1)return;NBTTagCompound tag=carried.writeToNBT(new NBTTagCompound());if(!tag.getBoolean("ArmRetractingStroke")||carried.activeExtension()!=reference)throw new IllegalStateException("Carried curve changed after first step: progress="+carried.progress()+" flag="+tag.getBoolean("ArmRetractingStroke")+" extension="+carried.activeExtension()+" actualNative="+reference);}
    private VehicleArmCurveProbe(){}
}
