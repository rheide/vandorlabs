package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry shared by collision and render adapters. */
public final class RampGeometry {
    private RampGeometry() { }

    public enum Direction { NORTH, SOUTH, WEST, EAST }

    /** Travel is relative to the face selected in the controller. */
    public static final int VERTICAL=0, LEFT=1, RIGHT=2;

    public static final class Box {
        public final double minX, minY, minZ, maxX, maxY, maxZ;
        public Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
            this.minX=minX; this.minY=minY; this.minZ=minZ;
            this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
        }
    }

    public static Box movingTread(Direction face,int sourceX,int sourceY,int sourceZ,
            double low,double high,int row,int length,int start,int end,int pixels,
            int step,double from,double to,boolean elevator,int travel,boolean extend) {
        return movingTread(face,sourceX,sourceY,sourceZ,low,high,row,length,start,end,pixels,
                step,from,to,elevator,travel,extend,false);
    }
    public static Box movingTread(Direction face,int sourceX,int sourceY,int sourceZ,
            double low,double high,int row,int length,int start,int end,int pixels,
            int step,double from,double to,boolean elevator,int travel,boolean extend,boolean fast) {
        Box footprint=footprintPixels(face,step,elevator?16:pixels,low,high);
        double a=ControllerPlatform.offsetPixels(row,step,length,pixels,start,end,from,elevator,fast);
        double b=ControllerPlatform.offsetPixels(row,step,length,pixels,start,end,to,elevator,fast);
        if (extend) {
            double initial=ControllerPlatform.offsetPixels(row,step,length,pixels,start,end,0,elevator,fast);
            double lower=Math.min(initial,Math.min(a,b));
            b=Math.max(initial,Math.max(a,b));
            a=lower;
        }
        double lo=Math.min(a,b),hi=Math.max(a,b);
        int dx=0,dz=0;
        if (travel==LEFT || travel==RIGHT) {
            dx=face==Direction.NORTH?-1:face==Direction.SOUTH?1:0;
            dz=face==Direction.EAST?-1:face==Direction.WEST?1:0;
            if (travel==RIGHT) { dx=-dx; dz=-dz; }
        }
        return new Box(sourceX+footprint.minX+(dx>0?lo:dx<0?-hi:0),
                sourceY+footprint.minY+(travel==VERTICAL?lo:0),
                sourceZ+footprint.minZ+(dz>0?lo:dz<0?-hi:0),
                sourceX+footprint.maxX+(dx>0?hi:dx<0?-lo:0),
                sourceY+footprint.maxY+(travel==VERTICAL?hi:0),
                sourceZ+footprint.maxZ+(dz>0?hi:dz<0?-lo:0));
    }

    public static Box clip(Box box,int x,int y,int z) {
        double x0=Math.max(0,box.minX-x),x1=Math.min(1,box.maxX-x);
        double y0=Math.max(0,box.minY-y),y1=Math.min(1,box.maxY-y);
        double z0=Math.max(0,box.minZ-z),z1=Math.min(1,box.maxZ-z);
        return x1-x0>1e-8 && y1-y0>1e-8 && z1-z0>1e-8
                ?new Box(x0,y0,z0,x1,y1,z1):null;
    }

    public static List<Box> boxes(Direction face,int cellY,int sourceY,double low,double high,
            int row,int length,int drop,int segments,double pose,boolean top,boolean elevator) {
        return boxes(face,cellY,sourceY,low,high,row,length,0,top?-drop:drop,segments,pose,elevator);
    }
    public static List<Box> boxes(Direction face,int cellY,int sourceY,double low,double high,
            int row,int length,int start,int end,int segments,double pose,boolean elevator) {
        List<Box> result=new ArrayList<>();
        int count=elevator?1:segments;
        for (int i=0;i<count;i++) {
            double offset=ControllerPlatform.offset(row,i,length,segments,start,end,pose,elevator);
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

    public static List<Box> boxesPixels(Direction face,int cellY,int sourceY,double low,double high,
            int row,int length,int start,int end,int pixels,double pose,boolean elevator) {
        List<Box> result=new ArrayList<>();
        for (int step=0;step<(elevator?1:ControllerPlatform.treadCount(pixels));step++) {
            double offset=ControllerPlatform.offsetPixels(row,step,length,pixels,start,end,pose,elevator);
            double y0=Math.max(0,sourceY+low+offset-cellY),y1=Math.min(1,sourceY+high+offset-cellY);
            if (y1-y0>=1e-8) result.add(footprintPixels(face,step,elevator?16:pixels,y0,y1));
        }
        return result;
    }
    public static int segmentAtPixels(Direction face,Box box,int pixels,boolean elevator) {
        if (elevator) return 0;
        double along=face==Direction.EAST||face==Direction.WEST
                ?(box.minX+box.maxX)/2:(box.minZ+box.maxZ)/2;
        if (face==Direction.NORTH||face==Direction.WEST) along=1-along;
        return Math.max(0,Math.min(ControllerPlatform.treadCount(pixels)-1,(int)(along*16/pixels)));
    }
    public static Box footprintPixels(Direction face,int step,int pixels,double minY,double maxY) {
        return footprint(face,ControllerPlatform.treadStart(step,pixels),
                ControllerPlatform.treadEnd(step,pixels),minY,maxY);
    }
    public static int segmentAt(Direction face,Box box,int segments,boolean elevator) {
        if (elevator) return 0;
        double along=face==Direction.EAST||face==Direction.WEST
                ?(box.minX+box.maxX)/2:(box.minZ+box.maxZ)/2;
        if (face==Direction.NORTH||face==Direction.WEST) along=1-along;
        return Math.max(0,Math.min(segments-1,(int)(along*segments)));
    }

    public static Box footprint(Direction face,int step,int count,double minY,double maxY) {
        return footprint(face,(double)step/count,(double)(step+1)/count,minY,maxY);
    }
    private static Box footprint(Direction face,double a,double b,double minY,double maxY) {
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
