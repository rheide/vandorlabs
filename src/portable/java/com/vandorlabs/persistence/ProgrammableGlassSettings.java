package com.vandorlabs.persistence;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.function.IntUnaryOperator;

/** Glass-specific saved defaults, including the original metadata-size migration signal. */
public final class ProgrammableGlassSettings {
    public final int shade,material,layout,tint,size,panelFacing;
    public final boolean stretch,framed,join,legacySize;
    public ProgrammableGlassSettings(int shade,int material,int layout,boolean stretch,int tint,boolean framed,int size,boolean join,int panelFacing,boolean legacySize){
        this.shade=shade;this.material=material;this.layout=layout;this.stretch=stretch;this.tint=tint;this.framed=framed;this.size=size;this.join=join;this.panelFacing=panelFacing;this.legacySize=legacySize;
    }
    public static ProgrammableGlassSettings fresh(){return new ProgrammableGlassSettings(0,-1,-1,false,1,true,1,true,-1,false);}
    public int textureLayout(){return layout<0?(stretch?0:1):layout;}
    public static ProgrammableGlassSettings read(PrimitiveData tag,IntUnaryOperator material){
        int layout=tag.getInt("GlassTextureLayout");boolean legacySize=!tag.contains("GlassSize");
        return new ProgrammableGlassSettings(GlassTextures.clamp(tag.getInt("GlassShade")),
                tag.containsInt("GlassMaterial")?material.applyAsInt(tag.getInt("GlassMaterial")):-1,
                tag.containsInt("GlassTextureLayout")&&TextureLayoutMode.valid(layout)?layout:-1,
                tag.getBoolean("GlassStretch"),tag.containsInt("GlassTint")?GlassTextures.clampTint(tag.getInt("GlassTint")):1,
                !tag.contains("GlassFramed")||tag.getBoolean("GlassFramed"),legacySize?1:Math.max(0,Math.min(1,tag.getInt("GlassSize"))),
                !tag.contains("GlassJoin")||tag.getBoolean("GlassJoin"),tag.containsInt("GlassPanelFacing")?Math.floorMod(tag.getInt("GlassPanelFacing"),4):-1,legacySize);
    }
    public void write(PrimitiveData tag){
        tag.putInt("GlassShade",shade);tag.putInt("GlassMaterial",material);tag.putBoolean("GlassStretch",stretch);tag.putInt("GlassTextureLayout",textureLayout());
        tag.putInt("GlassTint",tint);tag.putBoolean("GlassFramed",framed);tag.putInt("GlassSize",size);tag.putBoolean("GlassJoin",join);
        if(panelFacing>=0)tag.putInt("GlassPanelFacing",panelFacing);
    }
}
