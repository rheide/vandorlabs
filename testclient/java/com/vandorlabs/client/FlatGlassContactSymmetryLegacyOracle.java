package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual glass/flat/diagonal contact symmetry; transparent endpoint alignment is distinct from cap coverage. */
public final class FlatGlassContactSymmetryLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);
 private static void install(NonRenderingChecks.MemoryWorld world,net.minecraft.block.Block block,BlockPos pos,EnumFacing facing,int pose,boolean inverted,boolean join,boolean flip){IBlockState state;if(block instanceof BlockProgrammableGlass)state=block.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,pose);else state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted).withProperty(BlockProgrammableWall.DEPTH,block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isDiagonalShape()?0:pose);world.states.put(pos,state);if(block instanceof BlockProgrammableGlass){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GlassPanelFacing",(flip?facing.getOpposite():facing).getHorizontalIndex());tag.setBoolean("GlassJoin",join);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);}else{TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();if(((BlockProgrammableWall)block).isDiagonalShape())tile.setDiagonalGeometry(pose,0);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);}}
 private static JsonArray flat(BlockProgrammableWall.FlatCorner c){if(c==null)return null;JsonArray a=new JsonArray();a.add(c.frontArm);a.add(c.backArm);a.add(c.frontRight);a.add(c.backRight);a.add(c.straightLeft);a.add(c.straightRight);a.add(c.left());a.add(c.right());return a;}
 private static JsonArray corner(BlockProgrammableWall.Corner c){if(c==null)return null;JsonArray a=new JsonArray();a.add(c.frontRight);a.add(c.backRight);a.add(c.straightLeft);a.add(c.straightRight);a.add(c.frontFlat);a.add(c.backFlat);return a;}
 private static JsonElement number(double n){return Double.isNaN(n)?JsonNull.INSTANCE:new JsonPrimitive(n);}
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();net.minecraft.block.Block[] blocks=new net.minecraft.block.Block[4];for(net.minecraft.block.Block b:ModBlocks.BLOCKS){String id=b.getRegistryName().getResourcePath();if(id.equals("programmable_glass"))blocks[0]=b;if(id.equals("programmable_wall"))blocks[1]=b;if(id.equals("programmable_diagonal_wall"))blocks[2]=b;if(id.equals("programmable_diagonal_glass"))blocks[3]=b;}for(Object b:blocks)if(b==null)throw new AssertionError("Source contact catalog");NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int kind=0;kind<4;kind++)for(int facing=0;facing<4;facing++)for(int pose=0;pose<3;pose++)for(int inverted=0;inverted<(kind<2?1:2);inverted++)for(EnumFacing side:EnumFacing.values())for(int neighborKind=0;neighborKind<(kind==0?2:1);neighborKind++)for(int neighborFacing=0;neighborFacing<4;neighborFacing++)for(int depth=0;depth<3;depth++)for(boolean join:new boolean[]{false,true})for(boolean flip:new boolean[]{false,true}){
    world.clear();install(world,blocks[kind],POS,EnumFacing.getHorizontal(facing),pose,inverted!=0,true,false);install(world,blocks[neighborKind],POS.offset(side),EnumFacing.getHorizontal(neighborFacing),depth,false,join,flip);IBlockState state=world.getBlockState(POS);JsonObject row=new JsonObject();row.addProperty("kind",kind);row.addProperty("facing",facing);row.addProperty("pose",pose);row.addProperty("inverted",inverted!=0);row.addProperty("side",side.getIndex());row.addProperty("neighborKind",neighborKind);row.addProperty("neighborFacing",neighborFacing);row.addProperty("depth",depth);row.addProperty("join",join);row.addProperty("flip",flip);row.add("flat",flat(PanelJoins.flatCorner(state,world,POS)));if(kind>=2){BlockProgrammableWall block=(BlockProgrammableWall)blocks[kind];row.add("corner",corner(block.corner(state,world,POS)));JsonArray ends=new JsonArray();for(boolean upper:new boolean[]{false,true}){ends.add(number(BlockProgrammableWall.flatEnd(state,world,POS,upper)));ends.add(BlockProgrammableWall.coveredFlatEnd(state,world,POS,upper));}row.add("ends",ends);}out.println(gson.toJson(row));profiles++;
   }
  }
  SegmentedReferenceGolden.check(file,"6b40072048e4856ee96e48069afb67d12fbebda4469f479aae48db944e3b4072");
  System.out.println("Actual flat glass contact symmetry PASS profiles="+profiles+" file="+file);
 }
}
