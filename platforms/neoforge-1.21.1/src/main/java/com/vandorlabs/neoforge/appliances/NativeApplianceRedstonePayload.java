package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public record NativeApplianceRedstonePayload(int window,BlockPos pos,int mode,ChannelList channels) implements CustomPacketPayload {
    public static final Type<NativeApplianceRedstonePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","appliance_redstone"));
    private static final StreamCodec<RegistryFriendlyByteBuf,NativeApplianceRedstonePayload> CODEC=new StreamCodec<>() {
        @Override public NativeApplianceRedstonePayload decode(RegistryFriendlyByteBuf in){
            if(in.readableBytes()<14||in.readableBytes()>14+4*ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Appliance settings size");
            int window=in.readInt();
            BlockPos pos=in.readBlockPos();
            int mode=in.readUnsignedByte(),count=in.readUnsignedByte();
            if(count>ChannelList.MAX_CHANNELS||in.readableBytes()!=4*count)throw new IllegalArgumentException("Appliance channels size");
            int[] channels=new int[count];
            for(int i=0;i<count;i++)channels[i]=in.readInt();
            return new NativeApplianceRedstonePayload(window,pos,mode,ChannelList.of(channels));
        }
        @Override public void encode(RegistryFriendlyByteBuf out,NativeApplianceRedstonePayload message){
            out.writeInt(message.window);
            out.writeBlockPos(message.pos);
            out.writeByte(message.mode);
            out.writeByte(message.channels.size());
            for(int i=0;i<message.channels.size();i++)out.writeInt(message.channels.get(i));
        }
    };
    @Override public Type<NativeApplianceRedstonePayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NativeApplianceMenu menu
                    &&menu.containerId==message.window&&menu.tile.getBlockPos().equals(message.pos)&&menu.stillValid(player))
                menu.tile.configureRedstone(message.mode,message.channels);
        }));
    }
}
