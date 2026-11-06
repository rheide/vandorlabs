package com.vandorlabs.neoforge.segmented;
import com.google.gson.*;
import io.netty.buffer.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.nio.file.*;
/** Actual released wire bytes, plus bounded decoding and pose-grace boundaries. */
public final class SegmentedUseChecks {
 public static void main(String[] args)throws Exception{int count=0;for(var element:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()){String hex=element.getAsJsonObject().get("hex").getAsString();var input=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex)),RegistryAccess.EMPTY);var output=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{var value=SegmentedUsePayload.CODEC.decode(input);SegmentedUsePayload.CODEC.encode(output,value);if(input.isReadable()||output.readableBytes()!=64||!ByteBufUtil.hexDump(output).equals(hex))throw new AssertionError("Released use body drift "+count);count++;}finally{input.release();output.release();}}
  for(int length:new int[]{0,8,63,65,4096}){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(length).writeZero(length),RegistryAccess.EMPTY);try{reject(b);}finally{b.release();}}
  String first=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray().get(0).getAsJsonObject().get("hex").getAsString();for(int mode=0;mode<5;mode++){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(first)),RegistryAccess.EMPTY);try{if(mode==0)b.setDouble(8,Double.NaN);if(mode==1)b.setLong(48,-1);if(mode==2)b.setDouble(56,Double.POSITIVE_INFINITY);if(mode==3)b.setDouble(56,-.01);if(mode==4)b.setDouble(56,1.01);reject(b);}finally{b.release();}}
  if(!SegmentedUsePayload.poseAllowed(.5,0,1,130,100,10)||SegmentedUsePayload.poseAllowed(.5,0,1,131,100,10)||SegmentedUsePayload.poseAllowed(.5,0,0,100,100,10)||!SegmentedUsePayload.poseAllowed(.5,.5,0,1000,0,10))throw new AssertionError("Released current-stroke/at-rest grace");
  System.out.println("Native Segmented use wire PASS actual-source cases="+count+" bytes=64 fixed bounds identity/sequence/pose grace");
 }
 private static void reject(RegistryFriendlyByteBuf buffer){try{SegmentedUsePayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){return;}throw new AssertionError("Malformed interaction accepted");}
}
