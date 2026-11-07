package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual separate pane/frame rectangle and contour policies over irregular loaded flat-glass patches. */
public final class FlatGlassGroupsLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);
 private static JsonArray layout(GlassSurfaceGroups.Layout l){JsonArray a=new JsonArray();a.add(l.column);a.add(l.row);a.add(l.columns);a.add(l.rows);a.add(l.flipU);a.add(l.flipV);return a;}
 public static void main(String[] args)throws Exception{
  PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,members=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int mask=1;mask<64;mask+=2)for(int variant=0;variant<12;variant++){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);EnumFacing facing=rotated?EnumFacing.EAST:EnumFacing.NORTH,right=facing.rotateY();TileEntityProgrammableGlass[] tiles=new TileEntityProgrammableGlass[6];
    for(int cell=0;cell<6;cell++)if((mask&(1<<cell))!=0){BlockPos p=POS.offset(right,cell%3).up(cell/3);boolean peer=cell==1;NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GlassShade",peer&&variant==1?8:0);tag.setInteger("GlassTint",peer&&variant==2?2:1);tag.setInteger("GlassMaterial",peer&&variant==3?0:-1);tag.setInteger("GlassTextureLayout",peer&&variant==4?0:1);tag.setBoolean("GlassFramed",!(peer&&variant==5));tag.setBoolean("GlassJoin",!(peer&&variant==7));tag.setInteger("GlassPanelFacing",(peer&&variant==8?facing.getOpposite():facing).getHorizontalIndex());if(peer&&variant==6)tag.setInteger("FrameInterior",0);if(cell==0&&variant==11)tag.setBoolean("FrameTile",true);IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,peer&&variant==10?!rotated:rotated).withProperty(BlockGlassWall.DEPTH,peer&&variant==9?(depth+1)%3:depth);world.states.put(p,state);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(p);world.tiles.put(p,tile);tiles[cell]=tile;}
    GlassMaterials.changed();JsonObject row=new JsonObject();row.addProperty("rotated",rotated);row.addProperty("depth",depth);row.addProperty("mask",mask);row.addProperty("variant",variant);JsonArray cells=new JsonArray();for(int cell=0;cell<6;cell++)if(tiles[cell]!=null){TileEntityProgrammableGlass tile=tiles[cell];JsonObject c=new JsonObject();c.addProperty("cell",cell);c.add("pane",layout(GlassSurfaceGroups.layout(tile)));c.add("frame",layout(GlassSurfaceGroups.frameLayout(tile)));c.addProperty("contour",GlassSurfaceGroups.frameMask(tile));IBlockState actual=glass.getActualState(world.getBlockState(tile.getPos()),world,tile.getPos());int flags=0;net.minecraft.block.properties.PropertyBool[] properties={BlockGlassWall.LEFT,BlockGlassWall.RIGHT,BlockGlassWall.BOTTOM,BlockGlassWall.TOP,BlockGlassWall.INNER_BL,BlockGlassWall.INNER_BR,BlockGlassWall.INNER_TL,BlockGlassWall.INNER_TR};for(int i=0;i<properties.length;i++)if(actual.getValue(properties[i]))flags|=1<<i;c.addProperty("authored",flags);cells.add(c);members++;}row.add("cells",cells);out.println(gson.toJson(row));profiles++;
   }
  }
  SegmentedReferenceGolden.check(file,"b730d6bc4ffb9eab70b741d5a807e095334b9ce7bb236eb55bd6692aa92de707");
  System.out.println("Actual flat glass groups PASS profiles="+profiles+" member-projections="+members+" file="+file);
 }
}
