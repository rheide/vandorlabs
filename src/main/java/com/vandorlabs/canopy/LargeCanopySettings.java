package com.vandorlabs.canopy;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.tiles.GlassTextures;
/** Stable settings shared by placed blocks, configured items and captured craft. */
public final class LargeCanopySettings {
    public int depth=16,scale=71,shade=0,tint=1;
    public static boolean supports(String kind){return kind.equals("large_front_canopy_glass") || kind.equals("large_front_canopy_glass_5x5");}
    public static boolean valid(int depth,int scale,int shade,int tint){return depth>=0 && depth<=80 && scale>=10 && scale<=100 && GlassTextures.valid(shade) && GlassTextures.validTint(tint);}
    public int length(){return Math.max(1,(depth+15)/16);}
    public String key(){return depth+":"+scale+":"+shade+":"+tint;}
    public void write(NBTTagCompound tag){tag.setInteger("CanopyDepth",depth);tag.setInteger("CanopyScale",scale);tag.setInteger("CanopyShade",shade);tag.setInteger("CanopyTint",tint);}
    public void read(NBTTagCompound tag){depth=tag.hasKey("CanopyDepth",3)?Math.max(0,Math.min(80,tag.getInteger("CanopyDepth"))):16;scale=tag.hasKey("CanopyScale",3)?Math.max(10,Math.min(100,tag.getInteger("CanopyScale"))):71;shade=GlassTextures.clamp(tag.getInteger("CanopyShade"));tint=tag.hasKey("CanopyTint",3)?GlassTextures.clampTint(tag.getInteger("CanopyTint")):1;}
}
