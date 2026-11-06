package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import java.util.function.IntPredicate;

/** Released operation indices and bounded wire fields, separate from installed-menu authorization. */
public record NativeSegmentedEdit(int window, int operation, int ticks, boolean local, boolean opens,
                                  ChannelList channels, int texture, NativeSegmentedDoorSettings settings) {
    public static final int MAX_BYTES=27+4*ChannelList.MAX_CHANNELS;
    public NativeSegmentedEdit {
        if(window<0 || operation<0 || operation>5 || ticks<5 || ticks>30 || channels==null
                || operation>=4 && settings==null)
            throw new IllegalArgumentException("Invalid Segmented Door edit");
    }
    public static NativeSegmentedEdit decode(ByteBuf buffer,IntPredicate validTexture) {
        if(buffer.readableBytes()<8 || buffer.readableBytes()>MAX_BYTES)
            throw new IllegalArgumentException("Invalid bounded Segmented Door edit length");
        int window=buffer.readInt(),operation=buffer.readUnsignedByte(),ticks=buffer.readUnsignedByte();
        int flags=buffer.readUnsignedByte(),count=buffer.readUnsignedByte();
        if(window<0 || operation>5 || ticks<5 || ticks>30 || flags>3 || count>ChannelList.MAX_CHANNELS
                || buffer.readableBytes()<count*4)
            throw new IllegalArgumentException("Invalid Segmented Door header");
        int remaining=buffer.readableBytes()-count*4;
        if(operation<3 ? remaining!=0 : operation==3 ? remaining!=4
                : operation==5 ? remaining!=19 : remaining!=18 && remaining!=19)
            throw new IllegalArgumentException("Invalid Segmented Door extension length");
        // Check the entire list before allocating it. No world access occurs during decode.
        for(int i=0;i<count;i++)if(buffer.getInt(buffer.readerIndex()+i*4)<0)
            throw new IllegalArgumentException("Negative Segmented Door channel");
        int[] ids=new int[count];for(int i=0;i<count;i++)ids[i]=buffer.readInt();
        int texture=operation>=3?buffer.readInt():-1;
        if(operation>=3 && !validTexture.test(texture))throw new IllegalArgumentException("Invalid Segmented Door texture");
        var settings=operation>=4?NativeSegmentedDoorSettings.decode(buffer):null;
        return new NativeSegmentedEdit(window,operation,ticks,(flags&1)!=0,(flags&2)!=0,ChannelList.of(ids),texture,settings);
    }
    public void encode(ByteBuf buffer) {
        buffer.writeInt(window);buffer.writeByte(operation);buffer.writeByte(ticks);
        buffer.writeByte((local?1:0)|(opens?2:0));buffer.writeByte(channels.size());
        for(int i=0;i<channels.size();i++)buffer.writeInt(channels.get(i));
        if(operation>=3)buffer.writeInt(texture);if(operation>=4)settings.encode(buffer);
    }
}
