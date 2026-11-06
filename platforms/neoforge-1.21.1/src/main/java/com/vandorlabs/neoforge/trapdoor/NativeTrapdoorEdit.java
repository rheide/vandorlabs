package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import java.util.function.IntPredicate;

/** Bounded immediate dialog transaction; installed-menu and group authorization belong to the host adapter. */
public record NativeTrapdoorEdit(int window,NativeTrapdoorSettings settings,boolean inverted) {
    public static final int HEADER_BYTES=15,MAX_BYTES=HEADER_BYTES+4*ChannelList.MAX_CHANNELS;
    public NativeTrapdoorEdit {
        if(window<0 || settings==null || settings.choices().texture<0)
            throw new IllegalArgumentException("Invalid trapdoor edit");
    }
    public static NativeTrapdoorEdit decode(ByteBuf buffer,IntPredicate textureAllowed) {
        int length=buffer.readableBytes();
        if(length<HEADER_BYTES || length>MAX_BYTES)throw new IllegalArgumentException("Invalid trapdoor edit length");
        int window=buffer.readInt(),texture=buffer.readInt(),position=buffer.readUnsignedByte(),trigger=buffer.readUnsignedByte();
        int flags=buffer.readUnsignedByte(),facing=buffer.readUnsignedByte(),hinge=buffer.readUnsignedByte();
        int layout=buffer.readUnsignedByte(),count=buffer.readUnsignedByte();
        if(window<0 || texture<0 || !textureAllowed.test(texture) || position>2 || trigger>2 || facing>3 || hinge>2
                || layout>2 || count>ChannelList.MAX_CHANNELS || buffer.readableBytes()!=count*4)
            throw new IllegalArgumentException("Invalid trapdoor edit fields");
        for(int i=0;i<count;i++)if(buffer.getInt(buffer.readerIndex()+i*4)<0)
            throw new IllegalArgumentException("Negative trapdoor channel");
        int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=buffer.readInt();
        var choices=new TrapdoorData(texture,position,layout,trigger,(flags&16)!=0,(flags&2)!=0,
                (flags&1)!=0,(flags&4)!=0,(flags&8)!=0);
        var settings=new NativeTrapdoorSettings(choices,ChannelList.of(channels),facing,hinge,(flags&64)!=0,(flags&128)!=0);
        return new NativeTrapdoorEdit(window,settings,(flags&32)!=0);
    }
    public void encode(ByteBuf buffer) {
        var c=settings.choices();
        buffer.writeInt(window);buffer.writeInt(c.texture);buffer.writeByte(c.position);buffer.writeByte(c.trigger);
        buffer.writeByte((c.sliding?1:0)|(c.cover?2:0)|(c.intoWall?4:0)|(c.overSurface?8:0)|(c.tileTexture?16:0)
                |(inverted?32:0)|(settings.rotationFlipped()?64:0)|(settings.diagonal()?128:0));
        buffer.writeByte(settings.facing());buffer.writeByte(settings.hingeSide());buffer.writeByte(c.layout());
        buffer.writeByte(settings.channels().size());
        for(int i=0;i<settings.channels().size();i++)buffer.writeInt(settings.channels().get(i));
    }
}
