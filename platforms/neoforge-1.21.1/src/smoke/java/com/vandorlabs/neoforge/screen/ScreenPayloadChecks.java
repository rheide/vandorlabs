package com.vandorlabs.neoforge.screen;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
/** Native wire byte round trips and bounded malformed bodies, independent of world authority checks. */
public final class ScreenPayloadChecks {
 public static void main(String[] args){var catalog=NativeScreenCatalog.INSTANCE;Object[] edits={new NativeScreenDisplayEdit(catalog.screens().get(0),true,false,1,true,2,catalog.defaultInput(),catalog.defaultInput(),true,39,ChannelList.of(1,4)),new NativeScreenSurfaceEdit(0,-2),new NativeScreenRowEdit("Rows",java.util.List.of(new NativeScreenRowEdit.Row("Slider",ChannelList.of(1,5),true,false,2,13))),new NativeScreenActivationEdit(0,0,-1,-1,-1,false),new NativeScreenFrameEdit(new com.vandorlabs.persistence.FrameSettings(catalog.defaultFrame(),-1,-1,1,-1,-1,false,true,false,false)),new NativeScreenLayoutEdit(2),new NativeScreenTransmissionEdit(ChannelList.of(2,7),256,true)};int bytes=0,cases=0;for(int op=0;op<edits.length;op++){var payload=new ScreenSettingsPayload(new BlockPos(7,80,-19),31,op,0,edits[op]);var b=buffer();try{ScreenSettingsPayload.CODEC.encode(b,payload);bytes+=b.readableBytes();byte[] original=new byte[b.readableBytes()];b.getBytes(0,original);var decoded=ScreenSettingsPayload.CODEC.decode(b);var again=buffer();try{ScreenSettingsPayload.CODEC.encode(again,decoded);byte[] round=new byte[again.readableBytes()];again.getBytes(0,round);if(!java.util.Arrays.equals(original,round))throw new AssertionError("Screen wire changed "+op);}finally{again.release();}for(int length=0;length<original.length;length++){var truncated=buffer();try{truncated.writeBytes(original,0,length);reject(truncated);cases++;}finally{truncated.release();}}var trailing=buffer();try{trailing.writeBytes(original).writeByte(0);reject(trailing);cases++;}finally{trailing.release();}}finally{b.release();}}
  for(int count:new int[]{65,255}){var malformed=buffer();try{malformed.writeBlockPos(BlockPos.ZERO).writeInt(1).writeByte(ScreenSettingsPayload.TRANSMISSION).writeByte(0).writeByte(count);reject(malformed);cases++;}finally{malformed.release();}}
  for(int count:new int[]{9,255}){var malformed=buffer();try{malformed.writeBlockPos(BlockPos.ZERO).writeInt(1).writeByte(ScreenSettingsPayload.ROWS).writeByte(0).writeUtf("Rows").writeByte(count);reject(malformed);cases++;}finally{malformed.release();}}
  System.out.println("Native Screen editor payload PASS operations=7 bytes="+bytes+" malformed="+cases+" no runtime fields");
 }
 private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
 private static void reject(RegistryFriendlyByteBuf b){try{ScreenSettingsPayload.CODEC.decode(b);}catch(RuntimeException expected){return;}throw new AssertionError("Malformed Screen editor body accepted");}
}
