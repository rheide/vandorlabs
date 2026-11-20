package com.vandorlabs.render;

/** Hinge and travel for a cell in a row of joined programmable trapdoors. */
public final class TrapdoorGroupMotion {
    public static final int SPLIT=0,LOW_EDGE=1,HIGH_EDGE=2,COUNT=3;

    public final boolean high;
    public final double hinge,travel;

    public TrapdoorGroupMotion(int mode,int column,int width) {
        double split=width/2D;
        high=mode==HIGH_EDGE||mode==SPLIT&&column>=split;
        int distance=high?width-1-column:column;
        hinge=TrapdoorGeometry.OPEN_HINGE-distance;
        travel=(mode==SPLIT?(high?width-split:split):width)-TrapdoorGeometry.OPEN_HINGE;
    }
}
