package com.vandorlabs.neoforge.builders;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public record NativeBuilderPayload(NativeBuilderEdit edit) implements CustomPacketPayload{
 public static final Type<NativeBuilderPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","builder_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeBuilderPayload> CODEC=new StreamCodec<>(){
  public NativeBuilderPayload decode(RegistryFriendlyByteBuf in){if(in.readableBytes()<15||in.readableBytes()>15+64*4)throw new IllegalArgumentException("Builder payload size");int window=in.readInt();var pos=in.readBlockPos();int operation=in.readUnsignedByte(),value=in.readUnsignedByte(),count=in.readUnsignedByte();if(count>64||in.readableBytes()!=count*4)throw new IllegalArgumentException("Builder channel bound");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=in.readInt();return new NativeBuilderPayload(new NativeBuilderEdit(window,pos,operation,value,ChannelList.of(channels)));}
  public void encode(RegistryFriendlyByteBuf out,NativeBuilderPayload message){var e=message.edit;out.writeInt(e.window());out.writeBlockPos(e.pos());out.writeByte(e.operation());out.writeByte(e.value());out.writeByte(e.channels().size());for(int i=0;i<e.channels().size();i++)out.writeInt(e.channels().get(i));}
 };
 @Override public Type<NativeBuilderPayload> type(){return TYPE;}
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){if(NativeBuilderBindings.enabled())e.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)NativeBuilderMenu.apply(player,message.edit);}));}
}
