package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemCatalogBlock;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Real configured ItemBlock callback: saved mounting choices and click-specific overrides. */
public final class ScreenConfiguredPlacementLegacyChecks {
 private static net.minecraft.server.MinecraftServer marker;
 private static final class World extends NonRenderingChecks.MemoryWorld {World(){super(false);}@Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return marker;}}
 private static final String[] TAGS={"{}","{wallPosition:1,CeilingMounted:1b,CeilingPosition:2,housingTexture:14,HousingTextureVersion:1,RedstoneChannels:[I;17,19]}","{wallPosition:2,CeilingMounted:0b,CeilingPosition:0,smallInput:1b,framed:0b}","{wallPosition:0,CeilingMounted:1b,CeilingPosition:1,redstoneEnabled:1b,displayMode:0}","{wallPosition:-1,CeilingMounted:0b,CeilingPosition:2,SurfaceTextureLayout:2}"};
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);marker=(net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)field.get(null)).allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
  java.lang.reflect.Constructor<com.vandorlabs.tiles.DeferredTileLoad> constructor=com.vandorlabs.tiles.DeferredTileLoad.class.getDeclaredConstructor();constructor.setAccessible(true);com.vandorlabs.tiles.DeferredTileLoad unload=constructor.newInstance();
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_mount_reference"));JsonArray rows=new JsonArray();
  for(String id:ScreenFamilyLegacyChecks.IDS){BlockAnimatedScreenSelector block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;if(block==null)throw new AssertionError(id);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);ItemBlock item=block instanceof BlockProgrammableInput?new ItemProgrammableInput((BlockProgrammableInput)block):block instanceof BlockProgrammableHalfConsole?new ItemProgrammableHalfConsole((BlockProgrammableHalfConsole)block):new ItemCatalogBlock(block);item.setRegistryName(block.getRegistryName());net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item);
   for(int input=0;input<TAGS.length;input++)for(int yaw=0;yaw<4;yaw++)for(EnumFacing face:EnumFacing.values())for(int sneak=0;sneak<2;sneak++)for(int fraction=0;fraction<3;fraction++)for(int support=0;support<3;support++){
    World world=new World();EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,11),"ScreenMount")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};player.rotationYaw=yaw*90;player.setSneaking(sneak!=0);BlockPos pos=new BlockPos(0,80,0);if(support>0)world.states.put(pos.offset(face.getOpposite()),net.minecraft.init.Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,support==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP));float hit=.1F+.4F*fraction;
    ItemStack stack=new ItemStack(item);NBTTagCompound raw=JsonToNBT.getTagFromJson(TAGS[input]);if(input>0)stack.setTagInfo("BlockEntityTag",raw.copy());player.setHeldItem(EnumHand.MAIN_HAND,stack);IBlockState state=block.getStateForPlacement(world,pos,face,hit,hit,1-hit,0,player);
    if(!item.placeBlockAt(stack,player,world,pos,face,hit,hit,1-hit,state))throw new AssertionError("Source callback refused");TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);if(input==1&&(tile.getHousingTexture()!=14||!tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(17,19))))throw new AssertionError("Fixture did not execute actual server item NBT merge");
    JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("input",TAGS[input]);row.addProperty("yaw",yaw);row.addProperty("face",face.getIndex());row.addProperty("sneak",sneak!=0);row.addProperty("fraction",fraction);row.addProperty("support",support);row.addProperty("meta",block.getMetaFromState(world.getBlockState(pos)));row.addProperty("wall",tile.getWallPosition(-1));row.addProperty("ceiling",tile.isCeilingMounted());row.addProperty("ceilingPosition",tile.getCeilingPosition(-1));row.addProperty("pick",block.createConfiguredDrop(tile).getSubCompound("BlockEntityTag").toString());rows.add(row);
    // Each isolated fake world has no tick/unload loop of its own.
    tile.onChunkUnload();tile.invalidate();net.minecraftforge.event.world.WorldEvent.Unload event=new net.minecraftforge.event.world.WorldEvent.Unload(world);unload.worldUnload(event);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(event);
   }
  }
  java.nio.file.Path output=java.nio.file.Paths.get("build/test-artifacts/screen-configured-placement-reference.json");java.nio.file.Files.createDirectories(output.getParent());byte[] bytes=rows.toString().getBytes("UTF-8");java.nio.file.Files.write(output,bytes);StringBuilder sha=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format("%02x",value&255));if(rows.size()!=15120||!sha.toString().equals("2c5adaff8e83010a52a122beb1fd2c33ee7dbd95e67f4e0a5d73721f52a81ba3"))throw new AssertionError("Source configured mount corpus changed "+sha);System.out.println("Actual configured Screen placement PASS cases="+rows.size()+" sha256="+sha+" saved mounting fields/real ItemBlock merge");
 }
}
