package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Actual apply callback: malformed global fields must refuse before changing a valid housing edit. */
final class CopifierPreflightLegacyChecks {
 static void run(){try{
  String[] paths={"texture_layout","door_texture_layout","trapdoor_texture_layout","glass_layout.Mode","glass_layout.GlassPlaybackMode","glass_layout.GlassPlaybackSpeed","stair_tread","stair_roll","Transmission.Channels","Transmission.Range","Transmission.Local"};
  int[] numbers={-3,-1,0,1,2,3,4,8,16,17,32,Integer.MAX_VALUE};JsonArray rows=new JsonArray();BlockPos pos=new BlockPos(0,80,0);
  for(String path:paths)for(int type=0;type<10;type++)for(int number:numbers){
   NBTTagCompound values=new NBTTagCompound();values.setInteger("wall_texture",4);String[] parts=path.split("\\.");NBTTagCompound target=values;
   if(parts.length>1){target=new NBTTagCompound();values.setTag(parts[0],target);if(parts[0].equals("Transmission")){target.setIntArray("Channels",new int[]{1,3});target.setInteger("Range",16);target.setBoolean("Local",false);}}
   String key=parts[parts.length-1];target.removeTag(key);
   switch(type){case 0:break;case 1:target.setByte(key,(byte)number);break;case 2:target.setShort(key,(short)number);break;case 3:target.setInteger(key,number);break;case 4:target.setLong(key,number);break;case 5:target.setFloat(key,number);break;case 6:target.setString(key,"wrong");break;case 7:target.setIntArray(key,new int[]{number,3});break;case 8:target.setTag(key,new NBTTagCompound());break;default:int[] many=new int[65];java.util.Arrays.fill(many,number);target.setIntArray(key,many);}
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(pos,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());boolean accepted=ProgrammableSettings.apply(world,pos,values.copy());
   if(!accepted&&!before.equals(tile.writeToNBT(new NBTTagCompound())))throw new AssertionError("Refusal mutated target "+path);
   if(accepted&&tile.getHousingTexture()!=4)throw new AssertionError("Accepted edit missing");JsonObject row=new JsonObject();row.addProperty("path",path);row.addProperty("type",type);row.addProperty("number",number);row.addProperty("values",values.toString());row.addProperty("accepted",accepted);rows.add(row);
  }
  byte[] bytes=rows.toString().getBytes(StandardCharsets.UTF_8);Path file=Paths.get(System.getProperty("vandorlabs.copifierPreflightReference"));Files.createDirectories(file.getParent());Files.write(file,bytes);
  StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("9f3bb36ff486f7d2ebffbbe15f098fb6d9da222c3a3d0637061209bcff4af15d"))throw new AssertionError("Source preflight changed: "+hash);System.out.println("Copifier preflight Source PASS cases="+rows.size()+" sha256="+hash);
 }catch(Exception failure){throw new AssertionError(failure);}}
}
