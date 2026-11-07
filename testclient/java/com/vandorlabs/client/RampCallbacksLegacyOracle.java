package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
/** Actual copied-settings and configured-placement callbacks in an empty fixed-clock memory world. */
public final class RampCallbacksLegacyOracle {
 private static final BlockPos POS=new BlockPos(4,80,4);
 private static final String[] EDITS={"{}","{ramp_start_half:-3,ramp_end_half:7}","{ramp_start:2,ramp_end:-5}","{ramp_tread_pixels:3}","{ramp_tread_pixels:1}","{ramp_speed:99}","{ramp_speed:2}","{ramp_direction:0}","{ramp_direction:5}","{ramp_travel:1}","{ramp_travel:2}","{ramp_vertical_layout:1b}","{MoatMode:1b,ramp_lift:1b}","{ramp_escalator:1,ramp_start_half:7}","{ramp_interpolation:99}","{ramp_start_half:33}","{ramp_start_half:\"wrong\",ramp_end_half:5}","{redstone_channel:[I;23,29]}","{redstone_channel:\"wrong\"}","{Transmission:{Channels:[I;31],Range:64,Local:1b}}"};
 private static EntityPlayer player(NonRenderingChecks.MemoryWorld w){EntityPlayer p=new EntityPlayer(w,new com.mojang.authlib.GameProfile(new UUID(0,77),"RampOracle")){public boolean isCreative(){return true;}public boolean isSpectator(){return false;}};p.setPosition(4.5,80,3);p.capabilities.allowEdit=true;return p;}
 private static TileEntityRampController install(NonRenderingChecks.MemoryWorld w,BlockRampController block,int meta,NBTTagCompound raw){w.setBlockState(POS,block.getStateFromMeta(meta),2);TileEntityRampController tile=(TileEntityRampController)w.getTileEntity(POS);if(raw!=null){NBTTagCompound input=raw.copy();input.setInteger("x",POS.getX());input.setInteger("y",POS.getY());input.setInteger("z",POS.getZ());tile.readFromNBT(input);}return tile;}
 private static void retire(NonRenderingChecks.MemoryWorld w)throws Exception{RampSettingsLegacyOracle.retire(w);}
 public static void main(String[] args)throws Exception{List<BlockRampController> blocks=RampSettingsLegacyOracle.initialize();List<String> lines=new ArrayList<>();for(BlockRampController block:blocks)for(int meta=0;meta<16;meta++)for(String text:EDITS){
  NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(false);w.time=100;TileEntityRampController tile=install(w,block,meta,null);EntityPlayer p=player(w);NBTTagCompound values=JsonToNBT.getTagFromJson(text);JsonObject row=new JsonObject();row.addProperty("kind","copied");row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("values",text);row.add("before",RampSettingsLegacyOracle.snapshot(w,block,tile,p));row.addProperty("applied",ProgrammableSettings.apply(w,POS,values,p));row.add("after",RampSettingsLegacyOracle.snapshot(w,block,tile,p));if(!values.toString().equals(JsonToNBT.getTagFromJson(text).toString())||tile.area()!=0||!tile.vehicleCells().isEmpty())throw new AssertionError("Clipboard changed or empty platform acquired ownership");lines.add(row.toString());retire(w);
 }
 for(BlockRampController block:blocks)for(int meta=0;meta<16;meta++)for(int profile=0;profile<5;profile++){
  NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(false);w.time=100;EntityPlayer p=player(w);TileEntityRampController source=install(w,block,meta,null);NBTTagCompound values=JsonToNBT.getTagFromJson(profile==0?"{}":profile==1?"{ramp_start_half:-3,ramp_end_half:7}":profile==2?"{ramp_tread_pixels:4,ramp_speed:2,redstone_channel:[I;13,17]}":profile==3?"{ramp_vertical_layout:1b}":"{ramp_escalator:1}");ProgrammableSettings.apply(w,POS,values,p);ItemStack picked=block.getPickBlock(w.getBlockState(POS),null,w,POS,p);NBTTagCompound stackBefore=picked.serializeNBT().copy();source.invalidate();w.removeTileEntity(POS);TileEntityRampController target=install(w,block,meta,null);JsonObject row=new JsonObject();row.addProperty("kind","placement");row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("item",stackBefore.toString());block.onBlockPlacedBy(w,POS,w.getBlockState(POS),p,picked);row.add("after",RampSettingsLegacyOracle.snapshot(w,block,target,p));if(!stackBefore.equals(picked.serializeNBT())||target.area()!=0||!target.vehicleCells().isEmpty())throw new AssertionError("Configured placement changed input or transferred ownership");lines.add(row.toString());retire(w);
 }
 if(lines.size()!=800)throw new AssertionError("callback coverage count");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,lines,java.nio.charset.StandardCharsets.UTF_8);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("b471d189a5d2a971b7df6c80cbea8c13f5a1673bf05e5d5a50d645b377f14d03"))throw new AssertionError("Changed callback golden "+hash);System.out.println("Ramp Source callbacks PASS copied=640 configuredPlacement=160 emptyPlatform/noOwnershipTransfer; authorization and journal runtime not established");}
}
