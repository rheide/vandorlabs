package com.vandorlabs.neoforge.plate;
import com.vandorlabs.redstone.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Bounded family choices or inline transmitter update, both tied to the currently installed menu. */
public record NativePlatePayload(int window,int texture,int mode,int sensitivity,ChannelList channels,int range,boolean local,boolean transmission) implements CustomPacketPayload {
    public NativePlatePayload{if(channels==null||mode<0||mode>2||sensitivity<0||sensitivity>2||transmission&&!TransmissionSettings.validConfiguration(channels,range))throw new IllegalArgumentException("Invalid pressure plate edit");}
    public static final Type<NativePlatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","pressure_plate"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NativePlatePayload> CODEC=new StreamCodec<>(){
        public NativePlatePayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<16)throw new IllegalArgumentException("Truncated pressure plate edit");int window=b.readInt(),texture=b.readInt(),mode=b.readUnsignedByte(),sensitivity=b.readUnsignedByte(),range=b.readUnsignedShort(),local=b.readUnsignedByte(),transmission=b.readUnsignedByte(),count=b.readUnsignedShort();if(local>1||transmission>1||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid pressure plate length");int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();return new NativePlatePayload(window,texture,mode,sensitivity,ChannelList.of(values),range,local==1,transmission==1);}
        public void encode(RegistryFriendlyByteBuf b,NativePlatePayload p){b.writeInt(p.window);b.writeInt(p.texture);b.writeByte(p.mode);b.writeByte(p.sensitivity);b.writeShort(p.range);b.writeBoolean(p.local);b.writeBoolean(p.transmission);b.writeShort(p.channels.size());for(int i=0;i<p.channels.size();i++)b.writeInt(p.channels.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,NativePlatePayload p){var menu=NativePlateMenu.editing(player,p.window);return menu!=null&&(p.transmission?menu.tile.configureTransmission(player,p.channels,p.range,p.local):menu.tile.configure(player,p.texture,p.mode,p.sensitivity));}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor)apply(actor,p);}));}
}
