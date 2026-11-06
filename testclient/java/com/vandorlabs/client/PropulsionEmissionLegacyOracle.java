package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Released actual-state partitioning and client tick submissions; no substitute emitter. */
public final class PropulsionEmissionLegacyOracle {
 private static final class Capture extends CommonProxy {
  JsonArray particles=new JsonArray();
  @Override public void spawnThrusterParticle(World w,BlockPos p,EnumFacing f,int c,int s,float v){add(p.getX()+.5,p.getY()+.5,p.getZ()+.5,f,c,s,v,1,true);}
  @Override public void spawnThrusterParticle(World w,double x,double y,double z,EnumFacing f,int c,int s,float v,float spread){add(x,y,z,f,c,s,v,spread,false);}
  private void add(double x,double y,double z,EnumFacing f,int c,int s,float v,float spread,boolean block){JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);a.add(f.getIndex());a.add(c);a.add(s);a.add(Float.floatToRawIntBits(v));a.add(Float.floatToRawIntBits(spread));a.add(block);particles.add(a);}
 }
 private static boolean cell(int pattern,int x,int y,int size){switch(pattern){case 0:return x==0&&y==0;case 1:return x<2&&y<2;case 2:return x<size&&y<size;case 3:return x<size+1&&y<size+1;case 4:return x<3&&y<2;case 5:return x<2&&y<2&&!(x==1&&y==1);case 6:return x<4&&y<2;default:return x<2&&y<2;}}
 public static void main(String[] args)throws Exception {
  List<BlockPropulsionLight> blocks=PropulsionPlacementLegacyOracle.initialize();Capture capture=new Capture();VandorLabs.proxy=capture;Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int rows=0,members=0,particles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(BlockPropulsionLight b:blocks)for(EnumFacing face:EnumFacing.values())for(int pattern=0;pattern<(b instanceof BlockConnectedPropulsionLight?8:1);pattern++)for(int variant=0;variant<8;variant++){
    int max=b instanceof BlockConnectedHexagonalPropulsionLight?3:b instanceof BlockConnectedHoverPropulsionLight?4:8;
    NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(true);w.chunkLimit=pattern==7;BlockPos base=pattern==7?new BlockPos(15,90,15):new BlockPos(0,90,0);PanelPlane plane=PanelPlane.of(face);List<BlockPos> positions=new ArrayList<>();
    for(int y=0;y<max+1;y++)for(int x=0;x<max+1;x++)if(cell(pattern,x,y,max)){
     BlockPos p=base.offset(plane.right,x).offset(plane.up,y);if(!w.isBlockLoaded(p))continue;
     boolean special=x==1&&y==0;IBlockState state=b.getDefaultState().withProperty(BlockPropulsionLight.FACING,variant==5&&special?face.getOpposite():face).withProperty(BlockPropulsionLight.POWERED,variant!=4&&!(variant==6&&special));w.setBlockState(p,state,2);positions.add(p);
     NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("LightInitialized",true);tag.setBoolean("ManualOn",true);tag.setInteger("ParticleLevel",variant==0?0:variant==1?1:variant==2?2:3);tag.setBoolean("PropulsionJoin",!(variant==3&&special));if(variant==7&&special){tag.setBoolean("SignalBrightness",true);tag.setInteger("ChannelLevel",7);tag.setInteger("ParticleThreshold",8);}TileEntityRedstoneLight tile=(TileEntityRedstoneLight)w.getTileEntity(p);tile.readFromNBT(tag);tile.setPos(p);
    }
    JsonObject row=new JsonObject();row.addProperty("block",b.getRegistryName().getResourcePath());row.addProperty("face",face.getIndex());row.addProperty("pattern",pattern);row.addProperty("variant",variant);row.addProperty("max",max);JsonArray cells=new JsonArray();
    for(BlockPos p:positions){IBlockState state=w.getBlockState(p),actual=b.getActualState(state,w,p);TileEntityRedstoneLight tile=(TileEntityRedstoneLight)w.getTileEntity(p);JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());a.add(state.getValue(BlockPropulsionLight.FACING).getIndex());a.add(state.getValue(BlockPropulsionLight.POWERED));a.add(tile.isJoin());a.add(tile.isParticleStreamSelected());int size=1,x=0,y=0;if(b instanceof BlockConnectedPropulsionLight){BlockConnectedPropulsionLight.ConnectedPart part=actual.getValue(((BlockConnectedPropulsionLight)b).partProperty());size=part.size;x=part.x;y=part.y;}a.add(size);a.add(x);a.add(y);a.add(tile.getParticleLevel());cells.add(a);members++;}
    capture.particles=new JsonArray();for(BlockPos p:positions)((TileEntityRedstoneLight)w.getTileEntity(p)).update();row.add("cells",cells);row.add("particles",capture.particles);particles+=capture.particles.size();out.println(gson.toJson(row));rows++;
   }
  }
  SegmentedReferenceGolden.check(file,"df0cb25c94d6fa9318c8084eafa1394288b4565035249e2b41a26ed9f510501f");
  if(particles==0)throw new AssertionError("Missing actual tick submissions");
  System.out.println("Actual propulsion emission PASS rows="+rows+" members="+members+" submissions="+particles+" catalog27/6faces/partition/cold/join/mode/intensity/threshold/owner");
 }
}
