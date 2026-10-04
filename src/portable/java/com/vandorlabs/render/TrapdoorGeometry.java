package com.vandorlabs.render;

/** One rigid 3px leaf shared by rendering, collision and selection. */
public final class TrapdoorGeometry {
    public static final int BOTTOM=0, MIDDLE=1, TOP=2;
    public static final double THICKNESS=3/16D;
    public static final double EDGE_CLEARANCE=1/1024D;
    public static final double OPEN_HINGE=1/16D;
    public static final double COVER_OVERHANG=1/16D;
    private TrapdoorGeometry() { }
    public static double low(int position) {
        return position==TOP?1-THICKNESS-EDGE_CLEARANCE:position==MIDDLE?6.5/16D:EDGE_CLEARANCE;
    }
    /** Canonical leaf hinges/slides north. Quarter turns map it to world facing. */
    public static double[][] corners(int position,boolean sliding,int quarterTurns,double pose) {
        return corners(position,sliding,quarterTurns,pose,OPEN_HINGE,15/16D);
    }
    public static double[][] corners(int position,boolean sliding,int quarterTurns,double pose,double hinge,double travel) {
        double low=low(position), high=low+THICKNESS;
        double p=Math.max(0,Math.min(1,pose));
        double angle=(position==TOP?-1:1)*p*Math.PI/2;
        double pivot=position==TOP?high:low;
        double cos=Math.cos(angle),sin=Math.sin(angle);
        double[][] out=new double[8][];
        for(int i=0;i<8;i++) {
            double x=(i&1)==0?EDGE_CLEARANCE:1-EDGE_CLEARANCE,y=(i&2)==0?low:high,z=(i&4)==0?EDGE_CLEARANCE:1-EDGE_CLEARANCE;
            if(sliding) z-=p*travel;
            else {
                double dy=y-pivot;
                y=pivot+dy*cos+(z-hinge)*sin;
                // Closed floor/ceiling leaves sit flush. Move the rotating panel
                // slightly inward to retain clearance from its support when open.
                if(position!=MIDDLE)y+=Math.abs(sin)*(position==TOP?-1:1)*(OPEN_HINGE-EDGE_CLEARANCE);
                // Keep the full thickness inside the mounting edge throughout
                // rotation, finishing flush like vanilla. Retain saved assembly
                // pivots while removing their original one-pixel open inset.
                z=hinge+(z-hinge)*cos-dy*sin+Math.abs(sin)*THICKNESS
                        -(OPEN_HINGE-EDGE_CLEARANCE)*(1-cos);
            }
            for(int turn=0;turn<(quarterTurns&3);turn++) {double old=x;x=1-z;z=old;}
            out[i]=new double[]{x,y,z};
        }
        return out;
    }
    /** Lift above a neighbouring full-block surface, then retract across it. */
    public static double[][] surfaceCorners(int position,int quarterTurns,double pose,double hinge,double travel) {
        double p=Math.max(0,Math.min(1,pose));
        double[][] vertices=corners(position,true,quarterTurns,Math.max(0,(p-.25)/.75),hinge,travel);
        double lift=(1+EDGE_CLEARANCE-low(position))*Math.min(1,p*4);
        for(double[] vertex:vertices)vertex[1]+=lift;
        return vertices;
    }

    /** An adjacent mount owns the cover while its closed leaf spans the neighboring cell. */
    public static double[][] coverCorners(int position,int quarterTurns,double pose) {
        return coverCorners(position,true,quarterTurns,pose);
    }
    public static double[][] coverCorners(int position,boolean sliding,int quarterTurns,double pose) {
        return coverCorners(position,sliding,quarterTurns,pose,COVER_OVERHANG);
    }
    public static double[][] coverCorners(int position,boolean sliding,int quarterTurns,double pose,double overhang) {
        double[][] vertices=corners(position,true,0,0);
        double p=Math.max(0,Math.min(1,pose)),pivot=low(position)+(position==TOP?THICKNESS:0);
        double angle=(position==TOP?1:-1)*p*Math.PI/2,cos=Math.cos(angle),sin=Math.sin(angle),hinge=EDGE_CLEARANCE;
        double extension=sliding?-overhang:overhang;
        for(double[] point:vertices){
            // Sliding overlaps the owning mount by one pixel; rotation retains
            // its far-edge protrusion. Both retract fully into the owning cell.
            point[2]-=1+extension;
            if(sliding)point[2]+=p*(1+extension);
            else {double dy=point[1]-pivot,dz=point[2]-hinge;point[1]=pivot+dy*cos+dz*sin;point[2]=hinge+dz*cos-dy*sin;}
            for(int turn=0;turn<(quarterTurns&3);turn++){double x=point[0];point[0]=1-point[2];point[2]=x;}
        }
        return vertices;
    }
    public static double[] bounds(int position,boolean sliding,int quarterTurns,double pose) {
        double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(double[] p:corners(position,sliding,quarterTurns,pose))
            for(int axis=0;axis<3;axis++){b[axis]=Math.min(b[axis],p[axis]);b[axis+3]=Math.max(b[axis+3],p[axis]);}
        return b;
    }
    /** Counter-clockwise outside faces; corner indices encode x/y/z bits. */
    public static final int[][] FACES={{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5}};
}
