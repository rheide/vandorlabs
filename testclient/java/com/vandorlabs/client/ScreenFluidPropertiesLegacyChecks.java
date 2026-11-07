package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import java.nio.file.*;
import net.minecraft.block.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemBucket;
import net.minecraft.util.math.BlockPos;

/** Actual released fluid eligibility; no synthetic material or state fixture substitutes the blocks. */
public final class ScreenFluidPropertiesLegacyChecks {
 public static void main(String[] args)throws Exception{
  Bootstrap.register();JsonArray rows=new JsonArray();java.lang.reflect.Method flow=BlockDynamicLiquid.class.getDeclaredMethod("canFlowInto",net.minecraft.world.World.class,BlockPos.class,net.minecraft.block.state.IBlockState.class);flow.setAccessible(true);
  for(String id:ScreenFamilyLegacyChecks.IDS){Block block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=candidate;if(block==null)throw new AssertionError(id);
   for(int meta=0;meta<16;meta++)for(boolean lava:new boolean[]{false,true}){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);net.minecraft.block.state.IBlockState state=block.getStateFromMeta(meta);world.setBlockState(pos,state,2);
    boolean allowed=(Boolean)flow.invoke(lava?Blocks.FLOWING_LAVA:Blocks.FLOWING_WATER,world,pos,state);
    boolean bucket=((ItemBucket)(lava?Items.LAVA_BUCKET:Items.WATER_BUCKET)).tryPlaceContainedLiquid(null,world,pos);
    if(allowed||bucket||!state.getMaterial().blocksMovement()||world.getBlockState(pos)!=state)throw new AssertionError("Source Screen fluid replacement "+id+" meta="+meta+" lava="+lava);
    JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("meta",meta);row.addProperty("lava",lava);row.addProperty("flow",allowed);row.addProperty("bucket",bucket);row.addProperty("blocksMovement",state.getMaterial().blocksMovement());rows.add(row);
   }
  }
  byte[] bytes=rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Path path=Paths.get(System.getProperty("vandorlabs.screenFluidReference"));Files.createDirectories(path.getParent());Files.write(path,bytes);
  StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  if(!hash.toString().equals("ed4735ee62744fcbdeaaf795a3593b713c82562e9e534e62ac29fa27131467ee"))throw new AssertionError("Source fluid boundary changed: "+hash);
  System.out.println("Screen fluid Source PASS cases="+rows.size()+" sha256="+hash);
 }
}
