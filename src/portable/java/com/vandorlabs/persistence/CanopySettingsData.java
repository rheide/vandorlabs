package com.vandorlabs.persistence;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.GlassTextures;

/** Standalone canopy settings retain their own defaults and legacy texture-layout precedence. */
public final class CanopySettingsData {
    public final int depth, scale, shade, tint, mount, motion, hinge, angle, material;
    public final boolean openable, redstone, slideForward, tileMaterial, activateOnPower;
    public final ChannelList channels;
    private final int layout;

    public CanopySettingsData(int depth, int scale, int shade, int tint, int mount, int motion,
            int hinge, int angle, int material, boolean openable, boolean redstone,
            boolean slideForward, boolean tileMaterial, boolean activateOnPower, int layout, ChannelList channels) {
        this.depth=depth; this.scale=scale; this.shade=shade; this.tint=tint; this.mount=mount;
        this.motion=motion; this.hinge=hinge; this.angle=angle; this.material=material;
        this.openable=openable; this.redstone=redstone; this.slideForward=slideForward;
        this.tileMaterial=tileMaterial; this.activateOnPower=activateOnPower; this.layout=layout;
        this.channels=java.util.Objects.requireNonNull(channels);
    }
    public static CanopySettingsData fresh() {
        return new CanopySettingsData(16,71,0,1,0,0,2,1,-1,false,false,false,false,true,0,ChannelList.EMPTY);
    }
    public int textureLayout() { return layout==2?2:tileMaterial?1:0; }
    public int length() { return Math.max(1,(depth+15)/16); }
    public static boolean supports(String kind) {
        return "front_canopy_glass".equals(kind)||"large_front_canopy_glass".equals(kind);
    }
    public boolean valid() { return depth>=0&&depth<=80&&scale>=10&&scale<=100&&GlassTextures.valid(shade)&&GlassTextures.validTint(tint); }
    private static int clamp(int value,int min,int max) { return Math.max(min,Math.min(max,value)); }
    public static CanopySettingsData read(HousingTags tag) {
        ChannelList channels;
        try { channels=ChannelList.of(tag.getIntArray("CanopyChannels")); }
        catch(IllegalArgumentException malformed) { channels=ChannelList.EMPTY; }
        return new CanopySettingsData(
            tag.containsInt("CanopyDepth")?clamp(tag.getInt("CanopyDepth"),0,80):16,
            tag.containsInt("CanopyScale")?clamp(tag.getInt("CanopyScale"),10,100):71,
            GlassTextures.clamp(tag.getInt("CanopyShade")),
            tag.containsInt("CanopyTint")?GlassTextures.clampTint(tag.getInt("CanopyTint")):1,
            clamp(tag.getInt("CanopyMount"),0,3), clamp(tag.getInt("CanopyMotion"),0,1),
            tag.contains("CanopyHinge")?clamp(tag.getInt("CanopyHinge"),0,3):2,
            tag.contains("CanopyAngle")?clamp(tag.getInt("CanopyAngle"),0,3):1,
            tag.contains("CanopyMaterial")?tag.getInt("CanopyMaterial"):-1,
            tag.getBoolean("CanopyOpenable"), tag.getBoolean("CanopyRedstone"),
            tag.getBoolean("CanopySlideForward"), tag.getBoolean("CanopyTileMaterial"),
            !tag.containsBoolean("CanopyActivateOnPower")||tag.getBoolean("CanopyActivateOnPower"),
            tag.containsInt("CanopyTextureLayout")&&tag.getInt("CanopyTextureLayout")==2?2:0, channels);
    }
    public void write(HousingTags tag) {
        tag.putInt("CanopyDepth",depth); tag.putInt("CanopyScale",scale); tag.putInt("CanopyShade",shade);
        tag.putInt("CanopyTint",tint); tag.putInt("CanopyMount",mount); tag.putInt("CanopyMotion",motion);
        tag.putInt("CanopyHinge",hinge); tag.putInt("CanopyAngle",angle); tag.putInt("CanopyMaterial",material);
        tag.putBoolean("CanopyOpenable",openable); tag.putBoolean("CanopyRedstone",redstone);
        tag.putBoolean("CanopySlideForward",slideForward); tag.putBoolean("CanopyTileMaterial",tileMaterial);
        tag.putBoolean("CanopyActivateOnPower",activateOnPower); tag.putInt("CanopyTextureLayout",textureLayout());
        tag.putIntArray("CanopyChannels",channels.toArray());
    }
}
