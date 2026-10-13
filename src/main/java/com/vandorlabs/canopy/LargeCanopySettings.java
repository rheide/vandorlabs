package com.vandorlabs.canopy;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.tiles.GlassTextures;
/** Stable settings shared by placed blocks, configured items and captured craft. */
public final class LargeCanopySettings {
    public int depth=16,scale=71,shade=0,tint=1,mount=0,motion=0,hinge=2,angle=1,material=-1;
    public boolean openable=false,redstone=false;
    public com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.EMPTY;
    public static boolean supports(String kind){return kind.equals("large_front_canopy_glass") || kind.equals("large_front_canopy_glass_5x5");}
    public static boolean valid(int depth,int scale,int shade,int tint){return depth>=0 && depth<=80 && scale>=10 && scale<=100 && GlassTextures.valid(shade) && GlassTextures.validTint(tint);}
    public int length(){return Math.max(1,(depth+15)/16);}
    public String key(){return depth+":"+scale+":"+shade+":"+tint+":"+mount+":"+motion+":"+hinge+":"+angle+":"+material+":"+openable+":"+redstone+":"+channels;}
    public void write(NBTTagCompound tag){tag.setInteger("CanopyMount",mount);tag.setInteger("CanopyMotion",motion);tag.setInteger("CanopyHinge",hinge);tag.setInteger("CanopyAngle",angle);tag.setInteger("CanopyMaterial",material);tag.setBoolean("CanopyOpenable",openable);tag.setBoolean("CanopyRedstone",redstone);tag.setIntArray("CanopyChannels",channels.toArray());tag.setInteger("CanopyDepth",depth);tag.setInteger("CanopyScale",scale);tag.setInteger("CanopyShade",shade);tag.setInteger("CanopyTint",tint);}
    public void read(NBTTagCompound tag){mount=Math.max(0,Math.min(3,tag.getInteger("CanopyMount")));motion=Math.max(0,Math.min(1,tag.getInteger("CanopyMotion")));hinge=tag.hasKey("CanopyHinge")?Math.max(0,Math.min(3,tag.getInteger("CanopyHinge"))):2;angle=tag.hasKey("CanopyAngle")?Math.max(0,Math.min(2,tag.getInteger("CanopyAngle"))):1;material=tag.hasKey("CanopyMaterial")?tag.getInteger("CanopyMaterial"):-1;openable=tag.getBoolean("CanopyOpenable");redstone=tag.getBoolean("CanopyRedstone");channels=com.vandorlabs.redstone.ChannelData.read(tag,"CanopyChannels",com.vandorlabs.redstone.ChannelList.EMPTY);depth=tag.hasKey("CanopyDepth",3)?Math.max(0,Math.min(80,tag.getInteger("CanopyDepth"))):16;scale=tag.hasKey("CanopyScale",3)?Math.max(10,Math.min(100,tag.getInteger("CanopyScale"))):71;shade=GlassTextures.clamp(tag.getInteger("CanopyShade"));tint=tag.hasKey("CanopyTint",3)?GlassTextures.clampTint(tag.getInteger("CanopyTint")):1;}
}
