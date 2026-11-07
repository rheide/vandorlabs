package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bounded standalone editor choices. No platform snapshot, actor receipt or journal data. */
public record NativeRampEdit(BlockPos pos,int window,int request,NativeRampSettings settings) {
 public static final int BASE_BYTES=50,MAX_BYTES=BASE_BYTES+4*ChannelList.MAX_CHANNELS;
 public NativeRampEdit {java.util.Objects.requireNonNull(pos);java.util.Objects.requireNonNull(settings);pos=pos.immutable();if(window<0||request<0||!settings.hasDirection()||!ControllerPlatform.validTreadPixels(settings.treadPixels())||settings.channels().size()>ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Invalid Ramp edit");}
 public void encode(RegistryFriendlyByteBuf b){var s=settings;b.writeBlockPos(pos);b.writeInt(window);b.writeInt(request);b.writeInt(s.startHalf());b.writeInt(s.endHalf());b.writeInt(s.treadPixels());b.writeInt(s.speed());b.writeInt(s.interpolation());b.writeInt(s.escalator());b.writeInt(s.travelAxis());b.writeInt(s.direction());b.writeByte((s.powerOn()?1:0)|(s.elevator()?2:0)|(s.extend()?4:0)|(s.matchTextures()?8:0)|(s.moat()?16:0)|(s.verticalLayout()?32:0)|(s.analogDeployment()?64:0));b.writeByte(s.channels().size());for(int i=0;i<s.channels().size();i++)b.writeInt(s.channels().get(i));}
 public static NativeRampEdit decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<BASE_BYTES||b.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Ramp edit length");var pos=b.readBlockPos();int window=b.readInt(),request=b.readInt(),start=b.readInt(),end=b.readInt(),tread=b.readInt(),speed=b.readInt(),interpolation=b.readInt(),escalator=b.readInt(),travel=b.readInt(),direction=b.readInt(),flags=b.readUnsignedByte(),count=b.readUnsignedByte();if(flags>127||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=4*count)throw new IllegalArgumentException("Ramp edit header");int previous=0;for(int i=0;i<count;i++){int value=b.getInt(b.readerIndex()+4*i);if(value<=previous)throw new IllegalArgumentException("Ramp edit channel bank");previous=value;}int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.readInt();return new NativeRampEdit(pos,window,request,new NativeRampSettings(start,end,tread,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,speed,(flags&8)!=0,interpolation,escalator,(flags&16)!=0,(flags&32)!=0,travel,true,direction,(flags&64)!=0,ChannelList.of(channels)));}
}
