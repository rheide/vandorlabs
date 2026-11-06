package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import java.io.*;
import java.util.*;
import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
/** Actual Source configured-shape transactions and both released failure stages. */
public final class PropulsionShapeLegacyOracle {
 public static void main(String[] args)throws Exception{List<BlockPropulsionLight> blocks=PropulsionPlacementLegacyOracle.initialize();Field hook=BlockPropulsionLight.class.getDeclaredField("shapeFailure");hook.setAccessible(true);System.setProperty("vandorlabs.reprolab","true");File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int rows=0;
 try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockPropulsionLight block:blocks)for(EnumFacing facing:EnumFacing.values())for(int shape=-1;shape<=3;shape++)for(int fault=0;fault<3;fault++){
  NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(8,90,8);PanelPlane plane=PanelPlane.of(facing);List<BlockPos> cells=new ArrayList<>();List<TileEntityRedstoneLight> owners=new ArrayList<>();int size=block instanceof BlockConnectedPropulsionLight?2:1;for(int x=0;x<size;x++)for(int y=0;y<size;y++){BlockPos p=base.offset(plane.right,x).offset(plane.up,y);world.setBlockState(p,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing),2);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("ManualOn",true);tag.setBoolean("LightInitialized",true);tag.setInteger("ParticleLevel",1+(x+y)%3);tag.setInteger("PropulsionSideTexture",64-x-y);tag.setIntArray("RedstoneChannels",new int[]{17+x+y});TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(p);tile.readFromNBT(tag);tile.setPos(p);cells.add(p);owners.add(tile);}
  final String phase=fault==1?"before-write":"after-write";final BlockPos fail=cells.get(cells.size()-1);if(fault!=0)hook.set(null,(java.util.function.BiConsumer<String,BlockPos>)(stage,pos)->{if(stage.equals(phase)&&pos.equals(fail))throw new IllegalStateException("injected Source shape failure");});else hook.set(null,null);
  JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("face",facing.getIndex());row.addProperty("shape",shape);row.addProperty("fault",fault);row.add("before",snapshot(world,cells,owners));boolean result=BlockPropulsionLight.configureShape(world,cells.get(cells.size()-1),shape,null);row.addProperty("result",result);row.add("after",snapshot(world,cells,owners));out.println(gson.toJson(row));rows++;
 }}finally{hook.set(null,null);System.clearProperty("vandorlabs.reprolab");}SegmentedReferenceGolden.check(file,"89eb19a7489093efca82cbdd176c971f8e5380293f54d37dfa4d11f7c5c20d9d");System.out.println("Actual propulsion shape transaction PASS profiles="+rows+" all27/6faces/allTargets/before+afterWriteFailure/physicalTileIdentity/settings");
 }
 private static JsonArray snapshot(NonRenderingChecks.MemoryWorld world,List<BlockPos> cells,List<TileEntityRedstoneLight> owners){JsonArray out=new JsonArray();for(int i=0;i<cells.size();i++){BlockPos pos=cells.get(i);TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(pos);JsonObject row=new JsonObject();row.addProperty("block",world.getBlockState(pos).getBlock().getRegistryName().getResourcePath());row.addProperty("face",world.getBlockState(pos).getValue(BlockPropulsionLight.FACING).getIndex());row.addProperty("powered",world.getBlockState(pos).getValue(BlockPropulsionLight.POWERED));row.addProperty("sameOwner",tile==owners.get(i));row.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());out.add(row);}return out;}
}
