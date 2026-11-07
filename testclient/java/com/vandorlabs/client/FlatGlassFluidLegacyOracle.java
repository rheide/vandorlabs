package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual vanilla fluid eligibility and source-water/lava ticks against the released glass material. */
public final class FlatGlassFluidLegacyOracle {
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;Method can=BlockDynamicLiquid.class.getDeclaredMethod("canFlowInto",World.class,BlockPos.class,IBlockState.class);can.setAccessible(true);NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int meta=-1;meta<=17;meta++)for(boolean lava:new boolean[]{false,true})for(EnumFacing side:EnumFacing.HORIZONTALS){world.clear();IBlockState state=glass.getStateFromMeta(meta);world.setBlockState(pos,state,3);BlockPos source=pos.offset(side);world.setBlockState(source.down(),Blocks.STONE.getDefaultState(),3);BlockDynamicLiquid liquid=(BlockDynamicLiquid)(lava?Blocks.FLOWING_LAVA:Blocks.FLOWING_WATER);world.setBlockState(source,liquid.getDefaultState(),3);boolean eligible=(Boolean)can.invoke(liquid,world,pos,state);liquid.updateTick(world,source,world.getBlockState(source),new Random(37));JsonObject row=new JsonObject();row.addProperty("meta",meta);row.addProperty("lava",lava);row.addProperty("side",side.getIndex());row.addProperty("blocksMovement",state.getMaterial().blocksMovement());row.addProperty("canFlowInto",eligible);row.addProperty("retained",world.getBlockState(pos).getBlock()==glass);out.println(gson.toJson(row));profiles++;}
  }
  SegmentedReferenceGolden.check(file,"280270a7d2b5291ab2997efcd102fd4c21bc9cfabcd821a2f41ef7d7c4ea734c");
  System.out.println("Actual ordinary glass fluid eligibility/flow PASS profiles="+profiles+" file="+file);
 }
}
