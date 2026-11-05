package com.vandorlabs.render;

import com.vandorlabs.tiles.GlassTextures;

/** Palette PNG alpha and replacement-artwork vertex alpha share one explicit depth-pass policy. */
public final class GlassRenderPolicy {
    private GlassRenderPolicy() { }
    public static float opacity(int material,int tint){return material<0?1F:tint==0?.35F:tint==1?.65F:tint==2?.85F:1F;}
    public static boolean opaque(int tint){return GlassTextures.clampTint(tint)==GlassTextures.OPAQUE;}
}
