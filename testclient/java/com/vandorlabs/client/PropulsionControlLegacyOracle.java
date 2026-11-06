package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** Actual plain right-click callbacks, including bottom-left mode and any-member channel guards. */
public final class PropulsionControlLegacyOracle {
 public static void main(String[] args)throws Exception{List<BlockPropulsionLight> blocks=PropulsionPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int rows=0;
 try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockPropulsionLight block:blocks)for(EnumFacing face:EnumFacing.values())for(int profile=0;profile<6;profile++){
  NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(8,90,8);PanelPlane plane=PanelPlane.of(face);List<BlockPos> cells=new ArrayList<>();int size=block instanceof BlockConnectedPropulsionLight?2:1;
  for(int x=0;x<size;x++)for(int y=0;y<size;y++){BlockPos p=base.offset(plane.right,x).offset(plane.up,y);boolean on=profile!=1;world.setBlockState(p,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,face).withProperty(BlockPropulsionLight.POWERED,on),2);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("LightInitialized",true);tag.setBoolean("ManualOn",on);tag.setInteger("ParticleLevel",profile==2?3:profile==5?(x==0&&y==0?2:3):0);tag.setBoolean("PropulsionJoin",profile!=4);if(profile==3&&x==0&&y==0){tag.setInteger("RedstoneChannel",37);tag.setBoolean("ChannelSignal",true);tag.setInteger("ChannelLevel",15);}TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(p);tile.readFromNBT(tag);tile.setPos(p);cells.add(p);}
  EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("e7ee64b2-627e-4023-aef6-85e68f0cad73"),"EngineControlOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};
  JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("face",face.getIndex());row.addProperty("profile",profile);JsonArray frames=new JsonArray();frames.add(snapshot(world,cells));for(int step=0;step<4;step++){BlockPos target=cells.get(cells.size()-1);if(!block.onBlockActivated(world,target,world.getBlockState(target),player,EnumHand.MAIN_HAND,face,.5F,.5F,.5F))throw new AssertionError("Source right-click consumption");frames.add(snapshot(world,cells));}row.add("frames",frames);out.println(gson.toJson(row));rows++;
 }}SegmentedReferenceGolden.check(file,"87498e45eee80a0b94e12866236e030938a399bffe2502bc3fcd25cad889dca9");System.out.println("Actual propulsion controls PASS rows="+rows+" frames="+(rows*5)+" sixFaces/all27/solo+joined/4clicks/bottomLeft/channelGuard/intensity/join");
 }
 private static JsonArray snapshot(NonRenderingChecks.MemoryWorld world,List<BlockPos> cells){JsonArray frame=new JsonArray();for(BlockPos p:cells){TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(p);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());JsonArray c=new JsonArray();c.add(tile.getManualMode());c.add(tile.getParticleLevel());c.add(tile.getRedstoneChannel());c.add(saved.getBoolean("ManualOn"));c.add(world.getBlockState(p).getValue(BlockPropulsionLight.POWERED));c.add(tile.isParticleStreamSelected());c.add(saved.getBoolean("LightInitialized"));frame.add(c);}return frame;}

}
