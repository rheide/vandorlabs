package com.vandorlabs.gear.core;
import java.util.*;

/** Bounded immutable Source reservation order. All coordinates are relative to the physical mount. */
public final class LegacyLandingLayout {
    public static final int MAX_DEPTH=5,MAX_CELLS=53;
    public static final class Offset {
        public final int x,y,z;
        private Offset(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    }
    private static final List<List<List<Offset>>> CELLS=build();
    private static List<List<List<Offset>>> build(){List<List<List<Offset>>> all=new ArrayList<>();for(int shape=0;shape<3;shape++){List<List<Offset>> depths=new ArrayList<>();int radius=shape==0?0:1,min=shape==2?0:-radius;for(int count=0;count<=MAX_DEPTH;count++){List<Offset> cells=new ArrayList<>();for(int depth=0;depth<=count;depth++)for(int x=min;x<=radius;x++)for(int z=min;z<=radius;z++)if(depth!=0||x!=0||z!=0)cells.add(new Offset(x,-depth,z));depths.add(Collections.unmodifiableList(cells));}all.add(Collections.unmodifiableList(depths));}return Collections.unmodifiableList(all);}
    public static int depth(double distance,int size){if(!Double.isFinite(distance)||distance<0||distance>4||size<0||size>4)throw new IllegalArgumentException("Telescopic footprint bounds");return (int)Math.ceil(distance)+(size>=3?1:0);}
    public static List<Offset> reserved(double distance,int size){int count=depth(distance,size);return CELLS.get(size<3?0:size-2).get(count);}
    public static boolean kept(int depth,int x,int z,int keep,int size){int radius=size>=3?1:0;return depth<=keep+(radius==1?1:0)&&Math.abs(x)<=radius&&Math.abs(z)<=radius&&(size!=4||x>=0&&z>=0)&&(depth>0||radius==1);}
    public static boolean ownerOffset(int x,int y,int z){return Math.abs((long)x)<=1&&Math.abs((long)z)<=1&&y>=0&&y<=MAX_DEPTH&&(x!=0||y!=0||z!=0);}
    public static boolean connectedEnd(boolean loaded,boolean sameBlock,int ownSize,int otherSize,int ownFacing,int otherFacing,float ownProgress,float otherProgress){return loaded&&sameBlock&&ownSize==otherSize&&(ownFacing&1)==(otherFacing&1)&&Math.abs(ownProgress-otherProgress)<.001F;}
    public static int endStep(int size){return size==4?2:1;}
    private LegacyLandingLayout(){}
}
