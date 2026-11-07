package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.tiles.GlassTextures;
import io.netty.buffer.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual released primary editor serialization, including its optional playback extension. */
public final class GlassEditorWireLegacyOracle {
 private static Object field(MessageProgrammableGlass value,String name)throws Exception{Field f=MessageProgrammableGlass.class.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 public static void main(String[] args)throws Exception{File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;try(PrintWriter out=new PrintWriter(file,"UTF-8")){
  for(int size=0;size<3;size++)for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(int flags=0;flags<4;flags++)for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
   MessageProgrammableGlass packet=new MessageProgrammableGlass(new BlockPos(-30000000,64,29999999),size,shade,(flags&1)!=0).withAppearance(tint,(flags&2)!=0).withMaterial(-1).withTextureLayout(layout).withPlayback(mode,speed);ByteBuf bytes=Unpooled.buffer();try{packet.toBytes(bytes);if(bytes.readableBytes()!=28)throw new AssertionError("Source editor bytes");byte[] raw=new byte[28];bytes.getBytes(bytes.readerIndex(),raw);MessageProgrammableGlass decoded=new MessageProgrammableGlass();decoded.fromBytes(bytes);for(String name:new String[]{"pos","size","shade","join","tint","framed","material","stretch","layout","playbackMode","playbackSpeed"})if(!field(packet,name).equals(field(decoded,name)))throw new AssertionError("Actual Source roundtrip "+name);JsonObject row=new JsonObject();row.addProperty("size",size);row.addProperty("shade",shade);row.addProperty("tint",tint);row.addProperty("layout",layout);row.addProperty("join",(flags&1)!=0);row.addProperty("framed",(flags&2)!=0);row.addProperty("mode",mode);row.addProperty("speed",speed);row.addProperty("bytes",Base64.getEncoder().encodeToString(raw));out.println(gson.toJson(row));profiles++;}finally{bytes.release();}
  }
 }
 SegmentedReferenceGolden.check(file,"fc33ecb1192f068b88e348bf742b92e23cbcb9cd8de29a971c00245d57c219c2");
 System.out.println("Actual ordinary Glass editor wire PASS profiles="+profiles+" SourceBytes=28 file="+file);
 }
}
