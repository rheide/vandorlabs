package com.vandorlabs.persistence;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.function.IntUnaryOperator;

/** Current animated-selector panel glass fields; standalone Glass has a different saved contract. */
public final class PanelGlassSettings {
    public static final String SHADE="GlassShade",TINT="GlassTint",MATERIAL="GlassMaterial",STRETCH="GlassStretch",
            TEXTURE_JOIN="GlassTextureJoin",FRAMED="GlassFramed",LAYOUT="GlassTextureLayout";
    public final int shade,tint,material,layout;
    public final boolean stretch,textureJoin,framed;
    public PanelGlassSettings(int shade,int material,int layout,boolean stretch,int tint,boolean framed,boolean textureJoin){
        this.shade=shade;this.material=material;this.layout=layout;this.stretch=stretch;this.tint=tint;this.framed=framed;this.textureJoin=textureJoin;
    }
    public static PanelGlassSettings fresh(){return new PanelGlassSettings(2,-1,-1,false,1,true,true);}
    public static int shade(boolean typedInt,int saved){return typedInt?GlassTextures.clamp(saved):2;}
    public static int tint(boolean typedInt,int saved){return typedInt?GlassTextures.clampTint(saved):1;}
    public static int rawLayout(boolean typedInt,int saved){return typedInt&&TextureLayoutMode.valid(saved)?saved:-1;}
    public static boolean flag(boolean present,boolean saved){return !present||saved;}
    public static int textureLayout(int rawLayout,boolean stretch){return rawLayout<0?(stretch?0:1):rawLayout;}
    public int textureLayout(){return textureLayout(layout,stretch);}
    public static PanelGlassSettings read(HousingTags tag,IntUnaryOperator material){
        return new PanelGlassSettings(shade(tag.containsInt(SHADE),tag.getInt(SHADE)),tag.containsInt(MATERIAL)?material.applyAsInt(tag.getInt(MATERIAL)):-1,
                rawLayout(tag.containsInt(LAYOUT),tag.getInt(LAYOUT)),tag.getBoolean(STRETCH),tint(tag.containsInt(TINT),tag.getInt(TINT)),
                flag(tag.contains(FRAMED),tag.getBoolean(FRAMED)),flag(tag.contains(TEXTURE_JOIN),tag.getBoolean(TEXTURE_JOIN)));
    }
    public void write(PrimitiveData tag){write(tag,shade,tint,material,stretch,textureJoin,framed,layout);}
    /** Existing hot saves provide scalars; no immutable settings object is allocated by the writer. */
    public static void write(PrimitiveData tag,int shade,int tint,int material,boolean stretch,boolean textureJoin,boolean framed,int rawLayout){
        tag.putInt(SHADE,shade);tag.putInt(TINT,tint);tag.putInt(MATERIAL,material);tag.putBoolean(STRETCH,stretch);tag.putBoolean(TEXTURE_JOIN,textureJoin);tag.putBoolean(FRAMED,framed);tag.putInt(LAYOUT,textureLayout(rawLayout,stretch));
    }
}
