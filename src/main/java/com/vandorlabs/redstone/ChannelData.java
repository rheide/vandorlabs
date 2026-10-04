package com.vandorlabs.redstone;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/** List storage retains the old scalar tag through each tile's existing codec. */
public final class ChannelData {
    public static final String CHANNELS="RedstoneChannels";
    private ChannelData(){}
    public static ChannelList read(NBTTagCompound tag,int legacy) {
        return read(tag,CHANNELS,ChannelList.of(Math.max(0,legacy)));
    }
    public static ChannelList read(NBTTagCompound tag,String key,ChannelList fallback) {
        if(!tag.hasKey(key,11))return fallback;
        try{return ChannelList.of(tag.getIntArray(key));}
        catch(IllegalArgumentException malformed){return fallback;}
    }
    public static void write(NBTTagCompound tag,ChannelList channels){tag.setIntArray(CHANNELS,channels.toArray());}
    public static void write(ByteBuf buffer,ChannelList channels) {
        buffer.writeByte(channels.size());
        for(int i=0;i<channels.size();i++)buffer.writeInt(channels.get(i));
    }
    /** Optional packet extension; null rejects malformed lists without partial application. */
    public static ChannelList read(ByteBuf buffer,int legacy) {
        if(!buffer.isReadable())return legacy<0?null:ChannelList.of(legacy);
        int count=buffer.readUnsignedByte();
        if(count>ChannelList.MAX_CHANNELS || buffer.readableBytes()!=count*4)return null;
        int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=buffer.readInt();
        try{return ChannelList.of(channels);}catch(IllegalArgumentException malformed){return null;}
    }
}
