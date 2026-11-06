package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.render.TrapdoorLeafPose;

/** Coherent world-free host publication. Group scans and contact authorization precede publication. */
public record NativeTrapdoorVisual(NativeTrapdoorSettings settings, TrapdoorLeafPose pose,
                                   ArtworkBounds artwork, int frameJoins, boolean open) {
    public NativeTrapdoorVisual {
        if (settings == null || pose == null || artwork == null || frameJoins < 0 || frameJoins > 15
                || settings.diagonal() != pose.diagonal)
            throw new IllegalArgumentException("Trapdoor visual publication");
    }
    /** Group artwork extents along U/V, relative to this owner's integer artwork-plane origin. */
    public record ArtworkBounds(double minU, double minV, double maxU, double maxV) {
        public ArtworkBounds {
            if (!Double.isFinite(minU) || !Double.isFinite(minV) || !Double.isFinite(maxU)
                    || !Double.isFinite(maxV) || maxU <= minU || maxV <= minV
                    || Math.abs(minU) > 64 || Math.abs(minV) > 64
                    || Math.abs(maxU) > 64 || Math.abs(maxV) > 64)
                throw new IllegalArgumentException("Bounded trapdoor artwork group");
        }
    }
}
