package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;

/** Bounded editor packet value; no runtime signal levels or private tile data. */
public record NativePropulsionSettingsEdit(BlockPos pos,int window,NativePropulsionConfiguration.Draft choices) {
 public static final int HEADER_BYTES=21,MAX_BYTES=HEADER_BYTES+4*ChannelList.MAX_CHANNELS;
 public NativePropulsionSettingsEdit{if(pos==null||window<0||choices==null)throw new IllegalArgumentException("Invalid engine edit target");pos=pos.immutable();}
 public void encode(ByteBuf b){var d=choices;b.writeLong(pos.asLong());b.writeInt(window);b.writeByte((d.updateParticles()?1:0)|(d.updateJoin()?2:0)|(d.join()?4:0)|(d.updateSide()?8:0)|(d.updateShape()?16:0)|(d.inverted()?32:0)|(d.analog()?64:0));b.writeByte(d.particleLevel());b.writeInt(d.sideTexture());b.writeByte(d.shape());b.writeByte(d.threshold());b.writeByte(d.channels().size());for(int i=0;i<d.channels().size();i++)b.writeInt(d.channels().get(i));}
 public static NativePropulsionSettingsEdit decode(ByteBuf b){int length=b.readableBytes();if(length<HEADER_BYTES||length>MAX_BYTES)throw new IllegalArgumentException("Invalid engine edit length");var pos=BlockPos.of(b.readLong());int window=b.readInt(),flags=b.readUnsignedByte(),particles=b.readUnsignedByte(),side=b.readInt(),shape=b.readUnsignedByte(),threshold=b.readUnsignedByte(),count=b.readUnsignedByte();if(window<0||(flags&128)!=0||particles>3||side<0||shape>2||threshold>15||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=4*count)throw new IllegalArgumentException("Invalid engine edit fields");for(int i=0;i<count;i++)if(b.getInt(b.readerIndex()+4*i)<0)throw new IllegalArgumentException("Negative engine channel");int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.readInt();return new NativePropulsionSettingsEdit(pos,window,new NativePropulsionConfiguration.Draft(ChannelList.of(channels),(flags&1)!=0,particles,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,side,(flags&16)!=0,shape,(flags&32)!=0,(flags&64)!=0,threshold));}
}
