package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.canopy.*;
import com.vandorlabs.neoforge.credentials.ReaderTransmissionPayload;
import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.neoforge.light.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.redstone.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Actual converted native editor codecs retain the shared full-int ID and 64-entry policy. */
public final class NativeChannelInputRangeChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Native channel input "+text);}
 private static <T> void wire(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{codec.encode(b,value);int size=b.readableBytes();byte[] expected=new byte[size];b.getBytes(b.readerIndex(),expected);var decoded=codec.decode(b);need(!b.isReadable(),"actual dialog codec complete roundtrip "+value.getClass().getSimpleName());codec.encode(b,decoded);byte[] actual=new byte[b.readableBytes()];b.getBytes(b.readerIndex(),actual);need(b.readableBytes()==size&&Arrays.equals(expected,actual),"all encoded settings and int IDs roundtrip unchanged");}finally{b.release();}}
 public static void main(String[] args){int cases=0;for(ChannelList bank:List.of(ChannelList.EMPTY,ChannelList.of(65535),ChannelList.of(65565),ChannelList.of(67002),ChannelList.of(Integer.MAX_VALUE),maximum())){
  need(ChannelList.parse(bank.toString()).equals(bank)&&bank.toString().length()<=ChannelList.MAX_TEXT_LENGTH,"shared parser full-width bank");wire(BinaryTransmissionPayload.CODEC,new BinaryTransmissionPayload(BlockPos.ZERO,37,bank,256,true));wire(WirelessTransmissionPayload.CODEC,new WirelessTransmissionPayload(BlockPos.ZERO,37,bank,256,true));wire(ReaderTransmissionPayload.CODEC,new ReaderTransmissionPayload(37,bank,256,true));wire(TransmitterPayload.CODEC,new TransmitterPayload(37,bank.toString()));wire(DoorSettingsPayload.CODEC,new DoorSettingsPayload(BlockPos.ZERO,37,new NativeDoorEdit(0,0,false,0,0,true,true,0,false,0,0,127,-1,0),bank));
  var canopy=new CompoundTag();NativeCanopySettings.fresh().write(canopy);canopy.putIntArray("CanopyChannels",bank.toArray());wire(CanopySettingsPayload.CODEC,new CanopySettingsPayload(new NativeCanopyEdit(BlockPos.ZERO,37,1,NativeCanopySettings.read(canopy))));wire(LightSettingsPayload.CODEC,new LightSettingsPayload(new NativeLightSettingsEdit(BlockPos.ZERO,37,new NativeLightEdit(0,-1,15,false,0,0,false,0,false,0),bank)));wire(ScreenSettingsPayload.CODEC,new ScreenSettingsPayload(BlockPos.ZERO,37,ScreenSettingsPayload.TRANSMISSION,0,new NativeScreenTransmissionEdit(bank,256,true)));wire(ScreenSettingsPayload.CODEC,new ScreenSettingsPayload(BlockPos.ZERO,37,ScreenSettingsPayload.ROWS,0,new NativeScreenRowEdit("High IDs",List.of(new NativeScreenRowEdit.Row("Toggle",bank,false,false,5,15)))));
  var b=Unpooled.buffer();try{var edit=new NativeSegmentedEdit(37,0,10,true,true,bank,-1,null);edit.encode(b);need(NativeSegmentedEdit.decode(b,id->true).equals(edit)&&!b.isReadable(),"actual Segmented Door codec full-width bank");}finally{b.release();}cases++;
 }
 need(ChannelRange.shift(67002,1)==67003&&ChannelRange.shift(1,ChannelRange.MAX_INCREMENT)==Integer.MAX_VALUE&&ChannelRange.shift(Integer.MAX_VALUE,-ChannelRange.MAX_INCREMENT)==1&&ChannelRange.shift(0,ChannelRange.MAX_INCREMENT)==0,"same Java21 checked shift policy");for(String invalid:List.of("2147483648","-1","31,","1,,2"))need(ChannelList.parse(invalid)==null,"same malformed input refusal");try{ChannelList.of(new int[65]);throw new AssertionError("65-entry bank admitted");}catch(IllegalArgumentException expected){checks++;}
 System.out.println("Native channel input range PASS bankProfiles="+cases+" actualCodecFamilies=10 checks="+checks+" IDs1..2147483647/zeroDisconnected/64bank noWorld/noRuntimeMessageClaim");
 }
 private static ChannelList maximum(){int[] ids=new int[64];for(int i=0;i<64;i++)ids[i]=Integer.MAX_VALUE-63+i;return ChannelList.of(ids);}
}
