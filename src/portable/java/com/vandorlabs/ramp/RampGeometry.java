package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry shared by collision and render adapters. */
public final class RampGeometry {
    private RampGeometry() { }

    public enum Direction { NORTH, SOUTH, WEST, EAST }

    public static final class Box {
        public final double minX, minY, minZ, maxX, maxY, maxZ;
        public Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
            this.minX=minX; this.minY=minY; this.minZ=minZ;
            this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
        }
    }

    public static List<Box> boxes(Direction face,int cellY,int sourceY,double low,double high,
            int row,int length,int drop,int segments,double pose,boolean top,boolean elevator) {
        List<Box> result=new ArrayList<>();
        int count=elevator?1:segments;
        for (int i=0;i<count;i++) {
            double offset=ControllerPlatform.offset(row,i,length,segments,drop,pose,top,elevator);
            double y0=Math.max(0,sourceY+low+offset-cellY);
            double y1=Math.min(1,sourceY+high+offset-cellY);
            if (y1-y0<1e-8) continue;
            double a=(double)i/count,b=(double)(i+1)/count;
            result.add(new Box(face==Direction.EAST?a:face==Direction.WEST?1-b:0,y0,
                    face==Direction.SOUTH?a:face==Direction.NORTH?1-b:0,
                    face==Direction.EAST?b:face==Direction.WEST?1-a:1,y1,
                    face==Direction.SOUTH?b:face==Direction.NORTH?1-a:1));
        }
        return result;
    }

    public static int segmentAt(Direction face,Box box,int segments,boolean elevator) {
        if (elevator) return 0;
        double along=face==Direction.EAST||face==Direction.WEST
                ?(box.minX+box.maxX)/2:(box.minZ+box.maxZ)/2;
        if (face==Direction.NORTH||face==Direction.WEST) along=1-along;
        return Math.max(0,Math.min(segments-1,(int)(along*segments)));
    }

    public static Box footprint(Direction face,int step,int count,double minY,double maxY) {
        double a=(double)step/count,b=(double)(step+1)/count;
        return new Box(face==Direction.EAST?a:face==Direction.WEST?1-b:0,minY,
                face==Direction.SOUTH?a:face==Direction.NORTH?1-b:0,
                face==Direction.EAST?b:face==Direction.WEST?1-a:1,maxY,
                face==Direction.SOUTH?b:face==Direction.NORTH?1-a:1);
    }

    public static int firstOccupiedY(int sourceY,double low,double firstOffset,double secondOffset) {
        return (int)Math.floor(sourceY+low+Math.min(firstOffset,secondOffset)+1e-8);
    }

    public static int lastOccupiedY(int sourceY,double high,double firstOffset,double secondOffset) {
        return (int)Math.ceil(sourceY+high+Math.max(firstOffset,secondOffset)-1e-8)-1;
    }

    /** Target surface, or NaN if the entity is not standing on this moving tread. */
    public static double riderTarget(double before,double after,double feet,double tolerance) {
        if ((Math.abs(feet-before)>tolerance&&Math.abs(feet-after)>tolerance)
                ||Math.abs(after-before)<1e-9) return Double.NaN;
        return after;
    }

}
