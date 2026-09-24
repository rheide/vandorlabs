package com.vandorlabs.ramp;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Horizontal same-material selection and downhill translation for controllers. */
public final class ControllerPlatform {
    public static final int MAX_WIDTH = 8;
    public static final int MAX_LENGTH = 16;
    public static final int MAX_AREA = MAX_WIDTH * MAX_LENGTH;
    public static final int MAX_DROP = 8;
    private ControllerPlatform() { }

    public static final class Cell {
        public final int x,y,z;
        public Cell(int x,int y,int z) { this.x=x; this.y=y; this.z=z; }
        public Cell offset(int dx,int dy,int dz) { return new Cell(x+dx,y+dy,z+dz); }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof Cell)) return false;
            Cell c=(Cell)obj;
            return x==c.x && y==c.y && z==c.z;
        }
        @Override public int hashCode() { return (x*31+y)*31+z; }
    }

    /** Connected growth from the front seed, clipped to an eight-wide, sixteen-long footprint.
     * Fixed neighbor order makes clipping repeatable, including seeds in the middle.
     * Out-of-bounds cells are ignored without querying the world. */
    public static Set<Cell> discover(Cell seed, Predicate<Cell> matches) {
        return discover(seed,RampGeometry.Direction.SOUTH,matches);
    }
    public static Set<Cell> discover(Cell seed,RampGeometry.Direction direction,Predicate<Cell> matches) {
        boolean alongX=direction==RampGeometry.Direction.EAST || direction==RampGeometry.Direction.WEST;
        int spanX=alongX?MAX_LENGTH:MAX_WIDTH,spanZ=alongX?MAX_WIDTH:MAX_LENGTH;
        Set<Cell> found = new HashSet<>(), seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        int minX=seed.x,maxX=seed.x,minZ=seed.z,maxZ=seed.z;
        queue.add(seed);
        while (!queue.isEmpty()) {
            Cell c = queue.removeFirst();
            if (!seen.add(c)) continue;
            int x0=Math.min(minX,c.x),x1=Math.max(maxX,c.x);
            int z0=Math.min(minZ,c.z),z1=Math.max(maxZ,c.z);
            if (x1-x0>=spanX || z1-z0>=spanZ || !matches.test(c)) continue;
            minX=x0; maxX=x1; minZ=z0; maxZ=z1;
            found.add(c);
            queue.add(c.offset(1,0,0)); queue.add(c.offset(-1,0,0));
            queue.add(c.offset(0,0,1)); queue.add(c.offset(0,0,-1));
        }
        return found;
    }

    /** First tread is the fixed hinge, last tread reaches the configured drop. */
    public static double drop(int row, int segment, int length, int segments, int height, double pose) {
        double p = Math.max(0, Math.min(1, pose));
        return height * (row * segments + segment) / (double)(length * segments - 1)
                * p * p * (3 - 2*p);
    }

    public static double offset(int row,int segment,int length,int segments,int height,
            double pose,boolean top,boolean elevator) {
        double p=Math.max(0,Math.min(1,pose));
        double travel=elevator?height*p*p*(3-2*p):drop(row,segment,length,segments,height,p);
        return top?-travel:travel;
    }
    /** Signed endpoint heights relative to the original platform, with a fixed ramp hinge. */
    public static double offset(int row,int segment,int length,int segments,int start,int end,
            double pose,boolean elevator) {
        double p=Math.max(0,Math.min(1,pose));
        double height=start+(end-start)*p*p*(3-2*p);
        return elevator?height:height*(row*segments+segment)/(double)(length*segments-1);
    }
    public static boolean validTreadPixels(int pixels) {
        return pixels>=1 && pixels<=16 && (pixels & (pixels-1))==0;
    }
    /** Step to an allowed size, including when editing an older intermediate-size save. */
    public static int stepTreadPixels(int pixels,boolean increase) {
        if (increase) {
            for (int size=1;size<=16;size*=2) if (size>pixels) return size;
            return 16;
        }
        for (int size=16;size>=1;size/=2) if (size<pixels) return size;
        return 1;
    }
    public static int treadCount(int pixels) { return (16+pixels-1)/pixels; }
    public static double treadStart(int step,int pixels) { return step*pixels/16.0; }
    public static double treadEnd(int step,int pixels) { return Math.min(1,(step+1)*pixels/16.0); }
    public static double offsetPixels(int row,int step,int length,int pixels,int start,int end,
            double pose,boolean elevator) {
        return offsetPixels(row,step,length,pixels,start,end,pose,elevator,false);
    }
    public static double offsetPixels(int row,int step,int length,int pixels,int start,int end,
            double pose,boolean elevator,boolean fast) {
        double p=Math.max(0,Math.min(1,pose));
        double height=start+(end-start)*(fast?p:p*p*(3-2*p));
        double last=length-1+treadStart(treadCount(pixels)-1,pixels);
        // A one-block ramp with one full-block tread has no separate hinge tread.
        return elevator || last==0?height:height*(row+treadStart(step,pixels))/last;
    }
    public static int duration(int length,int height,boolean slow) {
        return duration(length,height,slow?2:1);
    }
    public static int duration(int length,int height,int speed) {
        return (speed==0?5:speed==2?20:10)*Math.max(1,Math.max(length,height));
    }
    /** Undo segment translation before sampling the original block's side texture.
     * Keeping the source coordinate (rather than normalizing a clipped slice) also
     * preserves slab UVs and continuity where one segment crosses two world cells. */
    public static double sideTextureV(int cellY,double localY,int sourceY,double offset) {
        return Math.max(0,Math.min(1,1-((cellY-sourceY)+localY-offset)));
    }
    public static double pose(double from,boolean open,double elapsed,int duration) {
        double distance=Math.max(0,elapsed)/Math.max(1,duration);
        return open?Math.min(1,from+distance):Math.max(0,from-distance);
    }
}
