package com.vandorlabs.gear;

import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Exact own-field preimages of old formed feet with all new optional keys absent. */
public final class LegacyArmFootDefaultsChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Legacy arm foot: "+why);}
    private static void sameOwn(NBTTagCompound expected,NBTTagCompound actual,String why){NBTTagCompound a=expected.copy(),b=actual.copy();a.removeTag("ForgeCaps");b.removeTag("ForgeCaps");need(a.equals(b),why+" expected="+a+" actual="+b);}
    private static NBTTagCompound snapshot(String kind,EnumFacing mount){
        GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound tag=seed.writeToNBT(new NBTTagCompound());
        for(String key:new String[]{"GearSourceFootFacing","ArmFootFacing","ArmMiddleHinge",ArmFootComponentFrame.KEY,ArmFootComponentFrame.SOURCE_KEY})tag.removeTag(key);
        tag.setString("ArmMountDirection",mount.name());tag.setBoolean("GearController",true);tag.setInteger("GearHeight",2);tag.setString("GearKind",kind);tag.setInteger("GearFootFacing",EnumFacing.NORTH.getHorizontalIndex());tag.setLong("GearOwnerOffset",0);
        NBTTagList feet=new NBTTagList();feet.appendTag(new NBTTagIntArray(new int[]{0,-2,0}));tag.setTag("GearFeet",feet);
        NBTTagCompound opaque=new NBTTagCompound();opaque.setString("GearSourceFootFacing","opaque, never a direction");opaque.setIntArray("x",new int[]{431,-77,902});tag.setTag("ForgeCaps",opaque);return tag;
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        for(String kind:new String[]{"PLATE","SKI","WHEEL"})for(EnumFacing mount:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}){
            NBTTagCompound raw=snapshot(kind,mount);GearTileEntity tile=new GearTileEntity();tile.readFromNBT(raw);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());sameOwn(raw,saved,"unrotated old "+kind+" "+mount+" snapshot preserves absence");
            need(!saved.hasKey("GearSourceFootFacing")&&!saved.hasKey("ArmFootFacing")&&!saved.hasKey("ArmMiddleHinge")&&!saved.hasKey(ArmFootComponentFrame.KEY),"new defaults remain absent");
            NBTTagCompound moved=raw.copy();GearData.rotate(moved,1);need(moved.getCompoundTag("ForgeCaps").equals(raw.getCompoundTag("ForgeCaps")),"rotation preserves opaque capability preimage");
            if(mount==EnumFacing.DOWN)need(!moved.hasKey("GearSourceFootFacing"),"equal source and visual rotation retains missing field");
            else need(moved.hasKey("GearSourceFootFacing",3)&&moved.getInteger("GearSourceFootFacing")!=moved.getInteger("GearFootFacing"),"UP rotation records distinct physical source direction");
            GearTileEntity rotated=new GearTileEntity();rotated.readFromNBT(moved);sameOwn(moved,rotated.writeToNBT(new NBTTagCompound()),"rotated old source direction round-trips");
            NBTTagCompound configured=raw.copy();configured.setInteger("ArmFootFacing",EnumFacing.NORTH.getHorizontalIndex());configured.setInteger("GearSourceFootFacing",EnumFacing.SOUTH.getHorizontalIndex());GearTileEntity selected=new GearTileEntity();selected.readFromNBT(configured);sameOwn(configured,selected.writeToNBT(new NBTTagCompound()),"configured active foot retains distinct physical source");
        }
        System.out.println("Legacy arm foot defaults PASS checks="+checks+" old Plate/Ski/Wheel own-field absence, UP distinct source migration, explicit direction, opaque capabilities");
    }
    private LegacyArmFootDefaultsChecks(){}
}
