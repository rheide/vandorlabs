package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Actual saved frame-light policy and opaque-insert backlight, including independent legacy FrameBare. */
public final class FlatGlassLightLegacyOracle {
 public static void main(String[] args)throws Exception{
  PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;List<Integer> choices=new ArrayList<>();choices.add(-1);for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(FrameAppearance.valid(i))choices.add(i);NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);world.states.put(pos,glass.getDefaultState());File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int choice:choices)for(int slot=0;slot<3;slot++)for(boolean bare:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true})for(int tint:new int[]{1,3})for(int material:new int[]{-1,0}){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("FrameFront",slot==0?choice:-1);tag.setInteger("FrameInterior",slot==1?choice:-1);tag.setInteger("FrameExterior",slot==2?choice:-2);tag.setBoolean("FrameBare",bare);tag.setBoolean("GlassFramed",framed);tag.setInteger("GlassTint",tint);tag.setInteger("GlassMaterial",material);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);JsonObject row=new JsonObject();row.addProperty("tag",tag.toString());row.addProperty("frameLight",tile.frameAppearance.light());row.addProperty("intrinsic",glass.getLightValue(world.getBlockState(pos),world,pos));row.addProperty("paneBlock",GlassInsertLighting.block(tile,112));out.println(gson.toJson(row));profiles++;}
  }
  SegmentedReferenceGolden.check(file,"94265e1a8eb1a610bcb74f71b34f087e58ea6a940007f1787c2af756f8eea882");
  System.out.println("Actual flat glass light PASS choices="+choices.size()+" profiles="+profiles+" file="+file);
 }
}
