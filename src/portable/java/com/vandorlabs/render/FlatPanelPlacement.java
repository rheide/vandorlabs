package com.vandorlabs.render;

/** Stable panel depth and local-side coordinates; facing IDs follow saved vanilla directions. */
public final class FlatPanelPlacement {
    private FlatPanelPlacement(){}
    public static int depth(float coordinate){return coordinate<1F/3F?1:coordinate>2F/3F?2:0;}
    public static int depth(int facing,float hitX,float hitZ){
        float coordinate=facing==3?1F-hitZ:facing==5?1F-hitX:facing==4?hitX:hitZ;
        return depth(coordinate);
    }
    public static int near(int depth){return depth==1?0:depth==2?12:6;}
    public static double coordinate(int facing,double x,double z){return facing==5?1-x:facing==3?1-z:facing==4?x:z;}
    public static int side(int facing,double near,double x,double z){return coordinate(facing,x,z)<near/16D+.125?0:1;}
    public static ControlMount.Point rotate(ControlMount.Point p,int facing){
        switch(facing){case 5:return new ControlMount.Point(1-p.z,p.y,p.x);case 3:return new ControlMount.Point(1-p.x,p.y,1-p.z);case 4:return new ControlMount.Point(p.z,p.y,1-p.x);default:return p;}
    }
}
