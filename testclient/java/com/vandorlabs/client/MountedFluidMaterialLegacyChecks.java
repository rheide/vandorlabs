package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import java.nio.file.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

/** Actual registered non-Vehicle families: logical material, bucket replacement and flow predicate. */
public final class MountedFluidMaterialLegacyChecks {
 private static String family(Block block){if(block instanceof BlockSignalControl)return "numeric-control";if(block instanceof BlockIndustrialLever)return "lever";if(block instanceof BlockMountedSwitch&&!(block instanceof BlockTokenSlot))return "mounted-control";if(block instanceof BlockWirelessRedstone)return "wireless";if(block instanceof BlockProgrammableLight)return "light";if(block instanceof BlockPropulsionLight)return "propulsion";return null;}
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();JsonArray rows=new JsonArray();int blocks=0;
  java.lang.reflect.Method flow=net.minecraft.block.BlockDynamicLiquid.class.getDeclaredMethod("canFlowInto",net.minecraft.world.World.class,BlockPos.class,IBlockState.class);flow.setAccessible(true);
  for(Block block:ModBlocks.BLOCKS){String family=family(block);if(family==null)continue;blocks++;
   for(IBlockState state:block.getBlockState().getValidStates())for(boolean lava:new boolean[]{false,true}){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);world.setBlockState(pos,state,2);
    boolean canFlow=(Boolean)flow.invoke(lava?net.minecraft.init.Blocks.FLOWING_LAVA:net.minecraft.init.Blocks.FLOWING_WATER,world,pos,state);
    boolean bucket=((net.minecraft.item.ItemBucket)(lava?net.minecraft.init.Items.LAVA_BUCKET:net.minecraft.init.Items.WATER_BUCKET)).tryPlaceContainedLiquid(null,world,pos);
    JsonObject row=new JsonObject();row.addProperty("id","vandorlabs:"+block.getRegistryName().getResourcePath());row.addProperty("family",family);row.addProperty("state",state.toString());row.addProperty("meta",block.getMetaFromState(state));row.addProperty("lava",lava);row.addProperty("blocksMovement",state.getMaterial().blocksMovement());row.addProperty("liquid",state.getMaterial().isLiquid());row.addProperty("flow",canFlow);row.addProperty("bucket",bucket);row.addProperty("retained",world.getBlockState(pos).getBlock()==block);rows.add(row);
   }
  }
  JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("blocks",blocks);root.add("cases",rows);StringBuilder golden=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))golden.append(String.format(java.util.Locale.ROOT,"%02x",value&255));if(blocks!=42||rows.size()!=48872||!golden.toString().equals("3e7619ff6a3719369ef6cd24957f5b91a539394630b11a3e36e3fc9a41953cc1"))throw new AssertionError("Released material corpus changed: "+golden);root.addProperty("golden",golden.toString());Path output=Paths.get(System.getProperty("vandorlabs.mountedFluidMaterialReference","build/test-artifacts/mounted-fluid-material-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(output)){new Gson().toJson(root,writer);}System.out.println("Actual mounted fluid material Source PASS blocks="+blocks+" cases="+rows.size());
 }
}
