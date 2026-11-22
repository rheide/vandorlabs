package com.vandorlabs.render;

import com.vandorlabs.tiles.GlassTextures;

/** Palette PNG alpha and replacement-artwork vertex alpha share one explicit depth-pass policy. */
public final class GlassRenderPolicy {
    private GlassRenderPolicy() { }
    public static float opacity(int material,int tint){return material<0?1F:tint==0?.35F:tint==1?.65F:tint==2?.85F:1F;}
    /** One culled sheet with this alpha matches two stacked sheets of {@code alpha}. */
    public static float singleLayerOpacity(float alpha){float clear=1F-alpha;return 1F-clear*clear;}
    public static boolean opaque(int tint){return GlassTextures.clampTint(tint)==GlassTextures.OPAQUE;}
}
