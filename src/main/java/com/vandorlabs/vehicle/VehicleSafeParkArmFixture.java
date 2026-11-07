package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import java.util.function.BiConsumer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Shared actual item placement for the player and abrupt-recovery arm fixtures. */
public final class VehicleSafeParkArmFixture {
 public static final int CHANNEL=6154077;
 private static void need(boolean value,String reason){if(!value)throw new IllegalStateException("Safe Park arm source: "+reason);}
 public static void place(WorldServer world,EntityPlayerMP player,BlockPos base){place(world,player,base,VehicleSafeParkArmFixture::need);}
 public static void place(WorldServer world,EntityPlayerMP player,BlockPos base,BiConsumer<Boolean,String> require){
  if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Disposable fixture only");
  BlockPos at=base.add(4,1,-1);player.inventory.currentItem=0;player.inventory.setInventorySlotContents(0,new ItemStack(GearRegistry.GEAR_EXTENDER));
  require.accept(player.getHeldItemMainhand().getItem().onItemUse(player,world,at.south(),EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"real Extender item on retained iron backing");
  GearTileEntity arm=(GearTileEntity)world.getTileEntity(at);require.accept(arm!=null&&arm.rebuild()&&arm.mountDirection()==EnumFacing.NORTH,"placed one-section NORTH Extender forms");arm.operator(player);
  NBTTagCompound settings=arm.settings();settings.setInteger("Trigger",1);settings.setString("Channels",Integer.toString(CHANNEL));settings.setDouble("ActiveExtension",2);settings.setBoolean("Deployed",true);arm.configure(settings);
  player.inventory.setInventorySlotContents(0,new ItemStack(Blocks.CHEST));require.accept(player.getHeldItemMainhand().getItem().onItemUse(player,world,at,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"real ordinary chest item on arm endpoint");
  TileEntityChest chest=(TileEntityChest)world.getTileEntity(at.north());require.accept(chest!=null,"ordinary payload chest owns contents");chest.setCustomName("Safe Park arm cargo");chest.setInventorySlotContents(3,new ItemStack(Items.EMERALD,7));require.accept(arm.target()&&arm.progress()==1,"unpowered arm starts at real construction endpoint");
 }
 private VehicleSafeParkArmFixture(){}
}
