package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.redstone.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Immediate main-slot transmission edit, independent of the reader's uncommitted cosmetic draft. */
public record ReaderTransmissionPayload(int window,ChannelList channels,int range,boolean local) implements CustomPacketPayload {
    public ReaderTransmissionPayload{if(!TransmissionSettings.validConfiguration(channels,range))throw new IllegalArgumentException("Invalid reader transmission");}
    public static final Type<ReaderTransmissionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","reader_transmission"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ReaderTransmissionPayload> CODEC=new StreamCodec<>(){
        public ReaderTransmissionPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()<8)throw new IllegalArgumentException("Truncated reader transmission");int window=buffer.readInt(),range=buffer.readUnsignedShort(),local=buffer.readUnsignedByte(),count=buffer.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||local>1||buffer.readableBytes()!=4*count)throw new IllegalArgumentException("Invalid reader transmission length");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=buffer.readInt();return new ReaderTransmissionPayload(window,ChannelList.of(channels),range,local==1);}
        public void encode(RegistryFriendlyByteBuf buffer,ReaderTransmissionPayload edit){buffer.writeInt(edit.window);buffer.writeShort(edit.range);buffer.writeBoolean(edit.local);buffer.writeByte(edit.channels.size());for(int i=0;i<edit.channels.size();i++)buffer.writeInt(edit.channels.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,ReaderTransmissionPayload edit){var menu=NativeCredentialReaderMenu.editing(player,edit.window);return menu!=null&&menu.tile.configureTransmission(player,edit.channels,edit.range,edit.local);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(edit,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,edit);}));}
}
