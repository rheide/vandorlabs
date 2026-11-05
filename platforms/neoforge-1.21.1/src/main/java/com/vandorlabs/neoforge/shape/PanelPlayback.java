package com.vandorlabs.neoforge.shape;

import com.vandorlabs.persistence.GlassPlaybackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable saved glazing playback; renderer phase is global and never stored here. */
public record PanelPlayback(int mode,int speed) {
    public static final PanelPlayback DEFAULT=new PanelPlayback(1,1);
    private static final GlassPlaybackData.ByteTag<CompoundTag> TAG=new GlassPlaybackData.ByteTag<>(){
        @Override public void putByte(CompoundTag tag,String key,byte value){tag.putByte(key,value);}
        @Override public void remove(CompoundTag tag,String key){tag.remove(key);}
    };
    public PanelPlayback{if(!GlassPlaybackData.valid(mode)||!GlassPlaybackData.valid(speed))throw new IllegalArgumentException("Invalid panel playback");}
    public static PanelPlayback read(CompoundTag tag){int mode=GlassPlaybackData.read(tag.contains(GlassPlaybackData.MODE,Tag.TAG_BYTE),tag.getByte(GlassPlaybackData.MODE)),speed=GlassPlaybackData.read(tag.contains(GlassPlaybackData.SPEED,Tag.TAG_BYTE),tag.getByte(GlassPlaybackData.SPEED));return mode==1&&speed==1?DEFAULT:new PanelPlayback(mode,speed);}
    public void write(CompoundTag tag){GlassPlaybackData.write(TAG,tag,mode,speed);}
}
