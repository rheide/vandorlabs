package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.NativeFrameWire;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.persistence.FrameSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed frame edit for the same authenticated Glass menu. */
public record GlassFramePayload(BlockPos pos,int window,FrameSettings frames) implements CustomPacketPayload {
 public static final Type<GlassFramePayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:glass_frames"));
 public static boolean valid(FrameSettings frames){return frames!=null&&NativeWallEntity.valid(frames);}
 public static final StreamCodec<RegistryFriendlyByteBuf,GlassFramePayload> CODEC=new StreamCodec<>(){
  public GlassFramePayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=12+NativeFrameWire.BYTES)throw new IllegalArgumentException("Invalid Glass frame length");var v=new GlassFramePayload(b.readBlockPos(),b.readInt(),NativeFrameWire.read(b));if(v.window<0||!valid(v.frames))throw new IllegalArgumentException("Invalid Glass frames");return v;}
  public void encode(RegistryFriendlyByteBuf b,GlassFramePayload v){if(v.pos==null||v.window<0||!valid(v.frames))throw new IllegalArgumentException("Invalid Glass frames");b.writeBlockPos(v.pos);b.writeInt(v.window);NativeFrameWire.write(b,v.frames);}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
