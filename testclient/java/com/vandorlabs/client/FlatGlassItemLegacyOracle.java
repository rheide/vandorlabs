package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemCatalogBlock;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual ItemBlock partial-merge then dedicated outer-settings placement, configured pick and plain drops. */
public final class FlatGlassItemLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);
 private static net.minecraft.server.MinecraftServer marker;
 private static final class World extends NonRenderingChecks.MemoryWorld {World(){super(false);}@Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return marker;}}
 private static NBTTagCompound input(int profile){if(profile==0)return null;NBTTagCompound t=new NBTTagCompound();switch(profile){case 1:break;case 2:t.setInteger("Size",2);break;case 3:t.setInteger("Size",-1);break;case 4:t.setInteger("Size",99);break;case 5:t.setByte("Size",(byte)1);break;case 6:t.setString("Size","1");break;case 7:t.setInteger("Shade",-1);break;case 8:t.setInteger("Shade",99);break;case 9:t.setInteger("Shade",8);break;case 10:t.setInteger("Tint",-1);t.setBoolean("Framed",false);break;case 11:t.setInteger("Tint",99);t.setBoolean("Framed",false);break;case 12:t.setInteger("Tint",0);t.setBoolean("Framed",false);break;case 13:t.setString("Tint","0");t.setString("Framed","true");break;case 14:t.setBoolean("Join",false);break;case 15:t.setBoolean("Stretch",true);break;case 16:t.setInteger("LayoutMode",2);break;case 17:t.setInteger("LayoutMode",99);break;case 18:t.setString("LayoutMode","2");t.setBoolean("Stretch",true);break;case 19:t.setInteger("Material",0);break;case 20:t.setInteger("Material",14);break;case 21:t.setInteger("Material",Integer.MAX_VALUE);break;case 22:t.setByte("Material",(byte)0);break;case 23:t.setInteger("FrameInterior",0);t.setBoolean("FrameTile",true);break;case 24:t.setBoolean("FrameBare",true);break;case 25:t.setInteger("PlaybackMode",2);break;default:throw new AssertionError();}return t;}
 public static void main(String[] args)throws Exception{
  PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(glass);ItemBlock item=new ItemCatalogBlock(glass);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item.setRegistryName(glass.getRegistryName()));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new ResourceLocation("vandorlabs","programmable_glass"));java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);marker=(net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)f.get(null)).allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
  File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,rejected=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int rotated=0;rotated<2;rotated++)for(int depth=0;depth<3;depth++)for(int size=0;size<3;size++)for(int facing=0;facing<4;facing++)for(int generic=0;generic<2;generic++)for(int profile=0;profile<26;profile++){
    World world=new World();EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("7ab1f02a-9acf-4058-809a-2af9a76f2789"),"GlassItemOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=EnumFacing.getHorizontal(facing).getHorizontalAngle();IBlockState initial=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated!=0).withProperty(BlockGlassWall.DEPTH,depth).withProperty(BlockProgrammableGlass.SIZE,size);ItemStack held=new ItemStack(item,2);NBTTagCompound outer=input(profile),base=new NBTTagCompound();if(outer!=null)held.setTagInfo("ProgrammableGlassSettings",outer);if(generic!=0){base.setInteger("GlassShade",5);base.setInteger("GlassSize",1);base.setInteger("GlassTint",2);base.setBoolean("GlassFramed",false);base.setInteger("GlassMaterial",14);base.setBoolean("GlassJoin",false);base.setInteger("FrameInterior",0);base.setBoolean("FrameTile",true);base.setString("FutureField","retained");held.setTagInfo("BlockEntityTag",base);}String before=held.serializeNBT().toString();String failure="";try{if(!item.placeBlockAt(held,actor,world,POS,EnumFacing.UP,.375F,1,.625F,initial))throw new AssertionError("Source refused placement");}catch(IllegalArgumentException expected){failure=expected.getClass().getName();rejected++;}if(!before.equals(held.serializeNBT().toString()))throw new AssertionError("Source mutated held stack");
    IBlockState state=world.getBlockState(POS);TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(POS);JsonObject row=new JsonObject();row.addProperty("rotated",rotated);row.addProperty("depth",depth);row.addProperty("size",size);row.addProperty("facing",facing);row.addProperty("generic",generic);row.addProperty("profile",profile);row.addProperty("outer",outer==null?"":outer.toString());row.addProperty("base",base.toString());row.addProperty("failure",failure);row.addProperty("placedSize",state.getValue(BlockProgrammableGlass.SIZE));row.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());row.addProperty("pick",glass.getPickBlock(state,new RayTraceResult(Vec3d.ZERO,EnumFacing.NORTH,POS),world,POS,actor).serializeNBT().toString());JsonArray drops=new JsonArray();for(ItemStack drop:glass.getDrops(world,POS,state,0))drops.add(drop.serializeNBT().toString());row.add("drops",drops);out.println(gson.toJson(row));profiles++;
   }
  }
  SegmentedReferenceGolden.check(file,"79a792c8d2ba934589fc35c58751896ce1d7031b3a2319e450422cd8f2bf5efd");
  System.out.println("Actual ordinary glass item PASS profiles="+profiles+" invalid-layout-callbacks="+rejected+" file="+file);
 }
}
