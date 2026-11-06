package com.vandorlabs.neoforge.screen;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
/** Fixed request: no NBT, client stack, dimension or unbounded collection. */
public final class ScreenPickWireChecks {
 public static void main(String[] args){int cases=0;for(int slot=0;slot<9;slot++)for(int state:new int[]{0,1,32767}){var b=buffer();try{var expected=new ScreenPickPayload(new BlockPos(-23,120,41),slot,state);ScreenPickPayload.CODEC.encode(b,expected);if(b.readableBytes()!=13||!expected.equals(ScreenPickPayload.CODEC.decode(b)))throw new AssertionError("Pick request round trip");cases++;}finally{b.release();}}for(int length=0;length<16;length++)if(length!=13){var b=buffer();try{b.writeZero(length);reject(b);cases++;}finally{b.release();}}for(int slot:new int[]{9,255}){var b=buffer();try{b.writeLong(0).writeByte(slot).writeInt(0);reject(b);cases++;}finally{b.release();}}for(int state:new int[]{-1,32768,Integer.MAX_VALUE}){var b=buffer();try{b.writeLong(0).writeByte(0).writeInt(state);reject(b);cases++;}finally{b.release();}}System.out.println("Native Screen pick wire PASS cases="+cases+" bytes=13 no client ownership payload");}
 private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
 private static void reject(RegistryFriendlyByteBuf b){try{ScreenPickPayload.CODEC.decode(b);}catch(RuntimeException expected){return;}throw new AssertionError("Malformed Screen pick accepted");}
}
