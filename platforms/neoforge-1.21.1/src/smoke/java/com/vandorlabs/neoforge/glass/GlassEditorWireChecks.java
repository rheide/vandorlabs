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
 public static void main(String[] args){int profiles=0;for(int size=0;size<3;size++)for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(int flags=0;flags<4;flags++)for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
  var value=new GlassSettingsPayload(new BlockPos(-30000000,64,29999999),17,size,shade,tint,-1,layout,(flags&1)!=0,(flags&2)!=0,mode,speed);byte[] bytes=encode(value);need(bytes.length==24&&decode(bytes).equals(value),"fixed24 roundtrip");profiles++;
 }
 byte[] valid=encode(new GlassSettingsPayload(BlockPos.ZERO,1,1,0,1,-1,1,true,true,1,1));for(int length=0;length<=32;length++)if(length!=24)refused(Arrays.copyOf(valid,length));
 for(int at:new int[]{20,21}){var bytes=valid.clone();bytes[at]=2;refused(bytes);}for(int at:new int[]{12,13,14,19,22,23}){var bytes=valid.clone();bytes[at]=(byte)255;refused(bytes);}
 var negative=valid.clone();Arrays.fill(negative,8,12,(byte)255);refused(negative);System.out.println("[vandorlabs][glass-editor-wire] PASS profiles="+profiles+" checks="+checks+" fixed24bytes/boundedDecode/strictFlags/noVariableAllocation");
 }
 private GlassEditorWireChecks(){}
}
