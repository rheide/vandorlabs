package com.vandorlabs.neoforge;
import com.vandorlabs.surface.TextureJoinSettings;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
/** New fields and bounded packet codec keep every original join default and external material identity. */
public final class NativeTextureJoinChecks {
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  need(TextureJoinSettings.read(new ModernHousingTags(new CompoundTag())).equals(TextureJoinSettings.DEFAULT),"older NBT defaults");int count=0;
  for(int direction=0;direction<6;direction++)for(int parity=0;parity<3;parity++){
   var settings=new TextureJoinSettings(direction,parity);var tag=new CompoundTag();tag.putString("opaque","retained");settings.write(new ModernHousingTags(tag));need(TextureJoinSettings.read(new ModernHousingTags(tag)).equals(settings)&&tag.getString("opaque").equals("retained"),"native tags round trip");
   var packet=new TextureJoinPayload(new BlockPos(-17,80,19),7,direction,parity);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{TextureJoinPayload.CODEC.encode(b,packet);need(b.readableBytes()==14,"bounded fixed payload bytes");need(TextureJoinPayload.CODEC.decode(b).equals(packet)&&b.readableBytes()==0,"packet round trip");}finally{b.release();}count++;
  }
  for(int bytes:new int[]{0,1,13,15,4096}){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(new byte[bytes]),RegistryAccess.EMPTY);try{boolean rejected=false;try{TextureJoinPayload.CODEC.decode(b);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"bad length rejected before reading");}finally{b.release();}}
  System.out.println("Native texture joins PASS cases="+count+" nativeNBT/defaults/opaqueFields/fixed14byteRoundtrip/malformedLengths; world authorization checked separately");
 }
}
