package com.vandorlabs.copier.core;

import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;

/** Released typed channel fields only. Callers select known child owners; opaque compounds are never traversed. */
public final class CopyChannelFields {
    public interface Tag extends HousingTags,ControlLatchData.Tag,com.vandorlabs.screen.ScreenArtworkMigration.Tag {}
    private static final String[] SCALARS={"RedstoneChannel","SpaceDoorChannel","Channel"};
    private static final String[] ARRAYS={"RedstoneChannels","CanopyChannels","LatchedChannels"};
    private static final String[] STRINGS={"GearChannels","ScanChannels","RotationChannels","DirectionChannels","RestoreChannels","CopyChannels"};
    public static void shift(Tag tag,int delta){
        ChannelRange.requireIncrement(delta);if(delta==0)return;
        for(String key:SCALARS)if(tag.containsInt(key))tag.putInt(key,ChannelRange.shift(tag.getInt(key),delta));
        for(String key:ARRAYS)if(tag.containsIntArray(key)){int[] values=tag.getIntArray(key).clone();for(int i=0;i<values.length;i++)values[i]=ChannelRange.shift(values[i],delta);tag.putIntArray(key,values);}
        for(String key:STRINGS)if(tag.containsString(key)){ChannelList values=ChannelList.parse(tag.getString(key));if(values==null)throw new IllegalArgumentException("Cannot shift malformed "+key);int[] shifted=values.toArray();for(int i=0;i<shifted.length;i++)shifted[i]=ChannelRange.shift(shifted[i],delta);tag.putString(key,ChannelList.of(shifted).toString());}
    }
    private CopyChannelFields(){}
}
