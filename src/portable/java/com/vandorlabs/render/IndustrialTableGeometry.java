package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Authored table plates and eight-strip diagonal collision, in block coordinates. */
public final class IndustrialTableGeometry {
    public static final class Box {
        public final double x0,y0,z0,x1,y1,z1;
        private Box(double x0,double y0,double z0,double x1,double y1,double z1){this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;}
    }
    private static final List<Box>[][][] CACHE=build();
    private IndustrialTableGeometry(){}
    public static boolean matches(boolean loaded,boolean sameBlock,int facing,boolean inverted,int otherFacing,boolean otherInverted){return loaded&&sameBlock&&facing==otherFacing&&inverted==otherInverted;}
    public static int supportFace(boolean inverted){return inverted?0:1;}
    public static boolean invertedPlacement(int clickedFace){return clickedFace==0;}
    public static List<Box> collision(int facing,boolean inverted,boolean left,boolean right){return CACHE[index(facing)][inverted?1:0][(left?1:0)|(right?2:0)];}
    private static int index(int facing){switch(facing){case 2:return 0;case 5:return 1;case 3:return 2;case 4:return 3;default:throw new IllegalArgumentException("Horizontal facing required");}}
    @SuppressWarnings("unchecked") private static List<Box>[][][] build(){
        List<Box>[][][] result=(List<Box>[][][])new List<?>[4][2][4];
        for(int turn=0;turn<4;turn++)for(int inverted=0;inverted<2;inverted++)for(int mask=0;mask<4;mask++){
            List<Box> boxes=new ArrayList<>();add(boxes,turn,inverted!=0,0,14,0,16,16,16);add(boxes,turn,inverted!=0,0,0,14,16,14,16);
            if((mask&1)==0)strut(boxes,turn,inverted!=0,.5);if((mask&2)==0)strut(boxes,turn,inverted!=0,13.5);result[turn][inverted][mask]=Collections.unmodifiableList(boxes);
        }return result;
    }
    private static void strut(List<Box> boxes,int turns,boolean inverted,double x){double start=9-8/Math.sqrt(2),end=9+8/Math.sqrt(2);for(int i=0;i<8;i++){double y0=start+(end-start)*i/8,y1=start+(end-start)*(i+1)/8;add(boxes,turns,inverted,x,y0-.707107,17-y1-.707107,x+2,y1+.707107,17-y0+.707107);}}
    private static void add(List<Box> boxes,int turns,boolean inverted,double x0,double y0,double z0,double x1,double y1,double z1){
        if(inverted){double previous=y0;y0=16-y1;y1=16-previous;}Box b=new Box(x0/16,y0/16,z0/16,x1/16,y1/16,z1/16);
        for(int i=0;i<turns;i++)b=new Box(1-b.z1,b.y0,b.x0,1-b.z0,b.y1,b.x1);boxes.add(b);
    }
}
