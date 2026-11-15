package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
/** Bounded32B header plus4B per channel; exact clicked physical identity and menu are rechecked. */
public record NativeLegacyLandingPayload(BlockPos selected,UUID identity,int window,NativeLegacyLandingData.Configuration settings) implements CustomPacketPayload {
 public NativeLegacyLandingPayload{java.util.Objects.requireNonNull(selected);java.util.Objects.requireNonNull(identity);java.util.Objects.requireNonNull(settings);}
 public static final Type<NativeLegacyLandingPayload> TYPE=new Type<>(NativeLegacyLandingBindings.id("legacy_landing_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeLegacyLandingPayload> CODEC=new StreamCodec<>(){
  public NativeLegacyLandingPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<32)throw new IllegalArgumentException("Short legacy settings");var pos=b.readBlockPos();var identity=b.readUUID();int window=b.readInt(),mode=b.readUnsignedByte(),pixels=b.readUnsignedByte(),size=b.readUnsignedByte(),count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Legacy settings channel limit");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.readInt();return new NativeLegacyLandingPayload(pos,identity,window,new NativeLegacyLandingData.Configuration(mode,ChannelList.of(channels),pixels,size));}
  public void encode(RegistryFriendlyByteBuf b,NativeLegacyLandingPayload p){b.writeBlockPos(p.selected);b.writeUUID(p.identity);b.writeInt(p.window);b.writeByte(p.settings.mode());b.writeByte(p.settings.pixels());b.writeByte(p.settings.size());b.writeByte(p.settings.channels().size());for(int i=0;i<p.settings.channels().size();i++)b.writeInt(p.settings.channels().get(i));}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor){var menu=NativeLegacyLandingMenu.editing(actor,p.selected,p.identity,p.window);if(menu!=null){var root=menu.root();if(root!=null)root.configure(actor,p.settings);}}}));}
}
