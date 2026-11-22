package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.redstone.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Immediate main-slot transmission edit, independent of the storage housing draft. */
public record BufferTransmissionPayload(int window,ChannelList channels,int range,boolean local) implements CustomPacketPayload {
    public BufferTransmissionPayload{if(!TransmissionSettings.validConfiguration(channels,range))throw new IllegalArgumentException("Invalid storage transmission");}
    public static final Type<BufferTransmissionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","buffer_transmission"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BufferTransmissionPayload> CODEC=new StreamCodec<>(){
        public BufferTransmissionPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()<8)throw new IllegalArgumentException("Truncated storage transmission");int window=buffer.readInt(),range=buffer.readUnsignedShort(),local=buffer.readUnsignedByte(),count=buffer.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||local>1||buffer.readableBytes()!=4*count)throw new IllegalArgumentException("Invalid storage transmission length");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=buffer.readInt();return new BufferTransmissionPayload(window,ChannelList.of(channels),range,local==1);}
        public void encode(RegistryFriendlyByteBuf buffer,BufferTransmissionPayload edit){buffer.writeInt(edit.window);buffer.writeShort(edit.range);buffer.writeBoolean(edit.local);buffer.writeByte(edit.channels.size());for(int i=0;i<edit.channels.size();i++)buffer.writeInt(edit.channels.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,BufferTransmissionPayload edit){var menu=player.containerMenu;
        if(menu.containerId!=edit.window||!menu.stillValid(player))return false;
        NativeBufferEntity owner=menu instanceof NativeBufferMenu storage?storage.tile:
            menu instanceof com.vandorlabs.neoforge.ports.NativePortMenu port && port.tile instanceof NativeBatteryEntity battery?battery:null;
        return owner!=null && owner.configureOutput(player,edit.channels,edit.range,edit.local);
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(edit,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,edit);}));}
}
