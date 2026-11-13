package com.vandorlabs.render;

/** Conservative condition for retaining the original ordering granularity. */
public final class GlassPaneOrdering {
    /**
     * U is Z on an X-normal pane and X on a Z-normal pane. Two perpendicular
     * batches which both cross the other's plane cannot be ordered as wholes.
     * Touching edges and vertically disjoint panes do not require a split.
     */
    public static boolean requiresSplit(int axisA,double planeA,double minUA,double maxUA,double minYA,double maxYA,
            int axisB,double planeB,double minUB,double maxUB,double minYB,double maxYB){
        if(axisA==axisB||maxYA<=minYB||maxYB<=minYA)return false;
        return planeB>minUA+1E-7&&planeB<maxUA-1E-7&&planeA>minUB+1E-7&&planeA<maxUB-1E-7;
    }
    private GlassPaneOrdering(){}
}
