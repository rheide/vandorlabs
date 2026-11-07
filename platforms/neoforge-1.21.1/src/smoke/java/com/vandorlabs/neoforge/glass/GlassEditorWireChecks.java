package com.vandorlabs.neoforge.glass;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Exact native transport bytes and malformed-input boundaries, independent of menu/world authorization. */
public final class GlassEditorWireChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError(text);}
 private static byte[] encode(GlassSettingsPayload value){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{GlassSettingsPayload.CODEC.encode(b,value);byte[] out=new byte[b.readableBytes()];b.readBytes(out);return out;}finally{b.release();}}
 private static GlassSettingsPayload decode(byte[] bytes){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),RegistryAccess.EMPTY);try{return GlassSettingsPayload.CODEC.decode(b);}finally{b.release();}}
 private static void refused(byte[] bytes){try{decode(bytes);throw new AssertionError("Malformed Glass edit accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception{int profiles=0;for(int size=0;size<3;size++)for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(int flags=0;flags<4;flags++)for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
  var value=new GlassSettingsPayload(new BlockPos(-30000000,64,29999999),17,size,shade,tint,-1,layout,(flags&1)!=0,(flags&2)!=0,mode,speed);byte[] bytes=encode(value);need(bytes.length==24&&decode(bytes).equals(value),"fixed24 roundtrip");profiles++;
 }
 byte[] valid=encode(new GlassSettingsPayload(BlockPos.ZERO,1,1,0,1,-1,1,true,true,1,1));for(int length=0;length<=32;length++)if(length!=24)refused(Arrays.copyOf(valid,length));
 for(int at:new int[]{20,21}){var bytes=valid.clone();bytes[at]=2;refused(bytes);}for(int at:new int[]{12,13,14,19,22,23}){var bytes=valid.clone();bytes[at]=(byte)255;refused(bytes);}
 int sourceCases=0;try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=com.google.gson.JsonParser.parseString(line).getAsJsonObject();byte[] raw=java.util.Base64.getDecoder().decode(row.get("bytes").getAsString());need(raw.length==28,"actual released Source payload28");var source=java.nio.ByteBuffer.wrap(raw);long packed=source.getLong();need(packed==((-30000000L&0x3ffffffL)<<38|(64L&0xfffL)<<26|(29999999L&0x3ffffffL)),"Source legacy position packing");int size=source.getInt(),shade=source.getInt();boolean join=source.get()!=0;int tint=source.get()&255;boolean framed=source.get()!=0;int material=source.getInt();boolean stretch=source.get()!=0;int layout=source.get()&255;need(source.get()==1,"Source playback extension marker");int mode=source.get()&255,speed=source.get()&255;var projected=new GlassSettingsPayload(new BlockPos(-30000000,64,29999999),17,size,shade,tint,material,layout,join,framed,mode,speed);need(stretch==(layout!=1)&&decode(encode(projected)).equals(projected),"all released Source primary fields survive native transport");need(size==row.get("size").getAsInt()&&shade==row.get("shade").getAsInt()&&tint==row.get("tint").getAsInt()&&layout==row.get("layout").getAsInt()&&join==row.get("join").getAsBoolean()&&framed==row.get("framed").getAsBoolean()&&mode==row.get("mode").getAsInt()&&speed==row.get("speed").getAsInt(),"Source reflection roundtrip corpus agrees with actual bytes");sourceCases++;}}need(sourceCases==profiles,"Source/native complete editor matrix");
 var negative=valid.clone();Arrays.fill(negative,8,12,(byte)255);refused(negative);System.out.println("[vandorlabs][glass-editor-wire] PASS profiles="+profiles+" SourceProfiles="+sourceCases+" checks="+checks+" Source28toNative24PayloadBytes fixed24bytes/boundedDecode/strictFlags/noVariableAllocation");
 }
 private GlassEditorWireChecks(){}
}
