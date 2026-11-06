package com.vandorlabs.persistence;

import java.util.function.IntUnaryOperator;
import com.vandorlabs.render.TextureLayoutMode;

/** World-free settings shared by regular and diagonal trapdoors. Links and power stay on the host. */
public final class TrapdoorData {
    public final int texture, position, rawLayout, trigger;
    public final boolean tileTexture, cover, sliding, intoWall, overSurface;

    public TrapdoorData(int texture, int position, int rawLayout, int trigger,
                        boolean tileTexture, boolean cover, boolean sliding, boolean intoWall, boolean overSurface) {
        if(position<0 || position>2 || rawLayout< -1 || rawLayout>2 || !SpaceDoorData.validTrigger(trigger))
            throw new IllegalArgumentException("Invalid trapdoor settings");
        this.texture=texture; this.position=position; this.rawLayout=rawLayout; this.trigger=trigger;
        this.tileTexture=tileTexture; this.cover=cover; this.sliding=sliding;
        this.intoWall=intoWall; this.overSurface=overSurface;
    }

    public int layout() { return rawLayout<0 ? (tileTexture?1:0) : rawLayout; }

    public static TrapdoorData read(HousingTags tag, boolean diagonal, int defaultTexture, IntUnaryOperator clampTexture) {
        int texture=tag.containsInt("housingTexture")?clampTexture.applyAsInt(tag.getInt("housingTexture")):defaultTexture;
        int position=Math.max(0,Math.min(2,tag.getInt("TrapdoorPosition")));
        boolean tile=!tag.containsBoolean("TrapdoorTileTexture") || tag.getBoolean("TrapdoorTileTexture");
        int layout=tag.containsInt("TrapdoorTextureLayout") && TextureLayoutMode.valid(tag.getInt("TrapdoorTextureLayout"))
                ?tag.getInt("TrapdoorTextureLayout"):-1;
        int trigger=tag.containsInt("TrapdoorTrigger")?tag.getInt("TrapdoorTrigger"):SpaceDoorData.TRIGGER_REDSTONE_ON;
        if(!SpaceDoorData.validTrigger(trigger))trigger=SpaceDoorData.TRIGGER_REDSTONE_ON;
        return new TrapdoorData(texture,position,layout,trigger,tile,!diagonal && tag.getBoolean("TrapdoorCover"),
                tag.getBoolean("TrapdoorSliding"),diagonal && tag.getBoolean("TrapdoorSlideIntoWall"),
                !diagonal && tag.getBoolean("TrapdoorSlideOverSurface"));
    }

    /** Configured-item fields only; never adds topology, signal or world coordinates. */
    public void write(HousingTags tag) {
        tag.putInt("housingTexture",texture); tag.putInt("TrapdoorPosition",position);
        tag.putBoolean("TrapdoorTileTexture",tileTexture); tag.putInt("TrapdoorTextureLayout",layout());
        tag.putBoolean("TrapdoorCover",cover); tag.putBoolean("TrapdoorSliding",sliding);
        tag.putBoolean("TrapdoorSlideIntoWall",intoWall); tag.putBoolean("TrapdoorSlideOverSurface",overSurface);
        tag.putInt("TrapdoorTrigger",trigger);
    }
}
