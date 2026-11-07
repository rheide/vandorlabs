package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** World-free ordinary glass collision publication, separate from its enclosing selection bounds. */
public final class FlatGlassGeometry {
    public static final class Box {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        private Box(double x0,double z0,double x1,double z1){minX=x0;minY=0;minZ=z0;maxX=x1;maxY=1;maxZ=z1;}
        public double coordinate(int index){switch(index){case 0:return minX;case 1:return minY;case 2:return minZ;case 3:return maxX;case 4:return maxY;case 5:return maxZ;default:throw new IndexOutOfBoundsException();}}
    }
    public static final class Snapshot {
        public final List<Box> boxes;
        public final Box bounds;
        public final int facing;
        public final double near;
        private Snapshot(int facing,double near,List<Box> boxes){this.facing=facing;this.near=near;this.boxes=Collections.unmodifiableList(boxes);double x0=1,z0=1,x1=0,z1=0;for(Box b:boxes){x0=Math.min(x0,b.minX);z0=Math.min(z0,b.minZ);x1=Math.max(x1,b.maxX);z1=Math.max(z1,b.maxZ);}bounds=new Box(x0,z0,x1,z1);}
        public LegacyBoxTrace.Hit trace(double sx,double sy,double sz,double ex,double ey,double ez){LegacyBoxTrace.Hit nearest=null;for(Box box:boxes){LegacyBoxTrace.Hit hit=LegacyBoxTrace.clip(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,sx,sy,sz,ex,ey,ez);if(hit!=null&&(nearest==null||hit.distance(sx,sy,sz)<nearest.distance(sx,sy,sz)))nearest=hit;}return nearest;}
        public int side(double x,double y,double z){double normal=facing==5?1-x:facing==3?1-z:facing==4?x:z;return normal<near/16D+.125?0:1;}
    }
    private FlatGlassGeometry(){}
    /** Facing is the stable Minecraft face index NORTH=2,SOUTH=3,WEST=4,EAST=5; near is already mirrored for saved panel facing. */
    public static Snapshot build(int facing,double near,PanelCornerGeometry.FlatCorner corner){
        if(facing<2||facing>5||!Double.isFinite(near)||near<0||near>12)throw new IllegalArgumentException("Ordinary glass plane");
        List<Box> boxes=new ArrayList<Box>(3);
        add(boxes,facing,corner==null?0:corner.left(),corner==null?16:corner.right(),near,near+4);
        if(corner!=null&&corner.frontArm!=null)add(boxes,facing,corner.frontArm,corner.frontArm+4,0,near);
        if(corner!=null&&corner.backArm!=null)add(boxes,facing,corner.backArm,corner.backArm+4,near+4,16);
        return new Snapshot(facing,near,boxes);
    }
    private static void add(List<Box> boxes,int facing,double x0,double x1,double z0,double z1){
        // Source's actual entity-box intersection excludes zero-volume arms at near/far depths.
        if(x1<=x0||z1<=z0)return;
        x0/=16;x1/=16;z0/=16;z1/=16;
        switch(facing){case 5:boxes.add(new Box(1-z1,x0,1-z0,x1));break;case 3:boxes.add(new Box(1-x1,1-z1,1-x0,1-z0));break;case 4:boxes.add(new Box(z0,1-x1,z1,1-x0));break;default:boxes.add(new Box(x0,z0,x1,z1));}
    }
}
