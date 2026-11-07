package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.Method;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual discovered glass T bars, center-owned appearance, transformed clipping and frame padding. */
public final class FlatGlassBarLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);
 private static TileEntityProgrammableGlass install(NonRenderingChecks.MemoryWorld world,BlockProgrammableGlass glass,BlockPos pos,EnumFacing facing,int depth,int tint,int material,boolean selected){IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);world.states.put(pos,state);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GlassPanelFacing",facing.getHorizontalIndex());tag.setInteger("GlassTint",tint);tag.setInteger("GlassMaterial",material);tile.readFromNBT(tag);if(selected)tile.frameAppearance.interior=0;tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);return tile;}
 public static void main(String[] args)throws Exception{
  PropulsionPlacementLegacyOracle.initialize();Method setup=FlatGlassMeshLegacyOracle.class.getDeclaredMethod("setup");setup.setAccessible(true);setup.invoke(null);BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BufferBuilder buffer=new BufferBuilder(65536);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long vertices=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int depth=0;depth<3;depth++)for(int barDepth=0;barDepth<3;barDepth++)for(boolean left:new boolean[]{false,true})for(int tint=0;tint<4;tint++)for(int material:new int[]{-1,0})for(boolean selected:new boolean[]{false,true}){
    world.clear();TileEntityProgrammableGlass root=install(world,glass,POS,EnumFacing.NORTH,depth,tint,material,selected);install(world,glass,POS.north(),EnumFacing.EAST,barDepth,1,-1,false);install(world,glass,POS.south(),EnumFacing.EAST,barDepth,1,-1,false);install(world,glass,left?POS.west():POS.east(),EnumFacing.NORTH,depth,1,-1,false);GlassMaterials.changed();IBlockState state=world.getBlockState(POS);BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,world,POS);if(bar==null)throw new AssertionError("Actual T discovery");FlatPanelBarRenderer.Material[] materials={FlatPanelBarRenderer.material(root,state),FlatPanelBarRenderer.barMaterial(root,state,bar.frontArm)};
    for(int role=0;role<2;role++)for(boolean frame:new boolean[]{false,true}){FlatPanelBarRenderer.Material m=materials[role];StaticSurfaceMesh mesh=FlatPanelBarRenderer.part(frame?m.opaque:m.transparent,role==0?0:3,role==0?0:5,bar.frontArm,m.depth,m.left,m.width,frame?.02:0);buffer.begin(7,BlockSurfaceFormat.get());if(mesh!=null)mesh.draw(buffer,0xD0,0x70);buffer.finishDrawing();JsonObject row=new JsonObject();row.addProperty("depth",depth);row.addProperty("barDepth",barDepth);row.addProperty("left",left);row.addProperty("tint",tint);row.addProperty("material",material);row.addProperty("selected",selected);row.addProperty("role",role);row.addProperty("frame",frame);row.addProperty("near",PanelJoins.flatNear(state,world,POS));row.addProperty("barNear",bar.frontArm);row.addProperty("offset",m.depth);row.addProperty("opacity",m.opacity);row.addProperty("solid",m.solidPane);row.addProperty("stride",BlockSurfaceFormat.get().getIntegerSize());JsonArray words=new JsonArray();for(int i=0;i<buffer.getVertexCount()*BlockSurfaceFormat.get().getNextOffset();i+=4)words.add(buffer.getByteBuffer().getInt(i));row.add("vertices",words);out.println(gson.toJson(row));profiles++;vertices+=buffer.getVertexCount();}
   }
  }
  SegmentedReferenceGolden.check(file,"9179442ffa2f337f345f63f6f49d36b8de179c56df210f8f16136544f4880922");
  System.out.println("Actual ordinary glass T-bar PASS profiles="+profiles+" vertices="+vertices+" file="+file);
 }
}
