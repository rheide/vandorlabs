package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** General mount draft, committed only by the dialog's Done action. */
public record BinaryControlPayload(BlockPos pos,int windowId,ControlMountSettings mount) implements CustomPacketPayload {
    public static final Type<BinaryControlPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","binary_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BinaryControlPayload> CODEC=new StreamCodec<>(){
        public BinaryControlPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=15)throw new IllegalArgumentException("Invalid binary control payload length");var pos=b.readBlockPos();int window=b.readInt(),height=b.readUnsignedByte(),tilt=b.readUnsignedByte(),direction=b.readUnsignedByte();return new BinaryControlPayload(pos,window,ControlMountSettings.of(height,tilt,direction));}
        public void encode(RegistryFriendlyByteBuf b,BinaryControlPayload p){b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeByte(p.mount.height);b.writeByte(p.mount.tilt);b.writeByte(p.mount.direction);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,BinaryControlPayload p){if(!player.getServer().isSameThread()||p.pos==null||p.mount==null||!ControlMountSettings.valid(p.mount.height,p.mount.tilt,p.mount.direction)||!(player.containerMenu instanceof BinaryControlMenu menu)||menu.containerId!=p.windowId||!menu.tile.getBlockPos().equals(p.pos)||!menu.stillValid(player))return false;return menu.tile.configureMount(p.mount);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
