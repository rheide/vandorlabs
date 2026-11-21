package com.vandorlabs.neoforge.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Diagonal Corner Glass size and slope from the open Glass dialog; the bevel's turn is kept. */
public record CornerGlassGeometryPayload(BlockPos pos,int window,int size,boolean inverted,boolean far) implements CustomPacketPayload {
 public static final Type<CornerGlassGeometryPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:corner_glass_geometry"));
 public boolean valid(){return pos!=null&&window>=0&&com.vandorlabs.render.DiagonalCornerRules.valid(size);}
 public static final StreamCodec<RegistryFriendlyByteBuf,CornerGlassGeometryPayload> CODEC=new StreamCodec<>(){
  public CornerGlassGeometryPayload decode(RegistryFriendlyByteBuf b){
   if(b.readableBytes()!=14)throw new IllegalArgumentException("Invalid corner glass geometry length");
   var pos=b.readBlockPos();int window=b.readInt(),size=b.readUnsignedByte(),flags=b.readUnsignedByte();
   if((flags&~3)!=0)throw new IllegalArgumentException("Invalid corner glass geometry flags");
   var value=new CornerGlassGeometryPayload(pos,window,size,(flags&1)!=0,(flags&2)!=0);
   if(!value.valid())throw new IllegalArgumentException("Invalid corner glass geometry");return value;
  }
  public void encode(RegistryFriendlyByteBuf b,CornerGlassGeometryPayload v){
   if(!v.valid())throw new IllegalArgumentException("Invalid corner glass geometry");
   b.writeBlockPos(v.pos);b.writeInt(v.window);b.writeByte(v.size);b.writeByte((v.inverted?1:0)|(v.far?2:0));
  }
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
