package com.vandorlabs.neoforge.canopy;

/** Immutable render facts prepared by the host; no tile, world, position or raw NBT is retained. */
public record NativeCanopyRenderSnapshot(String kind,int facing,int width,int mask,int roof,boolean centered,NativeCanopySettings settings) {
    private static final java.util.Set<String> KINDS=java.util.Set.of("angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height");
    public NativeCanopyRenderSnapshot {
        java.util.Objects.requireNonNull(kind);java.util.Objects.requireNonNull(settings);
        if(!KINDS.contains(kind)||facing<2||facing>5||width<1||width>9||mask<0||mask>15||roof<0||(roof&~mask)!=0)throw new IllegalArgumentException("Canopy render facts");
    }
}
