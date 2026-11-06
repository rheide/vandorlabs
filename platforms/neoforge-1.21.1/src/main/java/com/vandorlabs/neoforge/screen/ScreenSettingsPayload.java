package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.NativeFrameWire;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Inactive bounded editor transport. No runtime row state or ownership envelopes are accepted. */
public record ScreenSettingsPayload(BlockPos pos,int window,int operation,int slot,Object edit) implements CustomPacketPayload {
 public static final int DISPLAY=0,SURFACE=1,ROWS=2,ACTIVATION=3,FRAME=4,LAYOUT=5,TRANSMISSION=6,MAX_BYTES=8192;
 public static final Type<ScreenSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_settings"));
 public ScreenSettingsPayload{if(pos==null||window<0||slot<0||slot>1||!switch(operation){case DISPLAY->edit instanceof NativeScreenDisplayEdit;case SURFACE->edit instanceof NativeScreenSurfaceEdit value&&value.slot()==slot;case ROWS->edit instanceof NativeScreenRowEdit;case ACTIVATION->edit instanceof NativeScreenActivationEdit;case FRAME->edit instanceof NativeScreenFrameEdit;case LAYOUT->edit instanceof NativeScreenLayoutEdit;case TRANSMISSION->edit instanceof NativeScreenTransmissionEdit;default->false;})throw new IllegalArgumentException("Screen command");pos=pos.immutable();}
 public static final StreamCodec<RegistryFriendlyByteBuf,ScreenSettingsPayload> CODEC=new StreamCodec<>(){
  public ScreenSettingsPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<14||b.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Screen command length");var pos=b.readBlockPos();int window=b.readInt(),op=b.readUnsignedByte(),slot=b.readUnsignedByte();Object edit=switch(op){
   case DISPLAY->new NativeScreenDisplayEdit(b.readUtf(256),flag(b),flag(b),b.readUnsignedByte(),flag(b),b.readUnsignedByte(),b.readUtf(256),b.readUtf(256),flag(b),b.readInt(),channels(b));
   case SURFACE->new NativeScreenSurfaceEdit(slot,b.readInt());
   case ROWS->{String title=b.readUtf(NativeScreenControls.MAX_TITLE);int count=b.readUnsignedByte();if(count>NativeScreenControls.STANDALONE_ROWS)throw new IllegalArgumentException("Screen rows");var rows=new java.util.ArrayList<NativeScreenRowEdit.Row>(count);for(int i=0;i<count;i++)rows.add(new NativeScreenRowEdit.Row(b.readUtf(NativeScreenControls.MAX_LABEL),channels(b),flag(b),flag(b),b.readUnsignedByte(),b.readUnsignedByte()));yield new NativeScreenRowEdit(title,rows);}
   case ACTIVATION->new NativeScreenActivationEdit(b.readUnsignedByte(),b.readUnsignedByte(),b.readInt(),b.readByte(),b.readByte(),flag(b));
   case FRAME->new NativeScreenFrameEdit(NativeFrameWire.read(b));case LAYOUT->new NativeScreenLayoutEdit(b.readUnsignedByte());
   case TRANSMISSION->new NativeScreenTransmissionEdit(channels(b),b.readUnsignedShort(),flag(b));default->throw new IllegalArgumentException("Screen operation");};if(b.isReadable())throw new IllegalArgumentException("Trailing Screen command");return new ScreenSettingsPayload(pos,window,op,slot,edit);}
  public void encode(RegistryFriendlyByteBuf b,ScreenSettingsPayload p){b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeByte(p.operation);b.writeByte(p.slot);switch(p.operation){
   case DISPLAY->{var e=(NativeScreenDisplayEdit)p.edit;b.writeUtf(e.screen(),256);b.writeBoolean(e.enabled());b.writeBoolean(e.inverted());b.writeByte(e.playback());b.writeBoolean(e.framed());b.writeByte(e.speed());b.writeUtf(e.input(),256);b.writeUtf(e.secondary(),256);b.writeBoolean(e.small());b.writeInt(e.housing());channels(b,e.channels());}
   case SURFACE->b.writeInt(((NativeScreenSurfaceEdit)p.edit).choice());
   case ROWS->{var e=(NativeScreenRowEdit)p.edit;b.writeUtf(e.title(),256);b.writeByte(e.rows().size());for(var row:e.rows()){b.writeUtf(row.label(),256);channels(b,row.channels());b.writeBoolean(row.slider());b.writeBoolean(row.pulse());b.writeByte(row.min());b.writeByte(row.max());}}
   case ACTIVATION->{var e=(NativeScreenActivationEdit)p.edit;b.writeByte(e.mode());b.writeByte(e.state());b.writeInt(e.texture());b.writeByte(e.playback());b.writeByte(e.speed());b.writeBoolean(e.inverted());}
   case FRAME->NativeFrameWire.write(b,((NativeScreenFrameEdit)p.edit).frames());case LAYOUT->b.writeByte(((NativeScreenLayoutEdit)p.edit).layout());
   case TRANSMISSION->{var e=(NativeScreenTransmissionEdit)p.edit;channels(b,e.channels());b.writeShort(e.range());b.writeBoolean(e.local());}
  }}
 };
 private static boolean flag(RegistryFriendlyByteBuf b){int value=b.readUnsignedByte();if(value>1)throw new IllegalArgumentException("Screen flag");return value!=0;}
 private static ChannelList channels(RegistryFriendlyByteBuf b){int count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()<count*4)throw new IllegalArgumentException("Screen channels");for(int i=0;i<count;i++)if(b.getInt(b.readerIndex()+i*4)<0)throw new IllegalArgumentException("Negative Screen channel");int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();return ChannelList.of(values);}
 private static void channels(RegistryFriendlyByteBuf b,ChannelList channels){b.writeByte(channels.size());for(int i=0;i<channels.size();i++)b.writeInt(channels.get(i));}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public boolean apply(ServerPlayer player){return switch(operation){case DISPLAY->NativeScreenMenu.applyDisplay(player,pos,window,(NativeScreenDisplayEdit)edit);case SURFACE->NativeScreenMenu.applySurface(player,pos,window,(NativeScreenSurfaceEdit)edit);case ROWS->NativeScreenMenu.applyRows(player,pos,window,slot,(NativeScreenRowEdit)edit);case ACTIVATION->NativeScreenMenu.applyActivation(player,pos,window,(NativeScreenActivationEdit)edit);case FRAME->NativeScreenMenu.applyFrames(player,pos,window,(NativeScreenFrameEdit)edit);case LAYOUT->NativeScreenMenu.applyLayout(player,pos,window,(NativeScreenLayoutEdit)edit);case TRANSMISSION->NativeScreenMenu.applyTransmission(player,pos,window,(NativeScreenTransmissionEdit)edit);default->false;};}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)payload.apply(player);}));}
}
