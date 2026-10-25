package com.vandorlabs.persistence;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Typed glass/porthole settings shared by the wall, full porthole and dormant common tile fields. */
public final class WallAppearance {
    public final int shade,tint,material,shape,layout;
    public final boolean framed,stretch,textureJoin,join;
    public WallAppearance(int shade,int tint,int material,boolean framed,boolean stretch,boolean textureJoin,boolean join,int shape,int layout){this.shade=shade;this.tint=tint;this.material=material;this.framed=framed;this.stretch=stretch;this.textureJoin=textureJoin;this.join=join;this.shape=shape;this.layout=layout;}
    /** Porthole factories enable joining; an old save with no JoinPortholes tag reads false. */
    public static WallAppearance fresh(boolean porthole){return new WallAppearance(2,1,-1,true,false,true,porthole,0,-1);}
    public int textureLayout(){return layout<0?(stretch?0:1):layout;}
    public static int savedLayout(PrimitiveData tag,String key){int value=tag.getInt(key);return tag.containsInt(key)&&TextureLayoutMode.valid(value)?value:-1;}
    public static WallAppearance read(PrimitiveData tag,IntUnaryOperator material){return new WallAppearance(tag.containsInt("GlassShade")?GlassTextures.clamp(tag.getInt("GlassShade")):2,tag.containsInt("GlassTint")?GlassTextures.clampTint(tag.getInt("GlassTint")):1,tag.containsInt("GlassMaterial")?material.applyAsInt(tag.getInt("GlassMaterial")):-1,!tag.contains("GlassFramed")||tag.getBoolean("GlassFramed"),tag.getBoolean("GlassStretch"),!tag.contains("GlassTextureJoin")||tag.getBoolean("GlassTextureJoin"),tag.getBoolean("JoinPortholes"),tag.containsInt("PortholeShape")?Math.max(0,Math.min(3,tag.getInt("PortholeShape"))):0,savedLayout(tag,"GlassTextureLayout"));}
    public void write(PrimitiveData tag){tag.putInt("GlassShade",shade);tag.putInt("GlassTint",tint);tag.putInt("GlassMaterial",material);tag.putBoolean("GlassFramed",framed);tag.putBoolean("GlassStretch",stretch);tag.putBoolean("GlassTextureJoin",textureJoin);tag.putBoolean("JoinPortholes",join);tag.putInt("PortholeShape",shape);tag.putInt("GlassTextureLayout",textureLayout());}
    public static boolean validShape(int shape){return shape>=0&&shape<=3;}
    public static boolean validMaterial(int material,IntPredicate choices){return material==-1||choices.test(material);}
    public static int clampMaterial(int material,IntPredicate choices){return validMaterial(material,choices)?material:-1;}
    public static float opacity(int material,int tint){return material<0?1F:tint==0?.35F:tint==1?.65F:1F;}
    public WallAppearance withGlass(int shade,int material){return new WallAppearance(shade,tint,material,framed,stretch,textureJoin,join,shape,layout);}
    public WallAppearance withOptions(boolean join,int shape,int tint,boolean framed,int layout,boolean textureJoin){return new WallAppearance(shade,tint,material,framed,layout!=1,textureJoin,join,shape,layout);}
}
