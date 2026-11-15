package com.vandorlabs.neoforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Typed appearance only; a packet cannot claim a surface or supply a saved group. */
public record NativeSurfaceSettingsPayload(int window,BlockPos pos,String on,String off,int mode) implements CustomPacketPayload {
    public static final Type<NativeSurfaceSettingsPayload> TYPE=new Type<>(NativeSurfaceBindings.id("surface_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeSurfaceSettingsPayload> CODEC=new StreamCodec<>(){
        public NativeSurfaceSettingsPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()<17||buffer.readableBytes()>1045)throw new IllegalArgumentException("Surface packet bound");var result=new NativeSurfaceSettingsPayload(buffer.readInt(),buffer.readBlockPos(),buffer.readUtf(128),buffer.readUtf(128),buffer.readUnsignedByte());if(buffer.isReadable()||result.mode<0||result.mode>2||!NativeSurfaceArtwork.valid(result.on)||!NativeSurfaceArtwork.valid(result.off))throw new IllegalArgumentException("Surface packet choices");return result;}
        public void encode(RegistryFriendlyByteBuf buffer,NativeSurfaceSettingsPayload value){buffer.writeInt(value.window);buffer.writeBlockPos(value.pos);buffer.writeUtf(value.on,128);buffer.writeUtf(value.off,128);buffer.writeByte(value.mode);}
    };
    @Override public Type<NativeSurfaceSettingsPayload> type(){return TYPE;}
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NativeSurfaceMenu menu)menu.configure(player,message.window,message.pos,message.on,message.off,message.mode);}));}
}
