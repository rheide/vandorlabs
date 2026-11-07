package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.Field;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual opaque filled diagonal emission beside narrow transparent ordinary Glass. */
public final class GlassFilledContactLegacyOracle {
 private static TextureAtlasSprite sprite(String id,int w,int h,int x,int y){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(w);s.setIconHeight(h);s.initSprite(256,128,x,y,false);return s;}
 private static void number(JsonObject row,String key,double v){if(Double.isNaN(v))row.add(key,JsonNull.INSTANCE);else row.addProperty(key,v);}
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();BlockProgrammableWall wall=null;BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS){if(b.getRegistryName().getResourcePath().equals("programmable_diagonal_wall"))wall=(BlockProgrammableWall)b;if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;}if(wall==null||glass==null)throw new AssertionError("Source catalog");TextureAtlasSprite body=sprite("filled-glass-body",32,32,0,0),metal=sprite("filled-glass-metal",64,64,192,64);Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int cases=0,aligned=0;long capVertices=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=1;fill<=3;fill++)for(int profile=1;profile<=3;profile++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=BlockPos.ORIGIN;DiagonalGeometryLegacyChecks.install(world,pos,wall,face,inverted,mode,fill,0);boolean rotated=face.getAxis()==EnumFacing.Axis.X,reverse=face!=(rotated?EnumFacing.EAST:EnumFacing.NORTH);
   for(boolean upper:new boolean[]{false,true})if(profile==3||profile==(upper?2:1))for(int offset=-1;offset<=1;offset++){int depth=upper?2:1;if(reverse)depth=3-depth;BlockPos next=(upper?pos.up():pos.down()).offset(face.getOpposite(),offset);world.setBlockState(next,glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.DEPTH,depth),2);}
   IBlockState state=world.getBlockState(pos);JsonObject row=new JsonObject();row.addProperty("facing",face.getName());row.addProperty("inverted",inverted);row.addProperty("mode",mode);row.addProperty("fill",fill);row.addProperty("profile",profile);double lower=BlockProgrammableWall.flatEnd(state,world,pos,false),upper=BlockProgrammableWall.flatEnd(state,world,pos,true);number(row,"lower",lower);number(row,"upper",upper);boolean hl=BlockProgrammableWall.coveredFlatEnd(state,world,pos,false),hu=BlockProgrammableWall.coveredFlatEnd(state,world,pos,true);if(hl||hu)throw new AssertionError("Narrow Glass cannot seal filled cap");row.addProperty("hideLower",hl);row.addProperty("hideUpper",hu);if(!Double.isNaN(lower)||!Double.isNaN(upper))aligned++;
   StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawConfiguredWall(capture,(TileEntityAnimatedScreenSelector)world.getTileEntity(pos),state,body,metal,null);float[] raw=(float[])vertices.get(capture.finish());JsonArray data=new JsonArray();int caps=0;for(int i=0;i<raw.length;i++){data.add(Float.floatToRawIntBits(raw[i]));if(i%8==6&&Math.abs(raw[i])>.9)caps++;}if(caps==0)throw new AssertionError("Filled cap emission missing");row.addProperty("capVertices",caps);capVertices+=caps;row.add("vertices",data);out.println(gson.toJson(row));cases++;
  }}
  SegmentedReferenceGolden.check(file,"6e32a6754dba086883f4433ec84a443486028ba6c8fb1f8663cfaf894c196d0b");
  System.out.println("Actual Glass filled contact emission PASS cases="+cases+" aligned="+aligned+" capVertices="+capVertices+" file="+file);
 }
}
