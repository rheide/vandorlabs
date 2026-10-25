package com.vandorlabs.tiles;

/** Regular panel artwork shares glass tint controls without changing the original glass palette. */
public final class GlassMaterials {
    private static final java.util.concurrent.atomic.AtomicLong REVISION=new java.util.concurrent.atomic.AtomicLong();
    public static void changed(){REVISION.incrementAndGet();}
    public static long revision(){return REVISION.get();}
    public static boolean valid(int material){return com.vandorlabs.persistence.WallAppearance.validMaterial(material,ScreenHousingTextures::validChoice);}
    public static int clamp(int material){return com.vandorlabs.persistence.WallAppearance.clampMaterial(material,ScreenHousingTextures::validChoice);}
    public static float opacity(int material,int tint){return com.vandorlabs.persistence.WallAppearance.opacity(material,tint);}
    public static String texture(int material,int shade,int size,int tint){return material<0?GlassTextures.texture(shade,size,tint):ScreenHousingTextures.texture(material);}
    private GlassMaterials(){}
}
