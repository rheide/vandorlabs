package com.vandorlabs.segmented;

import java.util.function.IntPredicate;

/** Optional artwork owned by one existing segment cell; no topology or world references. */
public final class SegmentedCellArtwork {
    public static final String FIELD="SegmentedCellTexture";
    public static final SegmentedCellArtwork INHERITED=new SegmentedCellArtwork(false,0);
    public final boolean overridden;
    public final int choice;
    private SegmentedCellArtwork(boolean overridden,int choice){this.overridden=overridden;this.choice=choice;}
    /** The caller supplies its actual registered material policy, including custom encodings. */
    public static SegmentedCellArtwork explicit(int choice,IntPredicate valid){
        if(valid==null||!valid.test(choice))throw new IllegalArgumentException("Invalid segment artwork");
        return new SegmentedCellArtwork(true,choice);
    }
    public int effective(int inherited){return overridden?choice:inherited;}
    @Override public boolean equals(Object other){if(!(other instanceof SegmentedCellArtwork))return false;SegmentedCellArtwork value=(SegmentedCellArtwork)other;return overridden==value.overridden&&(!overridden||choice==value.choice);}
    @Override public int hashCode(){return overridden?31+choice:0;}
}
