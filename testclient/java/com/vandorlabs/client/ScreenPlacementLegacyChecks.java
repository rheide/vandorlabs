package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import com.vandorlabs.items.ItemCatalogBlock;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import com.google.gson.*;
/** Actual state callback and actual ItemBlock placement bridge, including inherited facings and slabs. */
public final class ScreenPlacementLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_placement_reference"));JsonArray output=new JsonArray();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
  for(String id:ScreenFamilyLegacyChecks.IDS){BlockAnimatedScreenSelector block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;if(block==null)throw new AssertionError(id);ItemBlock item=block instanceof BlockProgrammableInput?new ItemProgrammableInput((BlockProgrammableInput)block):block instanceof BlockProgrammableHalfConsole?new ItemProgrammableHalfConsole((BlockProgrammableHalfConsole)block):new ItemCatalogBlock(block);
   java.util.List<IBlockState> supports=new java.util.ArrayList<>();supports.add(Blocks.AIR.getDefaultState());for(Block slab:new Block[]{Blocks.STONE_SLAB,ModBlocks.PROGRAMMABLE_SLAB})for(BlockSlab.EnumBlockHalf half:BlockSlab.EnumBlockHalf.values())supports.add(slab.getDefaultState().withProperty(BlockSlab.HALF,half));supports.addAll(block.getBlockState().getValidStates());
   for(int support=0;support<supports.size();support++)for(int yaw=0;yaw<4;yaw++)for(EnumFacing side:EnumFacing.values())for(int sneak=0;sneak<2;sneak++)for(int fraction=0;fraction<3;fraction++){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,1),"ScreenPlacement")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};player.rotationYaw=yaw*90;player.setSneaking(sneak!=0);BlockPos pos=new BlockPos(0,80,0);world.states.put(pos.offset(side.getOpposite()),supports.get(support));float hit=.1F+.4F*fraction;IBlockState placed=block.getStateForPlacement(world,pos,side,hit,hit,1-hit,0,player);
    if(!item.placeBlockAt(new ItemStack(item),player,world,pos,side,hit,hit,1-hit,placed))throw new AssertionError("Actual placement refused "+id);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
    JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("support",support<5?support:5);value.addProperty("supportMeta",support<5?0:block.getMetaFromState(supports.get(support)));value.addProperty("yaw",yaw);value.addProperty("face",side.getIndex());value.addProperty("sneak",sneak!=0);value.addProperty("fraction",fraction);value.addProperty("meta",block.getMetaFromState(placed));value.addProperty("wall",tile.getWallPosition(-1));value.addProperty("ceiling",tile.isCeilingMounted());value.addProperty("ceilingPosition",tile.getCeilingPosition(-1));output.add(value);hash.update(value.toString().getBytes("UTF-8"));
   }
  }
  StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));if(!digest.toString().equals("cd0ab9533bfab20698da64c977ede6b306d67b535d0b9873c99bb67670c98b5a"))throw new AssertionError("Actual placement changed "+digest);JsonObject root=new JsonObject();root.addProperty("sha256",digest.toString());root.add("cases",output);java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/screen-placement-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,root.toString().getBytes("UTF-8"));System.out.println("Actual Screen placement reference PASS cases="+output.size()+" sha256="+digest);
 }
}
