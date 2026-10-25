package com.vandorlabs.render;

/** Placement and target choice; the platform retains loaded-world and edit/collision checks. */
public final class SlabMergePolicy {
    private SlabMergePolicy(){}
    public enum Target {NONE,DIRECT,ADJACENT}
    public static boolean upperHalf(int clickedFace,float hitY){return clickedFace==0||clickedFace!=1&&hitY>.5F;}
    public static boolean directMerge(boolean upper,int clickedFace){return clickedFace==(upper?0:1);}
    public static Target target(boolean clickedSlab,boolean upper,int clickedFace,boolean adjacentSlab){return clickedSlab&&directMerge(upper,clickedFace)?Target.DIRECT:adjacentSlab?Target.ADJACENT:Target.NONE;}
}
