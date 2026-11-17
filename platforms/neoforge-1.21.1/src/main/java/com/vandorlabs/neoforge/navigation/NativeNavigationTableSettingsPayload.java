package com.vandorlabs.neoforge.navigation;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
public record NativeNavigationTableSettingsPayload(BlockPos pos,int window,int texture,ChannelList channels) implements CustomPacketPayload {
 public static final Type<NativeNavigationTableSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","navigation_table_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeNavigationTableSettingsPayload> CODEC=new StreamCodec<>(){public NativeNavigationTableSettingsPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<17||b.readableBytes()>17+ChannelList.MAX_CHANNELS*4)throw new IllegalArgumentException("Table settings length");var p=b.readBlockPos();int w=b.readInt(),t=b.readInt(),count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Table channel count");int[] values=new int[count];for(int i=0;i<count;i++){values[i]=b.readInt();if(values[i]<0)throw new IllegalArgumentException("Negative channel");}return new NativeNavigationTableSettingsPayload(p,w,t,ChannelList.of(values));}public void encode(RegistryFriendlyByteBuf b,NativeNavigationTableSettingsPayload p){b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeInt(p.texture);b.writeByte(p.channels.size());for(int i=0;i<p.channels.size();i++)b.writeInt(p.channels.get(i));}};
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor&&actor.getServer().isSameThread()&&actor.containerMenu instanceof NativeNavigationTableMenu menu&&menu.containerId==p.window&&menu.tile.getBlockPos().equals(p.pos)&&menu.stillValid(actor)&&(p.texture==-5||p.texture==-1||p.texture==-2||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(p.texture))){menu.tile.setTexture(p.texture);menu.tile.setRxChannels(p.channels);}}));}
}
