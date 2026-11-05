package com.vandorlabs.render;

/** Exact placement/merge decisions; permission, collision, publication and inventory remain host-owned. */
public final class SlabMergePolicy {
    private SlabMergePolicy(){}
    public static boolean upperHalf(int clickedFace,float hitY){return clickedFace==0||clickedFace!=1&&hitY>.5F;}
    public static boolean directMerge(boolean upper,int clickedFace){return clickedFace==(upper?0:1);}
}
