package com.vandorlabs.neoforge.rack;

import com.vandorlabs.rack.AutopilotModuleSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Two bounded typed choices; no NBT, inventory, capability or recovery payload is accepted. */
public record NativeRackSettingsPayload(int window,BlockPos pos,int operation,int housing,boolean emit,
        NativeRackData.Transmission transmission,int slot,long sequence,AutopilotModuleSettings module) implements CustomPacketPayload {
    public static final Type<NativeRackSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","module_rack_settings"));
    public static NativeRackSettingsPayload housing(int window,BlockPos pos,int housing,boolean emit,NativeRackData.Transmission transmission){return new NativeRackSettingsPayload(window,pos,0,housing,emit,transmission,0,0,null);}
    public static NativeRackSettingsPayload module(int window,BlockPos pos,int slot,long sequence,AutopilotModuleSettings settings){return new NativeRackSettingsPayload(window,pos,1,0,false,null,slot,sequence,settings);}
    public static NativeRackSettingsPayload power(int window,BlockPos pos,boolean out){return new NativeRackSettingsPayload(window,pos,2,0,out,null,0,0,null);}
    private static ChannelList channels(RegistryFriendlyByteBuf in){int count=in.readUnsignedByte();if(count>64||in.readableBytes()<count*4)throw new IllegalArgumentException("Rack channel bound");int[] values=new int[count];for(int index=0;index<count;index++)values[index]=in.readInt();return ChannelList.of(values);}
    private static void channels(RegistryFriendlyByteBuf out,ChannelList channels){out.writeByte(channels.size());for(int index=0;index<channels.size();index++)out.writeInt(channels.get(index));}
    private static boolean flag(RegistryFriendlyByteBuf in){int value=in.readUnsignedByte();if(value>1)throw new IllegalArgumentException("Rack boolean");return value==1;}
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeRackSettingsPayload> CODEC=new StreamCodec<>() {
        @Override public NativeRackSettingsPayload decode(RegistryFriendlyByteBuf in){
            if(in.readableBytes()<14||in.readableBytes()>800)throw new IllegalArgumentException("Rack packet bound");int operation=in.readUnsignedByte(),window=in.readInt();BlockPos pos=in.readBlockPos();NativeRackSettingsPayload result;
            if(operation==0){int housing=in.readInt();boolean emit=flag(in);var channels=channels(in);int range=in.readInt();boolean local=flag(in),present=flag(in);result=housing(window,pos,housing,emit,new NativeRackData.Transmission(channels,range,local,present));}
            else if(operation==1){int slot=in.readUnsignedByte();long sequence=in.readLong();if(slot>=4||sequence<0)throw new IllegalArgumentException("Rack module slot");var channels=channels(in);int activation=in.readUnsignedByte();String marker=in.readUtf(128);int metadata=in.readUnsignedByte();result=module(window,pos,slot,sequence,new AutopilotModuleSettings(channels,activation,marker,metadata));}
            else if(operation==2)result=power(window,pos,flag(in));
            else throw new IllegalArgumentException("Rack operation");if(in.isReadable())throw new IllegalArgumentException("Rack trailing bytes");return result;
        }
        @Override public void encode(RegistryFriendlyByteBuf out,NativeRackSettingsPayload value){
            out.writeByte(value.operation);out.writeInt(value.window);out.writeBlockPos(value.pos);
            if(value.operation==0){out.writeInt(value.housing);out.writeBoolean(value.emit);channels(out,value.transmission.channels());out.writeInt(value.transmission.range());out.writeBoolean(value.transmission.local());out.writeBoolean(value.transmission.present());}
            else if(value.operation==2)out.writeBoolean(value.emit);
            else{out.writeByte(value.slot);out.writeLong(value.sequence);channels(out,value.module.channels);out.writeByte(value.module.activation);out.writeUtf(value.module.marker,128);out.writeByte(value.module.markerMetadata);}
        }
    };
    @Override public Type<NativeRackSettingsPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{
        if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NativeRackMenu menu){
            if(message.operation==0)menu.configure(player,message.window,message.pos,message.housing,message.emit,message.transmission);
            else if(message.operation==2)menu.configurePower(player,message.window,message.pos,message.emit);
            else menu.configureModule(player,message.window,message.pos,message.slot,message.sequence,message.module);
        }
    }));}
}
