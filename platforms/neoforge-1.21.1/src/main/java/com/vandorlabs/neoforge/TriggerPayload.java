package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded atomic Trigger edit. Dormant playback arrays remain data, never animation controls. */
public record TriggerPayload(BlockPos pos,int windowId,int off,TriggerState state,ChannelList channels) implements CustomPacketPayload {
    public static final Type<TriggerPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TriggerPayload> CODEC=new StreamCodec<>(){
        @Override public TriggerPayload decode(RegistryFriendlyByteBuf b){
            // Fixed header and playback fields followed by at most 64 channel integers.
            if(b.readableBytes()<71)throw new IllegalArgumentException("Truncated Trigger settings");
            BlockPos pos=b.readBlockPos();int window=b.readInt(),off=b.readInt(),on=b.readInt(),exact=b.readInt(),low=b.readInt(),medium=b.readInt(),sides=b.readInt();
            int bands=b.readUnsignedByte(),front=b.readUnsignedByte();
            if(bands>1||front>1||exact< -1||exact>15)throw new IllegalArgumentException("Invalid Trigger settings");
            int[] modes=new int[4],speeds=new int[4];for(int i=0;i<4;i++)modes[i]=b.readInt();for(int i=0;i<4;i++)speeds[i]=b.readInt();
            int count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid Trigger channels length");
            int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();
            var result=new TriggerPayload(pos,window,off,new TriggerState(on,bands==1,exact,low,medium,front==1,sides,modes,speeds),ChannelList.of(values));
            if(!result.valid())throw new IllegalArgumentException("Invalid Trigger choices");return result;
        }
        @Override public void encode(RegistryFriendlyByteBuf b,TriggerPayload p){
            b.writeBlockPos(p.pos);b.writeInt(p.windowId);b.writeInt(p.off);b.writeInt(p.state.on);b.writeInt(p.state.exact);b.writeInt(p.state.low);b.writeInt(p.state.medium);b.writeInt(p.state.sides);
            b.writeBoolean(p.state.bands);b.writeBoolean(p.state.frontOnly);
            for(int v:p.state.artworkModes())b.writeInt(v);for(int v:p.state.artworkSpeeds())b.writeInt(v);
            b.writeByte(p.channels.size());for(int v:p.channels.toArray())b.writeInt(v);
        }
    };
    public boolean valid(){
        if(state==null||channels==null||channels.size()>ChannelList.MAX_CHANNELS||state.exact< -1||state.exact>15||!TriggerState.validArtwork(state.artworkModes(),state.artworkSpeeds()))return false;
        for(int choice:new int[]{off,state.on,state.low,state.medium,state.sides})if(!ModernFinishCatalog.validChoice(choice))return false;
        return true;
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static boolean apply(ServerPlayer player,TriggerPayload packet){
        if(!player.getServer().isSameThread()||!packet.valid()||!(player.containerMenu instanceof TriggerMenu menu)||menu.containerId!=packet.windowId||!menu.stillValid(player)||!menu.tile.getBlockPos().equals(packet.pos))return false;
        return menu.tile.configure(packet.off,packet.state,packet.channels);
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
