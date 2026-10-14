package com.vandorlabs.tiles;

/** Regular panel artwork shares glass tint controls without changing the original glass palette. */
public final class GlassMaterials {
    public static boolean valid(int material){return material==-1 || ScreenHousingTextures.validChoice(material);}
    public static int clamp(int material){return valid(material)?material:-1;}
    public static float opacity(int material,int tint){return material<0?1F:tint==0?.35F:tint==1?.65F:1F;}
    public static String texture(int material,int shade,int size,int tint){return material<0?GlassTextures.texture(shade,size,tint):ScreenHousingTextures.texture(material);}
    private GlassMaterials(){}
}
