package com.vandorlabs.neoforge.gear.track;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Separate choice packet leaves the released arm settings wire layout unchanged. */
public record NativeTrackStylePayload(int window,BlockPos selected,java.util.UUID identity,int style) implements CustomPacketPayload {
    public static final Type<NativeTrackStylePayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:track_style"));
    public NativeTrackStylePayload{selected=selected.immutable();if(style<0||style>2)throw new IllegalArgumentException("Invalid Track style");java.util.Objects.requireNonNull(identity);}
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeTrackStylePayload> CODEC=new StreamCodec<>(){
        public NativeTrackStylePayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=29)throw new IllegalArgumentException("Track style packet bound");return new NativeTrackStylePayload(buffer.readInt(),buffer.readBlockPos(),buffer.readUUID(),buffer.readUnsignedByte());}
        public void encode(RegistryFriendlyByteBuf buffer,NativeTrackStylePayload value){buffer.writeInt(value.window);buffer.writeBlockPos(value.selected);buffer.writeUUID(value.identity);buffer.writeByte(value.style);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,payload);}));}
    public static boolean apply(ServerPlayer actor,NativeTrackStylePayload payload){if(!actor.getServer().isSameThread()||actor.containerMenu.containerId!=payload.window)return false;if(actor.containerMenu instanceof NativeTrackMenu menu){if(!menu.selected.equals(payload.selected)||!menu.identity.equals(payload.identity)||!menu.stillValid(actor))return false;return NativeTracks.style(actor,menu.tile,payload.style);}if(actor.containerMenu instanceof com.vandorlabs.neoforge.gear.NativeGearMenu menu){if(!menu.selectedPosition().equals(payload.selected)||!menu.selectedIdentity().equals(payload.identity)||!menu.resolve(actor))return false;return NativeTracks.style(actor,menu.tile,payload.style);}return false;}
}
