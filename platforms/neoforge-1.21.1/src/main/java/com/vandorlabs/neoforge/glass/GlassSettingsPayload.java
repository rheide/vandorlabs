package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed 24-byte ordinary-glass edit; saved panel facing and frames are never supplied by this packet. */
public record GlassSettingsPayload(BlockPos pos,int window,int size,int shade,int tint,int material,int layout,boolean join,boolean framed,int mode,int speed) implements CustomPacketPayload {
 public static final Type<GlassSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:glass_settings"));
 public boolean valid(){return pos!=null&&window>=0&&size>=0&&size<=2&&GlassTextures.valid(shade)&&GlassTextures.validTint(tint)&&(material==-1||ModernFinishCatalog.validChoice(material))&&TextureLayoutMode.valid(layout)&&mode>=0&&mode<=2&&speed>=0&&speed<=2;}
 private static boolean flag(RegistryFriendlyByteBuf b){int value=b.readUnsignedByte();if(value>1)throw new IllegalArgumentException("Invalid Glass flag");return value!=0;}
 public static final StreamCodec<RegistryFriendlyByteBuf,GlassSettingsPayload> CODEC=new StreamCodec<>(){
  public GlassSettingsPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=24)throw new IllegalArgumentException("Invalid Glass edit length");var value=new GlassSettingsPayload(b.readBlockPos(),b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readInt(),b.readUnsignedByte(),flag(b),flag(b),b.readUnsignedByte(),b.readUnsignedByte());if(!value.valid())throw new IllegalArgumentException("Invalid Glass edit");return value;}
  public void encode(RegistryFriendlyByteBuf b,GlassSettingsPayload v){if(!v.valid())throw new IllegalArgumentException("Invalid Glass edit");b.writeBlockPos(v.pos);b.writeInt(v.window);b.writeByte(v.size);b.writeByte(v.shade);b.writeByte(v.tint);b.writeInt(v.material);b.writeByte(v.layout);b.writeBoolean(v.join);b.writeBoolean(v.framed);b.writeByte(v.mode);b.writeByte(v.speed);}
 };
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)NativeGlassMenu.apply(player,payload);}));registrar.playToServer(GlassFramePayload.TYPE,GlassFramePayload.CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)NativeGlassMenu.frames(player,payload);}));}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
