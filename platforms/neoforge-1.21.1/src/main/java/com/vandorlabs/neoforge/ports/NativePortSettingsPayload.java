package com.vandorlabs.neoforge.ports;

import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A bounded configuration request, checked against the exact open tile session. */
public record NativePortSettingsPayload(int window,BlockPos pos,NativePortData.Configuration configuration) implements CustomPacketPayload {
    public static final Type<NativePortSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:resource_port_settings"));
    public NativePortSettingsPayload {pos=pos.immutable();java.util.Objects.requireNonNull(configuration);}
    public static final StreamCodec<RegistryFriendlyByteBuf,NativePortSettingsPayload> CODEC=new StreamCodec<>(){
        public NativePortSettingsPayload decode(RegistryFriendlyByteBuf buffer){int length=buffer.readableBytes();if(length<28||length>32+4*ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Invalid port settings length");int window=buffer.readInt();var pos=buffer.readBlockPos();long throughput=buffer.readLong();var faces=new ResourcePortSettings.Face[6];for(int i=0;i<6;i++){int face=buffer.readUnsignedByte();if(face>2)throw new IllegalArgumentException("Invalid port face");faces[i]=ResourcePortSettings.Face.values()[face];}int flags=buffer.readUnsignedByte(),count=buffer.readUnsignedByte();if((flags&~31)!=0||(flags&3)>2||((flags>>3)&3)>2||count>ChannelList.MAX_CHANNELS||(buffer.readableBytes()!=4*count&&buffer.readableBytes()!=4*count+4))throw new IllegalArgumentException("Invalid port choices");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=buffer.readInt();int housing=buffer.isReadable()?buffer.readInt():-1;return new NativePortSettingsPayload(window,pos,new NativePortData.Configuration(new ResourcePortSettings(faces,throughput,(flags&4)!=0,ResourcePortSettings.OnTexture.values()[flags&3],housing),(flags>>3)&3,ChannelList.of(channels)));}
        public void encode(RegistryFriendlyByteBuf buffer,NativePortSettingsPayload packet){var config=packet.configuration;var settings=config.settings();buffer.writeInt(packet.window);buffer.writeBlockPos(packet.pos);buffer.writeLong(settings.throughput);for(int i=0;i<6;i++)buffer.writeByte(settings.face(i).ordinal());buffer.writeByte(settings.onTexture.ordinal()|(settings.pullInputs?4:0)|(config.redstoneMode()<<3));buffer.writeByte(config.channels().size());for(int channel:config.channels().toArray())buffer.writeInt(channel);buffer.writeInt(settings.housingChoice);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer actor,NativePortSettingsPayload packet){return actor.getServer().isSameThread()&&actor.containerMenu instanceof NativePortMenu menu&&menu.apply(actor,packet.window,packet.pos,packet.configuration);}
    public static void register(RegisterPayloadHandlersEvent event){if(NativePortBindings.enabled())event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,packet);}));}
}
