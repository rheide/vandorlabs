package com.vandorlabs.render;

/** Render-only rear-rim clearance; physical shell coordinates remain authoritative. */
public final class FrontCanopyRenderDepth {
    public static final double REAR_CLEARANCE=1D/512;
    private FrontCanopyRenderDepth(){}
    public static double coordinate(double z,double depth,boolean backAnchored){
        double extent=Math.max(1D/64,depth),inset=Math.min(REAR_CLEARANCE,extent/8);
        // Keep the narrow front unchanged, and move the broad rear just inside
        // its own cell. Applying one affine map preserves shared seams/planes.
        return backAnchored?inset+z*(1-inset/extent):z*(1-inset/extent);
    }
}
