package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Actual ordinary-Glass Copifier capture and typed partial application before the native backend. */
final class CopifierGlassLegacyChecks {
 static void run(){try{
  BlockProgrammableGlass block=new BlockProgrammableGlass("programmable_glass");BlockPos pos=new BlockPos(4,80,4),target=pos.east(3);JsonArray rows=new JsonArray();
  String[] partial={"{glass_size:2}","{glass_size:-1}","{glass_shade:999}","{glass_tint:999,framed:0b}","{glass_material:-1}","{glass_material:-2}","{glass_layout:{Stretch:0b}}","{glass_layout:{Stretch:1b,Mode:2,GlassPlaybackMode:0b,GlassPlaybackSpeed:2b}}","{glass_layout:{Stretch:1b,Mode:0,GlassPlaybackSpeed:0b}}","{FrameAppearance:{FrameBare:1b}}","{wall_texture:7}","{glass_layout:{Stretch:0b,Mode:99}}","{glass_size:\"wrong\"}","{join:0b,framed:0b,glass_shade:2,glass_tint:2}"};
  for(int meta=0;meta<16;meta++)for(int profile=0;profile<9;profile++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(pos,block.getStateFromMeta(meta),2);
   TileEntityProgrammableGlass source=(TileEntityProgrammableGlass)world.getTileEntity(pos);NBTTagCompound input=new NBTTagCompound();
   if(profile==1)input=source.writeToNBT(new NBTTagCompound());
   if(profile>=2){input.setInteger("GlassSize",profile%2);input.setInteger("GlassShade",profile%4);input.setInteger("GlassTint",profile%5);input.setInteger("GlassMaterial",7);input.setInteger("GlassTextureLayout",profile%3);input.setBoolean("GlassStretch",(profile&1)==0);input.setBoolean("GlassJoin",(profile&1)!=0);input.setBoolean("GlassFramed",profile%3!=0);input.setInteger("GlassPanelFacing",7);input.setByte("GlassPlaybackMode",(byte)(profile%3));input.setByte("GlassPlaybackSpeed",(byte)((profile+1)%3));}
   if(profile==3){input.removeTag("GlassSize");input.setInteger("GlassTextureLayout",99);}
   if(profile==4){input.setString("GlassSize","wrong");input.setString("GlassTint","wrong");input.setInteger("GlassMaterial",-2);}
   if(profile==5){input.setInteger("GlassSize",2);input.setBoolean("FrameBare",true);input.setBoolean("FrameTile",true);}
   if(profile==6){input.setInteger("GlassShade",-1);input.setInteger("GlassTint",99);input.setString("GlassPlaybackMode","wrong");}
   input.setInteger("x",pos.getX());input.setInteger("y",pos.getY());input.setInteger("z",pos.getZ());source.readFromNBT(input.copy());source.onLoad();NBTTagCompound captured=ProgrammableSettings.capture(world,pos);world.setBlockState(target,block.getStateFromMeta(meta^3),2);TileEntityProgrammableGlass destination=(TileEntityProgrammableGlass)world.getTileEntity(target);NBTTagCompound before=new NBTTagCompound();before.setInteger("GlassSize",0);before.setInteger("GlassShade",1);before.setInteger("GlassPanelFacing",2);before.setByte("GlassPlaybackMode",(byte)2);before.setByte("GlassPlaybackSpeed",(byte)0);before.setInteger("x",target.getX());before.setInteger("y",target.getY());before.setInteger("z",target.getZ());destination.readFromNBT(before.copy());destination.onLoad();
   JsonObject row=new JsonObject();row.addProperty("meta",meta);row.addProperty("stateSize",world.getBlockState(pos).getValue(BlockProgrammableGlass.SIZE));row.addProperty("input",input.toString());row.addProperty("capture",captured.toString());row.addProperty("target",before.toString());row.addProperty("values",captured.toString());row.addProperty("applied",ProgrammableSettings.apply(world,target,captured.copy()));row.addProperty("after",ProgrammableSettings.capture(world,target).toString());if(world.getBlockState(target)!=block.getStateFromMeta(meta^3))throw new AssertionError("Copy changed Glass block metadata");rows.add(row);
   if(profile==0)for(String text:partial){destination.readFromNBT(before.copy());destination.onLoad();NBTTagCompound values=JsonToNBT.getTagFromJson(text);JsonObject p=new JsonObject();p.addProperty("meta",meta);p.addProperty("stateSize",world.getBlockState(target).getValue(BlockProgrammableGlass.SIZE));p.addProperty("target",before.toString());p.addProperty("values",values.toString());p.addProperty("applied",ProgrammableSettings.apply(world,target,values.copy()));p.addProperty("after",ProgrammableSettings.capture(world,target).toString());rows.add(p);}
  }
  byte[] raw=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path file=Paths.get(System.getProperty("vandorlabs.copifierGlassReference"));Files.createDirectories(file.getParent());Files.write(file,raw);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("2863325c1e85694b9ace59f1845f9bdfca322654a9220375336aa7c214aca5db"))throw new AssertionError("Source Glass copied settings changed: "+hash);System.out.println("Copifier Glass Source PASS cases="+rows.size()+" sha256="+hash);
 }catch(Exception failure){throw new AssertionError(failure);}}
}
