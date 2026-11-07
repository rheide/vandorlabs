package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import java.io.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;

/** The released vanilla multipart predicate parser selects actual authored parts for every stored/computed state. */
public final class FlatGlassSelectionLegacyOracle {
 public static void main(String[] args)throws Exception {PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)block;if(glass==null)throw new AssertionError("Source glass");ModelBlockDefinition definition;try(Reader reader=new InputStreamReader(FlatGlassSelectionLegacyOracle.class.getResourceAsStream("/assets/vandorlabs/blockstates/programmable_glass.json"),"UTF-8")){definition=ModelBlockDefinition.parseFromReader(reader);}File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(int size=0;size<3;size++)for(int depth=0;depth<3;depth++)for(boolean rotated:new boolean[]{false,true})for(int mask=0;mask<256;mask++){IBlockState state=glass.getDefaultState().withProperty(BlockProgrammableGlass.SIZE,size).withProperty(BlockGlassWall.DEPTH,depth).withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.LEFT,(mask&1)!=0).withProperty(BlockGlassWall.RIGHT,(mask&2)!=0).withProperty(BlockGlassWall.BOTTOM,(mask&4)!=0).withProperty(BlockGlassWall.TOP,(mask&8)!=0).withProperty(BlockGlassWall.INNER_BL,(mask&16)!=0).withProperty(BlockGlassWall.INNER_BR,(mask&32)!=0).withProperty(BlockGlassWall.INNER_TL,(mask&64)!=0).withProperty(BlockGlassWall.INNER_TR,(mask&128)!=0);JsonObject row=new JsonObject();row.addProperty("size",size);row.addProperty("depth",depth);row.addProperty("rotated",rotated);row.addProperty("mask",mask);JsonArray parts=new JsonArray();for(net.minecraft.client.renderer.block.model.multipart.Selector selector:definition.getMultipartData().getSelectors())if(selector.getPredicate(glass.getBlockState()).apply(state))for(Variant variant:selector.getVariantList().getVariantList()){JsonObject part=new JsonObject();part.addProperty("source",variant.getModelLocation().toString());part.addProperty("y",variant.getRotation()==ModelRotation.X0_Y90?90:0);part.addProperty("uvlock",variant.isUvLock());parts.add(part);}row.add("parts",parts);out.println(gson.toJson(row));profiles++;}}
  SegmentedReferenceGolden.check(file,"eba1f0af041e75ddaafc947888795cab17bb182f942fa6ed3e493cd79cf43d17");
  System.out.println("Actual flat glass multipart PASS profiles="+profiles+" actualVanillaPredicates/all8frameFlags/3sizes/3depths/2planes");
 }
}
