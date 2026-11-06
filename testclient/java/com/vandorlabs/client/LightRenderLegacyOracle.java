package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual released Light emitters; only Tessellator GPU submission is replaced with CPU capture. */
public final class LightRenderLegacyOracle {
 private static TextureAtlasSprite sprite(int height){TextureAtlasSprite value=new TextureAtlasSprite("oracle/light"){};value.setIconWidth(16);value.setIconHeight(height);value.initSprite(256,256,32,64,false);return value;}
 private static JsonObject copy(JsonObject source){JsonObject out=new JsonObject();for(java.util.Map.Entry<String,JsonElement> entry:source.entrySet())out.add(entry.getKey(),entry.getValue());return out;}
 private static JsonArray values(double... values){JsonArray out=new JsonArray();for(double value:values)out.add(value);return out;}
 private static void vertices(JsonObject row,BufferBuilder buffer,boolean drawn){JsonArray data=new JsonArray();if(drawn)for(int i=0;i<buffer.getVertexCount()*BlockSurfaceFormat.get().getNextOffset();i+=4)data.add(buffer.getByteBuffer().getInt(i));row.add("vertices",data);}
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();BlockProgrammableLight[] blocks={new BlockProgrammableLight(),new BlockProgrammableLightFrame(),new BlockProgrammableLightSlab()};
  Method housing=TEAnimatedScreenSelector.class.getDeclaredMethod("renderLightHousing",TextureAtlasSprite.class,int.class,double.class,double.class,double.class,double.class,double.class,double.class,int.class);housing.setAccessible(true);
  Method face=TEAnimatedScreenSelector.class.getDeclaredMethod("renderProgrammableLightFace",TextureAtlasSprite.class,TEAnimatedScreenSelector.LightGroup.class,BlockPos.class,double.class,double.class,double.class,double.class,double.class);face.setAccessible(true);
  File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long vertices=0;BufferBuilder buffer=new BufferBuilder(1<<16);
  OpenGlHelper.lastBrightnessX=0x70;OpenGlHelper.lastBrightnessY=0xD0;
  try(PrintWriter output=new PrintWriter(file,"UTF-8");PanelBoxEmitterChecks.TessellatorOverride capture=new PanelBoxEmitterChecks.TessellatorOverride(buffer)){
   for(int family=0;family<3;family++)for(EnumFacing facing:EnumFacing.values())for(boolean upper:new boolean[]{false,true})for(boolean small:new boolean[]{false,true}){
    IBlockState state=blocks[family].getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);if(family==2)state=state.withProperty(BlockProgrammableLightSlab.HALF,upper?net.minecraft.block.BlockSlab.EnumBlockHalf.TOP:net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM);
    AxisAlignedBB b=ProgrammableLightShape.local(state,small);JsonObject pose=new JsonObject();pose.addProperty("family",family);pose.addProperty("facing",facing.getIndex());pose.addProperty("upper",upper);pose.addProperty("small",small);pose.add("bounds",values(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));JsonArray directions=new JsonArray();for(EnumFacing local:EnumFacing.values())directions.add(TEAnimatedScreenSelector.lightWorldSide(facing,local).getIndex());pose.add("worldSides",directions);
    for(int height:new int[]{8,16,32})for(int layout=0;layout<3;layout++)for(int visible=0;visible<64;visible++){
     JsonObject row=copy(pose);row.addProperty("part","housing");row.addProperty("height",height);row.addProperty("layout",layout);row.addProperty("visible",visible);capture.cpu.draws=0;
     housing.invoke(null,sprite(height),visible,b.minX*16,b.minY*16,b.minZ*16,b.maxX*16,b.maxY*16,b.maxZ*16,layout);
     vertices(row,buffer,capture.cpu.draws>0);if(capture.cpu.draws>0)vertices+=buffer.getVertexCount();output.println(gson.toJson(row));profiles++;
    }
    PanelPlane plane=PanelPlane.of(facing);BlockPos pos=new BlockPos(19,83,-17);int axis=PanelPlane.axis(pos,plane.right),rowAxis=PanelPlane.axis(pos,plane.up);
    for(int columns:new int[]{1,3,17})for(int rows:new int[]{1,2,13})for(int column:new int[]{0,columns-1})for(int rowIndex:new int[]{0,rows-1}){
     TEAnimatedScreenSelector.LightGroup group=new TEAnimatedScreenSelector.LightGroup(axis-column,rowAxis-rowIndex,columns,rows,plane.right,plane.up);JsonObject row=copy(pose);row.addProperty("part","face");row.add("uv",values(group.left(pos),group.top(pos),group.right(pos),group.bottom(pos)));row.addProperty("height",32);capture.cpu.draws=0;
     face.invoke(null,sprite(32),group,pos,b.minX*16,b.maxX*16,b.minY*16,b.maxY*16,b.minZ*16);vertices(row,buffer,true);vertices+=buffer.getVertexCount();output.println(gson.toJson(row));profiles++;
    }
   }
  }
  SegmentedReferenceGolden.check(file,"c2343b5f5edd561d47338452e0b32f958bc093683500a331af29d0359a827b1a");
  System.out.println("Actual Light emitted mesh PASS profiles="+profiles+" vertices="+vertices+" all72poses/all64visibility/layouts/aspects/joinedUV; GPU upload only bypassed");
 }
}
