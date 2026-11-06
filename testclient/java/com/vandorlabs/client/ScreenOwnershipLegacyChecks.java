package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.items.ItemCatalogBlock;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
/** Actual constructor, reader allocation and configured ItemBlock load behavior before native ownership guards. */
public final class ScreenOwnershipLegacyChecks {
 private static net.minecraft.server.MinecraftServer marker;
 private static final class World extends NonRenderingChecks.MemoryWorld {World(){super(false);}@Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return marker;}}
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);marker=(net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)f.get(null)).allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class); // Vanilla ItemBlock only checks this sentinel for non-null.
 net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_ownership_reference"));JsonArray output=new JsonArray();NBTTagCompound empty=new TileEntityTokenSlot(true).writeEmbedded();
  for(String id:ScreenFamilyLegacyChecks.IDS){BlockAnimatedScreenSelector block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;
   for(int kind=0;kind<6;kind++){
    TileEntityAnimatedScreenSelector original=(TileEntityAnimatedScreenSelector)block.createNewTileEntity(null,0);if(kind==1)original.ownedChipReader();NBTTagCompound settings=original.writeToNBT(new NBTTagCompound());settings.removeTag("id");settings.removeTag("x");settings.removeTag("y");settings.removeTag("z");if(kind==2)settings.setTag("ScreenChipReader",new NBTTagCompound());if(kind==3)settings.setTag("ScreenChipReader",empty.copy());if(kind==4)settings.setInteger("PrimarySurfaceTexture",TileEntityAnimatedScreenSelector.CHIP_SURFACE);if(kind==5){NBTTagCompound reader=empty.copy();reader.setTag("InsertedToken",new ItemStack(ModItems.DATA_CHIP,1,2).writeToNBT(new NBTTagCompound()));settings.setTag("ScreenChipReader",reader);}
    World world=new World();EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,1),"ScreenOwnership")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};BlockPos pos=new BlockPos(0,80,0);ItemBlock item=block instanceof BlockProgrammableInput?new ItemProgrammableInput((BlockProgrammableInput)block):block instanceof BlockProgrammableHalfConsole?new ItemProgrammableHalfConsole((BlockProgrammableHalfConsole)block):new ItemCatalogBlock(block);ItemStack stack=new ItemStack(item);stack.setTagInfo("BlockEntityTag",settings);if(!item.placeBlockAt(stack,player,world,pos,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()))throw new AssertionError("Actual item placement");TileEntityAnimatedScreenSelector installed=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);NBTTagCompound saved=installed.writeToNBT(new NBTTagCompound());boolean owned=!installed.ownedChip().isEmpty();if(saved.hasKey("ScreenChipReader")!=owned||owned!=(kind==5))throw new AssertionError("Actual constructor/item empty-reader behavior "+id+" kind="+kind+" saved="+saved);
    JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("kind",kind);row.addProperty("owned",owned);row.addProperty("selected",installed.hasChipSurface(0));row.addProperty("savedReader",saved.hasKey("ScreenChipReader")?saved.getCompoundTag("ScreenChipReader").toString():"absent");output.add(row);
   }
  }
  String serialized=output.toString();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");StringBuilder digest=new StringBuilder();for(byte b:hash.digest(serialized.getBytes("UTF-8")))digest.append(String.format("%02x",b&255));if(!digest.toString().equals("bf63f918fdc6764207efe6d9182820b879110e2d0e021d70f8fa598d8d0ea72f"))throw new AssertionError("Actual reader envelope changed "+digest);JsonObject root=new JsonObject();root.addProperty("emptyReader",empty.toString());root.addProperty("sha256",digest.toString());root.add("cases",output);java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/screen-ownership-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,root.toString().getBytes("UTF-8"));System.out.println("Actual Screen ownership reference PASS cases="+output.size()+" sha256="+digest+" empty="+empty);
 }
}
