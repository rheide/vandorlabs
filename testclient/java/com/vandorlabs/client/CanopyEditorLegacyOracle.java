package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.network.MessageLargeCanopy;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Actual released edit constructor/encoder/decoder, independent of the native bounded protocol. */
public final class CanopyEditorLegacyOracle {
 public static void main(String[] args)throws Exception {
  Field valid=MessageLargeCanopy.class.getDeclaredField("valid"),settings=MessageLargeCanopy.class.getDeclaredField("settings");valid.setAccessible(true);settings.setAccessible(true);
  File file=new File(args[0]);file.getParentFile().mkdirs();int profiles=0;
  try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),"UTF-8"))){
   for(int shade=0;shade<19;shade++)for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(int bank=0;bank<2;bank++){
    int i=profiles++;LargeCanopySettings d=new LargeCanopySettings();d.depth=new int[]{0,16,80}[i%3];d.scale=new int[]{10,71,100}[(i/3)%3];d.shade=shade;d.tint=tint;d.mount=i%4;d.motion=(i/4)%2;d.hinge=(i/8)%4;d.angle=(i/32)%4;d.openable=(i&1)!=0;d.redstone=(i&2)!=0;d.slideForward=(i&4)!=0;d.activateOnPower=(i&8)!=0;d.textureLayout(layout);d.channels=bank==0?ChannelList.EMPTY:ChannelList.of(1,17,Integer.MAX_VALUE);
    MessageLargeCanopy request=new MessageLargeCanopy(new BlockPos(-30000000,73,29999999),d),decoded=new MessageLargeCanopy();int bytes=varBuffer(request,decoded);
    if(!valid.getBoolean(decoded))throw new AssertionError("Released edit refused "+i);
    LargeCanopySettings actual=(LargeCanopySettings)settings.get(decoded);NBTTagCompound saved=new NBTTagCompound();actual.write(saved);
    JsonObject row=new JsonObject();row.addProperty("profile",i);row.addProperty("bytes",bytes);row.addProperty("key",actual.key());row.add("saved",CanopySettingsLegacyOracle.tags(saved));out.println(row);
   }
  }
  System.out.println("[vandorlabs][canopy-editor-source] PASS profiles="+profiles);
 }
 private static int varBuffer(MessageLargeCanopy request,MessageLargeCanopy decoded){io.netty.buffer.ByteBuf buffer=Unpooled.buffer();try{request.toBytes(buffer);int bytes=buffer.readableBytes();decoded.fromBytes(buffer);if(buffer.isReadable())throw new AssertionError("Trailing released edit bytes");return bytes;}finally{buffer.release();}}
}
