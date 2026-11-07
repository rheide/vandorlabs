package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.items.ItemDuplifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Actual tool clipboard state, title suffix, clear and failed capture retention. */
public final class CopifierClipboardLegacyChecks {
 public static void main(String[] args)throws Exception{
  Bootstrap.register();ItemDuplifier item=new ItemDuplifier();String base=item.getItemStackDisplayName(new ItemStack(Items.STICK));JsonArray rows=new JsonArray();
  for(int settings=0;settings<9;settings++)for(int name=0;name<5;name++)for(boolean extra:new boolean[]{false,true}){
   NBTTagCompound root=new NBTTagCompound();
   switch(settings){case 0:break;case 1:root.setTag(ItemDuplifier.SETTINGS_TAG,new NBTTagCompound());break;case 2:{NBTTagCompound data=new NBTTagCompound();data.setInteger("wall_texture",4);root.setTag(ItemDuplifier.SETTINGS_TAG,data);break;}case 3:root.setString(ItemDuplifier.SETTINGS_TAG,"wrong");break;case 4:root.setInteger(ItemDuplifier.SETTINGS_TAG,37);break;case 5:root.setTag(ItemDuplifier.SETTINGS_TAG,new NBTTagList());break;case 6:root.setIntArray(ItemDuplifier.SETTINGS_TAG,new int[]{1,2});break;case 7:{NBTTagCompound data=new NBTTagCompound();data.setTag("future",new NBTTagCompound());root.setTag(ItemDuplifier.SETTINGS_TAG,data);break;}default:{NBTTagCompound data=new NBTTagCompound();data.setString("future","opaque");root.setTag(ItemDuplifier.SETTINGS_TAG,data);}}
   switch(name){case 0:break;case 1:root.setString(ItemDuplifier.SOURCE_TAG,"");break;case 2:root.setString(ItemDuplifier.SOURCE_TAG,"Porthole / Block");break;case 3:root.setInteger(ItemDuplifier.SOURCE_TAG,17);break;default:root.setString(ItemDuplifier.SOURCE_TAG,"Quote \" / unicode \u00e9");}
   if(extra){root.setBoolean("DuplifierConnected",true);root.setLong("DuplifierApplyMask",0);NBTTagCompound unknown=new NBTTagCompound();unknown.setIntArray("retained",new int[]{7,9});root.setTag("ForeignData",unknown);}
   ItemStack tool=new ItemStack(Items.STICK);tool.setTagCompound(root.copy());JsonObject row=new JsonObject();row.addProperty("input",root.toString());row.addProperty("hasCopy",ItemDuplifier.hasCopy(tool));row.addProperty("suffix",item.getItemStackDisplayName(tool).substring(base.length()));
   if(ItemDuplifier.copyFrom(new NonRenderingChecks.MemoryWorld(false),new net.minecraft.util.math.BlockPos(0,80,0),tool)!=null||!root.equals(tool.getTagCompound()))throw new AssertionError("Failed capture changes clipboard");
   ItemDuplifier.clearCopyState(tool);if(tool.getTagCompound()==null)row.add("cleared",JsonNull.INSTANCE);else row.addProperty("cleared",tool.getTagCompound().toString());rows.add(row);
  }
  ItemStack empty=new ItemStack(Items.STICK);if(ItemDuplifier.hasCopy(empty))throw new AssertionError("Missing clipboard");ItemDuplifier.clearCopyState(empty);if(empty.getTagCompound()!=null)throw new AssertionError("Clear created root");
  byte[] bytes=rows.toString().getBytes(StandardCharsets.UTF_8);Path path=Paths.get(System.getProperty("vandorlabs.copifierClipboardReference"));Files.createDirectories(path.getParent());Files.write(path,bytes);
  StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("1d29802db862f643723b7c9063baba03d7f9575b28be00a02d942398b324f25e"))throw new AssertionError("Source clipboard changed: "+hash);System.out.println("Copifier clipboard Source PASS cases="+rows.size()+" sha256="+hash);
 }
}
