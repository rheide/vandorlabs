package com.vandorlabs.neoforge.light;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.LightData;
import net.minecraft.nbt.CompoundTag;

/** Inventory artwork follows saved manual state and configured brightness, not live signal samples. */
public record NativeLightItemStyle(int texture,boolean lit,int faceTexture) {
    public NativeLightItemStyle {if(texture<0||texture>=com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length||faceTexture< -1)throw new IllegalArgumentException("Invalid Light inventory style");}
    public static final NativeLightItemStyle DEFAULT=new NativeLightItemStyle(0,true,-1);
    public static NativeLightItemStyle read(CompoundTag tag){
        if(tag==null)return DEFAULT;
        var data=LightData.read(new ModernHousingTags(tag),0,ModernFinishCatalog::storedChoice);
        return new NativeLightItemStyle(data.texture,data.manualOn&&data.configuredLevel>0,data.faceTexture);
    }
    public int modelKey(){return texture*2+(lit?1:0);}
}
