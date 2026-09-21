package com.vandorlabs.render;

/** Stable semantic identity; legacy item metadata is confined to the 1.12 adapter. */
public enum DoorLeaf {
    LEFT("left_leaf",1), RIGHT("right_leaf",2);
    public final String modelSuffix;
    public final int legacyMetadata;
    DoorLeaf(String modelSuffix,int legacyMetadata) {
        this.modelSuffix=modelSuffix; this.legacyMetadata=legacyMetadata;
    }
    public static DoorLeaf fromRight(boolean right) { return right?RIGHT:LEFT; }
}
