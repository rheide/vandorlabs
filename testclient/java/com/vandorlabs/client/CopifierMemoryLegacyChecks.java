package com.vandorlabs.client;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.nbt.*;

/** Exact released retained-tree accounting before sharing the policy across NBT APIs. */
public final class CopifierMemoryLegacyChecks {
 private static NBTBase sample(int n,int depth){switch(n%12){case 0:return new NBTTagByte((byte)n);case 1:return new NBTTagShort((short)n);case 2:return new NBTTagInt(n);case 3:return new NBTTagLong(n);case 4:return new NBTTagFloat(n/7F);case 5:return new NBTTagDouble(n/11D);case 6:return new NBTTagString("copied "+n+" \\"+" quoted \" "+"\u2603 \uD83D\uDE80");case 7:return new NBTTagByteArray(new byte[n%43]);case 8:return new NBTTagIntArray(new int[n%79]);case 9:{NBTTagList list=new NBTTagList();for(int i=0;i<n%13;i++)list.appendTag(new NBTTagInt(i));return list;}default:{NBTTagCompound compound=new NBTTagCompound();if(depth<4)for(int i=0;i<5;i++)compound.setTag("nested_"+i,sample(n+i+7,depth+1));return compound;}}}
 public static void main(String[] args)throws Exception{
  java.lang.reflect.Method bytes=com.vandorlabs.items.DuplifierUndo.class.getDeclaredMethod("bytes",NBTBase.class);bytes.setAccessible(true);JsonArray rows=new JsonArray();for(int i=0;i<192;i++){NBTTagCompound root=new NBTTagCompound();root.setTag("value",sample(i,0));JsonObject row=new JsonObject();row.addProperty("tag",root.toString());row.addProperty("bytes",((Number)bytes.invoke(null,root)).longValue());rows.add(row);}
  byte[] raw=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path target=Paths.get(args[0]);Files.createDirectories(target.getParent());Files.write(target,raw);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("e7379a267e6013a04fa1ee9621993088ec833d9968eb444c93295a6c862675ae"))throw new AssertionError("Source memory accounting changed: "+hash);System.out.println("Copifier memory Source PASS cases="+rows.size()+" sha256="+hash);
 }
}
