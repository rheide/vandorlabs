package com.vandorlabs.ui;

/** Wall-specific appearance choices used beside the common material picker. */
public final class WallAppearanceDialog {
    public static final int JOIN_PORTHOLES=102,SHAPE=104,SIZE=105,FILL_INSIDE=109,FILL_OUTSIDE=110,SLOPE=111,JOIN=112;
    private static final String[] SHAPES={"Hexagon","Octagon","Square","Round"};
    private static final String[] JOINS={"Auto","Straight","Front-left L","Front-right L","Back-left L","Back-right L","Front-left T","Front-right T","Back-left T","Back-right T","Left cross","Right cross"};
    private WallAppearanceDialog() { }
    public static String portholeJoinLabel(boolean joined){return "Join: "+(joined?"On":"Off");}
    public static String shapeLabel(int shape){return "Shape: "+SHAPES[shape];}
    public static String joinLabel(int join){return "Junction: "+JOINS[join];}
    public static String sizeLabel(boolean half,boolean full){return half?"Size: Wide / half height":"Size: "+(full?"Full width / height":"Tall / half width");}
    public static int nextShape(int shape,int direction){return DialogInput.cycle(shape,SHAPES.length,direction);}
    public static int nextJoin(int join,int direction){return DialogInput.cycle(join,JOINS.length,direction);}
    public static int nextSize(int mode,int direction){return DialogInput.cycle(mode,3,direction);}
}
