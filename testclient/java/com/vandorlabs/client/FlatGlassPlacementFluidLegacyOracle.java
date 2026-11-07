package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.io.*;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual placement-event side capture, including empty-volume sides and opaque occupied slots. */
public final class FlatGlassPlacementFluidLegacyOracle {
 private static IBlockState fluid(int kind){return kind==1?Blocks.WATER.getDefaultState():kind==2?Blocks.LAVA.getDefaultState():Blocks.AIR.getDefaultState();}
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("9b839a47-5194-42d5-a56b-c099509a55d7"),"GlassFluidPlacement")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int facing=0;facing<4;facing++)for(int depth=0;depth<3;depth++)for(boolean flip:new boolean[]{false,true})for(int previous=0;previous<3;previous++)for(int front=0;front<3;front++)for(int back=0;back<3;back++)for(boolean transverse:new boolean[]{false,true})for(int occupied=0;occupied<3;occupied++){
    world.clear();EnumFacing canonical=EnumFacing.getHorizontal(facing),actual=flip?canonical.getOpposite():canonical;world.setBlockState(pos,fluid(previous),3);net.minecraftforge.common.util.BlockSnapshot snapshot=net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,pos);IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,canonical.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);world.setBlockState(pos,state,3);TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(pos);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GlassPanelFacing",actual.getHorizontalIndex());if(occupied!=0){NBTTagCompound slots=new NBTTagCompound(),slot=new NBTTagCompound();slot.setString("FluidName",occupied==1?"lava":"future:opaque_fluid");slot.setInteger("Amount",1000);slot.setString("FutureFluid","opaque");slots.setTag(occupied==1?"Side0":"Side1",slot);tag.setTag("ContainedFluids",slots);}tile.readFromNBT(tag);world.setBlockState(pos.offset(actual),fluid(front),3);world.setBlockState(pos.offset(actual.getOpposite()),fluid(back),3);if(transverse)world.setBlockState(pos.up(),Blocks.WATER.getDefaultState(),3);WaterloggedParts.placed(new net.minecraftforge.event.world.BlockEvent.PlaceEvent(snapshot,Blocks.STONE.getDefaultState(),actor,EnumHand.MAIN_HAND));NBTTagCompound stored=new NBTTagCompound();tile.fluids.write(stored);JsonObject row=new JsonObject();row.addProperty("facing",facing);row.addProperty("depth",depth);row.addProperty("flip",flip);row.addProperty("previous",previous);row.addProperty("front",front);row.addProperty("back",back);row.addProperty("transverse",transverse);row.addProperty("occupied",occupied);row.addProperty("input",tag.toString());row.addProperty("stored",stored.toString());JsonArray available=new JsonArray();WaterloggedParts.Volume volume=WaterloggedParts.uncachedVolume(world,pos);available.add(volume.hasSide(0));available.add(volume.hasSide(1));row.add("visibleSides",available);out.println(gson.toJson(row));profiles++;
   }
  }
  SegmentedReferenceGolden.check(file,"9c38cce4782d3a7540ff8d60801ab8a15ad47e0e55d46011f8a71fc6c3bc92a0");
  System.out.println("Actual ordinary glass placement fluids PASS profiles="+profiles+" file="+file);
 }
}
