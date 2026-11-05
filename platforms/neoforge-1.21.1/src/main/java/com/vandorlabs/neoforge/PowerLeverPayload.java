package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.persistence.PowerLeverSize;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Atomic General-page size and mount draft; binary controls retain their original 15-byte payload. */
public record PowerLeverPayload(BlockPos pos,int windowId,ControlMountSettings mount,int size) implements CustomPacketPayload {
    public static final Type<PowerLeverPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","power_lever"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PowerLeverPayload> CODEC=new StreamCodec<>(){
        public PowerLeverPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=16)throw new IllegalArgumentException("Invalid power lever payload length");var pos=b.readBlockPos();int window=b.readInt(),height=b.readUnsignedByte(),tilt=b.readUnsignedByte(),direction=b.readUnsignedByte(),size=b.readUnsignedByte();if(!PowerLeverSize.valid(size))throw new IllegalArgumentException("Invalid power lever size");return new PowerLeverPayload(pos,window,ControlMountSettings.of(height,tilt,direction),size);}
        public void encode(RegistryFriendlyByteBuf b,PowerLeverPayload p){b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeByte(p.mount.height);b.writeByte(p.mount.tilt);b.writeByte(p.mount.direction);b.writeByte(p.size);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,PowerLeverPayload p){if(p.pos==null||p.mount==null||!ControlMountSettings.valid(p.mount.height,p.mount.tilt,p.mount.direction)||!PowerLeverSize.valid(p.size))return false;var tile=BinaryControlMenu.editing(player,p.pos,p.windowId);return tile!=null&&tile.configurePowerLever(p.mount,p.size);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
}
