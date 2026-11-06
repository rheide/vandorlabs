package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Frozen released specialized item callbacks, including fresh-save partial merge and removal ordering. */
public final class TrapdoorPlacementLegacyOracle {
 private static final BlockPos POS=new BlockPos(10,80,10);
 private static NBTTagCompound tag(int kind){if(kind==0)return null;NBTTagCompound n=new NBTTagCompound();switch(kind){case 1:break;case 2:n.setInteger("housingTexture",0);break;case 3:n.setInteger("TrapdoorPosition",1);break;case 4:n.setBoolean("TrapdoorSliding",true);break;case 5:n.setInteger("TrapdoorPosition",2);n.setInteger("TrapdoorTrigger",0);n.setIntArray("RedstoneChannels",new int[]{17,19});break;case 6:n.setBoolean("TrapdoorCover",true);n.setInteger("TrapdoorCoverFacing",1);break;case 7:n.setInteger("DiagonalHingeSide",2);n.setBoolean("DiagonalRotationFlipped",true);break;case 8:n.setBoolean("DiagonalHalfHeight",true);break;case 9:n.setBoolean("DiagonalFullWidth",true);break;default:throw new AssertionError();}return n;}
 static JsonObject snapshot(NonRenderingChecks.MemoryWorld w,BlockPos p){TileEntityProgrammableTrapdoor t=(TileEntityProgrammableTrapdoor)w.getTileEntity(p);JsonObject n=new JsonObject();n.addProperty("stateFacing",w.getBlockState(p).getValue(BlockTrapDoor.FACING).getHorizontalIndex());n.addProperty("top",w.getBlockState(p).getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP);n.addProperty("open",w.getBlockState(p).getValue(BlockTrapDoor.OPEN));n.addProperty("saved",t.writeToNBT(new NBTTagCompound()).toString());n.addProperty("item",t.itemSettings().toString());JsonArray g=new JsonArray();for(TileEntityProgrammableTrapdoor m:t.group())g.add(m.getPos().toLong());n.add("group",g);n.addProperty("offset",t.canOffsetClosedLeaf());n.addProperty("hinge",t.motionHinge());return n;}
 static ItemBlock[] initialize(){
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableTrapdoor.class,new ResourceLocation("vandorlabs","programmable_trapdoor"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableDiagonalTrapdoor.class,new ResourceLocation("vandorlabs","programmable_diagonal_trapdoor"));
  BlockProgrammableTrapdoor regular=(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_TRAPDOOR,diagonal=(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR;
  net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(regular);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(diagonal);
  ItemBlock[] items={new ItemProgrammableTrapdoor(regular),new ItemDiagonalTrapdoor((BlockProgrammableDiagonalTrapdoor)diagonal)};for(ItemBlock i:items)net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(i.setRegistryName(i.getBlock().getRegistryName()));
  return items;
 }
 public static void main(String[] args)throws Exception{
  ItemBlock[] items=initialize();BlockProgrammableTrapdoor regular=(BlockProgrammableTrapdoor)items[0].getBlock(),diagonal=(BlockProgrammableTrapdoor)items[1].getBlock();
  JsonArray rows=new JsonArray();float[] hits={0,Math.nextDown(1/3F),1/3F,Math.nextUp(1/3F),.5F,Math.nextDown(2/3F),2/3F,Math.nextUp(2/3F),1};
  for(int family=0;family<2;family++)for(boolean remote:new boolean[]{false,true})for(EnumFacing side:EnumFacing.values())for(int yaw=0;yaw<4;yaw++)for(int hit=0;hit<hits.length;hit++)for(int kind=0;kind<10;kind++){
   NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(remote);EntityPlayer p=new EntityPlayer(w,new com.mojang.authlib.GameProfile(UUID.fromString("fcf30a04-a028-4e19-8cf4-02477b6ef0cd"),"TrapdoorOracle")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};p.rotationYaw=EnumFacing.getHorizontal(yaw).getHorizontalAngle();ItemStack stack=new ItemStack(items[family]);NBTTagCompound input=tag(kind);if(input!=null)stack.setTagInfo("BlockEntityTag",input);p.setHeldItem(EnumHand.MAIN_HAND,stack);String held=stack.serializeNBT().toString();BlockProgrammableTrapdoor block=family==0?regular:diagonal;
   net.minecraft.block.state.IBlockState state=block.getStateForPlacement(w,POS,side,.1F,hits[hit],.9F,0,p,EnumHand.MAIN_HAND);boolean placed=items[family].placeBlockAt(stack,p,w,POS,side,.1F,hits[hit],.9F,state);if(!placed||!held.equals(stack.serializeNBT().toString()))throw new AssertionError("Specialized placement mutated held stack");
   JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("remote",remote);row.addProperty("side",side.getIndex());row.addProperty("yaw",yaw);row.addProperty("hit",hit);row.addProperty("kind",kind);row.addProperty("input",input==null?"":input.toString());row.addProperty("initialFacing",state.getValue(BlockTrapDoor.FACING).getHorizontalIndex());row.addProperty("initialTop",state.getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP);row.add("placed",snapshot(w,POS));rows.add(row);
  }
  String json=rows.toString();Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,json.getBytes(StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(rows.size()!=8640||!hash.toString().equals("e36c4946c5500c223bec447a1f2f6263a9b73a97b375c8c060adc0df8dd70360"))throw new AssertionError("Released specialized Trapdoor placement changed "+hash);System.out.println("Actual specialized Trapdoor placement PASS cases="+rows.size()+" golden="+hash);
 }
}
