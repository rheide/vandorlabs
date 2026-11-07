package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemCatalogBlock;
import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Measured Source getters, rather than constructor arguments, for standalone glass families. */
public final class GlassPropertiesLegacyOracle {
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("f20ec165-67a9-4a01-90c4-8d5b139b45c9"),"GlassProperties")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableGlass||block instanceof BlockProgrammableDiagonalGlass||block instanceof BlockCanopy){net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(new ItemCatalogBlock(block).setRegistryName(block.getRegistryName()));world.clear();IBlockState state=block.getDefaultState();world.setBlockState(pos,state,3);JsonObject row=new JsonObject();row.addProperty("id",block.getRegistryName().toString());row.addProperty("hardness",block.getBlockHardness(state,world,pos));row.addProperty("explosion",block.getExplosionResistance((net.minecraft.entity.Entity)null));row.addProperty("lightOpacity",block.getLightOpacity(state,world,pos));row.addProperty("opaque",block.isOpaqueCube(state));row.addProperty("full",block.isFullCube(state));row.addProperty("blocksMovement",state.getMaterial().blocksMovement());row.addProperty("materialOpaque",state.getMaterial().isOpaque());row.addProperty("tool",block.getHarvestTool(state));row.addProperty("harvestLevel",block.getHarvestLevel(state));net.minecraft.block.SoundType sound=block.getSoundType(state,world,pos,actor);row.addProperty("breakSound",sound.getBreakSound().getRegistryName().toString());row.addProperty("placeSound",sound.getPlaceSound().getRegistryName().toString());row.addProperty("volume",sound.getVolume());row.addProperty("pitch",sound.getPitch());JsonArray tools=new JsonArray();for(ItemStack stack:new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.WOODEN_PICKAXE),new ItemStack(Items.STONE_PICKAXE),new ItemStack(Items.DIAMOND_PICKAXE),new ItemStack(Items.WOODEN_SWORD),new ItemStack(Items.SHEARS)}){actor.setHeldItem(EnumHand.MAIN_HAND,stack);JsonObject tool=new JsonObject();tool.addProperty("item",stack.isEmpty()?"hand":stack.getItem().getRegistryName().toString());tool.addProperty("canHarvest",block.canHarvestBlock(world,pos,actor));tool.addProperty("destroySpeed",stack.isEmpty()?1:stack.getDestroySpeed(state));tools.add(tool);}row.add("tools",tools);if(block instanceof BlockProgrammableGlass){JsonArray drops=new JsonArray();for(ItemStack stack:block.getDrops(world,pos,state,0))drops.add(stack.serializeNBT().toString());row.add("drops",drops);}out.println(gson.toJson(row));profiles++;}
  }
  SegmentedReferenceGolden.check(file,"736271654f8a4d39230a6388ca3bec6339a3fcb47542d2d7b809074bd01f4287");
  System.out.println("Actual standalone glass properties PASS profiles="+profiles+" file="+file);
 }
}
