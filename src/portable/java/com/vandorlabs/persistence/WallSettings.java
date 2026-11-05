package com.vandorlabs.persistence;

/** Saved wall/porthole choices. World joins and publication belong to each host. */
public final class WallSettings {
    public static final String FULL="DiagonalFullWidth", HALF="DiagonalHalfHeight",
            FILL="DiagonalFill", JOIN="DiagonalJoin", PORTHOLES="JoinPortholes", SHAPE="PortholeShape";
    public final boolean fullWidth,halfHeight,joinPortholes;
    public final int fill,join,portholeShape;
    private WallSettings(boolean full,boolean half,int fill,int join,boolean portholes,int shape){
        fullWidth=full;halfHeight=half;this.fill=fill;this.join=join;joinPortholes=portholes;portholeShape=shape;
    }
    public static int fill(int saved){return Math.max(0,Math.min(3,saved));}
    public static int join(int saved){return Math.max(0,Math.min(11,saved));}
    public static int portholeShape(boolean typedInt,int saved){return typedInt?Math.max(0,Math.min(3,saved)):0;}
    /** Old half/full flags are independent on load; do not rewrite legacy combinations. */
    public static WallSettings read(HousingTags tag){
        return new WallSettings(tag.getBoolean(FULL),tag.getBoolean(HALF),fill(tag.getInt(FILL)),
                join(tag.getInt(JOIN)),tag.getBoolean(PORTHOLES),portholeShape(tag.containsInt(SHAPE),tag.getInt(SHAPE)));
    }
    public static int geometry(boolean half,boolean full){return half?2:full?1:0;}
    public static void write(PrimitiveData tag,boolean full,boolean half,int fill,int join,boolean portholes,int shape){
        tag.putBoolean(FULL,full);tag.putBoolean(HALF,half);tag.putInt(FILL,fill);tag.putInt(JOIN,join);
        tag.putBoolean(PORTHOLES,portholes);tag.putInt(SHAPE,shape);
    }
}
