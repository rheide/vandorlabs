package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Typed settings/operation only, bounded before allocating channel text. */
public record NativeCopySettingsPayload(int window,BlockPos pos,int operation,CopySettings choices) implements CustomPacketPayload {
    public static final Type<NativeCopySettingsPayload> TYPE=new Type<>(NativeCopyBindings.id("copy_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeCopySettingsPayload> CODEC=new StreamCodec<>(){
        public NativeCopySettingsPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()<21||buffer.readableBytes()>4121)throw new IllegalArgumentException("Copy packet length");int window=buffer.readInt();var pos=buffer.readBlockPos();int operation=buffer.readUnsignedByte(),height=buffer.readUnsignedByte(),increment=buffer.readInt(),mode=buffer.readUnsignedByte(),chip=buffer.readUnsignedByte();var channels=ChannelList.parse(buffer.readUtf(1024));if(buffer.isReadable()||operation>2||chip>1)throw new IllegalArgumentException("Copy packet operation");return new NativeCopySettingsPayload(window,pos,operation,new CopySettings(height,increment,mode,channels,chip==1));}
        public void encode(RegistryFriendlyByteBuf buffer,NativeCopySettingsPayload value){buffer.writeInt(value.window);buffer.writeBlockPos(value.pos);buffer.writeByte(value.operation);buffer.writeByte(value.choices.height);buffer.writeInt(value.choices.increment);buffer.writeByte(value.choices.activation);buffer.writeByte(value.choices.chip?1:0);buffer.writeUtf(value.choices.channels.toString(),1024);}
    };
    @Override public Type<NativeCopySettingsPayload> type(){return TYPE;}
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor&&actor.containerMenu instanceof NativeCopyMenu menu)menu.apply(actor,message.window,message.pos,message.operation,message.choices);}));}
}
