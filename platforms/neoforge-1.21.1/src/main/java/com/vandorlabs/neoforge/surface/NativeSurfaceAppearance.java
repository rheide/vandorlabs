package com.vandorlabs.neoforge.surface;

import java.util.List;
import net.minecraft.nbt.*;

/** Public drawing coordinates and artwork only; no claim, archive, capability or controller identity. */
public final class NativeSurfaceAppearance {
    public static CompoundTag project(CompoundTag raw){
        if(raw.contains("NativeSurfaceSchema")&&(!raw.contains("NativeSurfaceSchema",Tag.TAG_INT)||raw.getInt("NativeSurfaceSchema")!=1))throw new IllegalArgumentException("Unsupported Surface appearance schema");
        for(String key:List.of("SurfacePublic","NativeSurfaceInert"))if(raw.contains(key)&&(!raw.contains(key,Tag.TAG_BYTE)||raw.getByte(key)<0||raw.getByte(key)>1))throw new IllegalArgumentException("Surface public envelope flag");
        if(raw.getBoolean("NativeSurfaceInert"))throw new IllegalArgumentException("Inert Surface has no usable public snapshot");
        var result=new CompoundTag();for(String key:List.of("SurfaceOn","SurfaceOff")){if(raw.contains(key)&&!raw.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Surface public artwork type");String value=raw.contains(key)?raw.getString(key):NativeSurfaceArtwork.normalize("",key.equals("SurfaceOn"));if(!NativeSurfaceArtwork.valid(value))throw new IllegalArgumentException("Surface public artwork");result.putString(key,value);}
        for(String key:List.of("SurfaceLayout","SurfaceGroupSize","SurfaceGroupHeight","SurfaceGroupNormal"))if(raw.contains(key)&&!raw.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Surface public integer type");
        int mode=raw.contains("SurfaceLayout")?raw.getInt("SurfaceLayout"):2,width=raw.contains("SurfaceGroupSize")?raw.getInt("SurfaceGroupSize"):1,height=raw.contains("SurfaceGroupHeight")?raw.getInt("SurfaceGroupHeight"):width,normal=raw.contains("SurfaceGroupNormal")?raw.getInt("SurfaceGroupNormal"):1;
        if(mode<0||mode>2||width<1||width>32||height<1||height>32||normal<0||normal>5)throw new IllegalArgumentException("Surface public drawing bounds");
        if(raw.contains("SurfaceGroupOffset")&&!raw.contains("SurfaceGroupOffset",Tag.TAG_LONG))throw new IllegalArgumentException("Surface public drawing origin");long packed=raw.getLong("SurfaceGroupOffset");var offset=raw.contains("NativeSurfaceSchema")||raw.getBoolean("SurfacePublic")?net.minecraft.core.BlockPos.of(packed):new net.minecraft.core.BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(packed),com.vandorlabs.persistence.LegacyBlockPosition.y(packed),com.vandorlabs.persistence.LegacyBlockPosition.z(packed));if(Math.abs((long)offset.getX())>32||Math.abs((long)offset.getY())>32||Math.abs((long)offset.getZ())>32)throw new IllegalArgumentException("Surface public origin bounds");
        for(String key:List.of("SurfaceActive","SurfaceAppearanceConfigured"))if(raw.contains(key)&&(!raw.contains(key,Tag.TAG_BYTE)||raw.getByte(key)<0||raw.getByte(key)>1))throw new IllegalArgumentException("Surface public flag type");
        result.putInt("SurfaceLayout",mode);result.putInt("SurfaceGroupSize",width);if(height!=width)result.putInt("SurfaceGroupHeight",height);result.putInt("SurfaceGroupNormal",normal);result.putLong("SurfaceGroupOffset",offset.asLong());result.putBoolean("SurfaceAppearanceConfigured",raw.getBoolean("SurfaceAppearanceConfigured"));result.putBoolean("SurfaceActive",raw.getBoolean("SurfaceActive"));result.putBoolean("SurfacePublic",true);result.putBoolean("NativeSurfaceInert",false);return result;
    }
    private NativeSurfaceAppearance(){}
}
