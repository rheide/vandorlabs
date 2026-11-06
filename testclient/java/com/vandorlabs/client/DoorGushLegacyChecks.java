package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
/** Actual ordinary DoorWaterGush outlet ownership and publication decisions. */
public final class DoorGushLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();BlockDetailedDoor pair=new BlockDetailedDoor("gush_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);BlockConfigurableSpaceDoor block=new BlockConfigurableSpaceDoor("gush_fixture",true,pair);
  Path file=Paths.get(System.getProperty("vandorlabs.doorGushReference"));Files.createDirectories(file.toAbsolutePath().getParent());int cases=0;java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
  try(java.io.PrintWriter out=new java.io.PrintWriter(Files.newBufferedWriter(file))){for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean open:new boolean[]{false,true})for(boolean stop:new boolean[]{false,true})for(int half=0;half<2;half++)for(int first=0;first<4;first++)for(int second=0;second<4;second++)for(int target=0;target<6;target++)for(boolean previous:new boolean[]{false,true}){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,open),2);world.setBlockState(pos.up(),block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
   TileEntitySpaceDoor owner=(TileEntitySpaceDoor)world.getTileEntity(pos),part=(TileEntitySpaceDoor)world.getTileEntity(pos.up(half));NBTTagCompound tags=new NBTTagCompound(),slots=new NBTTagCompound();for(int side=0;side<2;side++){int kind=side==0?first:second;if(kind!=0){NBTTagCompound tag=new NBTTagCompound();tag.setString("FluidName",kind==1?"water":kind==2?"lava":"fixture:unknown");tag.setInteger("Amount",1000);slots.setTag("Side"+side,tag);}}tags.setTag("ContainedFluids",slots);part.fluids.read(tags);owner.setWaterOutlets(previous?255:0);
   net.minecraft.block.state.IBlockState state=target==0?Blocks.AIR.getDefaultState():target==1?Blocks.WATER.getDefaultState():target==2?Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,3):target==3?Blocks.LAVA.getDefaultState():target==4?Blocks.STONE.getDefaultState():Blocks.TALLGRASS.getDefaultState();for(int h=0;h<2;h++)for(EnumFacing d:EnumFacing.HORIZONTALS)world.setBlockState(pos.up(h).offset(d),state,2);
   if(stop)DoorWaterGush.stop(world,pos);else DoorWaterGush.opened(world,pos);
   JsonObject row=new JsonObject();row.addProperty("face",face.getIndex());row.addProperty("open",open);row.addProperty("stop",stop);row.addProperty("half",half);row.addProperty("first",first);row.addProperty("second",second);row.addProperty("target",target);row.addProperty("previous",previous);row.addProperty("mask",owner.waterOutlets());JsonArray states=new JsonArray();for(int h=0;h<2;h++)for(int d=0;d<4;d++){net.minecraft.block.state.IBlockState s=world.getBlockState(pos.up(h).offset(EnumFacing.getHorizontal(d)));states.add(s.getMaterial()==net.minecraft.block.material.Material.WATER?s.getValue(BlockLiquid.LEVEL):target==0?-1:target==3?-3:target==4?-4:-5);}row.add("states",states);String line=row.toString();out.println(line);hash.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;
  }}StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));if(cases!=6144||!hex.toString().equals("c9c7bee7c61fe7ee7d93545a1d1f4f0dab33835c468ed2653caafa67e503f115"))throw new AssertionError("Door gush source golden changed "+hex);System.out.println("Actual DoorWaterGush PASS cases="+cases+" golden="+hex);
 }
}
