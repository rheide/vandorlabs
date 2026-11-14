package com.vandorlabs.items;

import com.vandorlabs.persistence.GlassPlaybackData;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import com.vandorlabs.render.TextureLayoutMode;

/** Released global Copifier preflight; validate before changing any applicable target field. */
public final class CopiedSettingsValidation {
    public interface Access<T> {
        boolean contains(T tag,String key);
        boolean type(T tag,String key,int type);
        int integer(T tag,String key);
        byte numberByte(T tag,String key);
        int[] integers(T tag,String key);
        T compound(T tag,String key);
    }
    private static final String[] LAYOUTS={"texture_layout","door_texture_layout","trapdoor_texture_layout"};
    private static final String[] PLAYBACK={GlassPlaybackData.MODE,GlassPlaybackData.SPEED};
    public static <T> boolean valid(T values,Access<T> tags){
        if(values==null)return false;
        if(tags.contains(values,"texture_join_policy")){
            if(!tags.type(values,"texture_join_policy",10))return false;
            T policy=tags.compound(values,"texture_join_policy");
            if(!tags.type(policy,"Direction",3)||!tags.type(policy,"Parity",3)
                    ||!com.vandorlabs.surface.TextureJoinSettings.valid(tags.integer(policy,"Direction"),tags.integer(policy,"Parity")))return false;
        }
        for(String key:LAYOUTS)if(tags.type(values,key,3)&&!TextureLayoutMode.valid(tags.integer(values,key)))return false;
        if(tags.type(values,"glass_layout",10)){
            T glass=tags.compound(values,"glass_layout");
            for(String key:PLAYBACK)if(tags.contains(glass,key)&&(!tags.type(glass,key,1)||!GlassPlaybackData.valid(tags.numberByte(glass,key))))return false;
            if(tags.type(glass,"Mode",3)&&!TextureLayoutMode.valid(tags.integer(glass,"Mode")))return false;
        }
        if(tags.type(values,"stair_tread",3)&&!ControllerPlatform.validTreadPixels(tags.integer(values,"stair_tread")))return false;
        if(tags.type(values,"stair_roll",3)&&(tags.integer(values,"stair_roll")<0||tags.integer(values,"stair_roll")>2))return false;
        if(tags.type(values,"Transmission",10)){
            T transmission=tags.compound(values,"Transmission");
            if(!tags.type(transmission,"Channels",11)||!tags.type(transmission,"Range",3)||!tags.type(transmission,"Local",1)||!TransmissionSettings.validRange(tags.integer(transmission,"Range")))return false;
            try{ChannelList.of(tags.integers(transmission,"Channels"));}catch(IllegalArgumentException invalid){return false;}
        }
        return true;
    }
    private CopiedSettingsValidation(){}
}
