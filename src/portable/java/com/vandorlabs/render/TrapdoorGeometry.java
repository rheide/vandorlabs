package com.vandorlabs.render;

/** One rigid 3px leaf shared by rendering, collision and selection. */
public final class TrapdoorGeometry {
    public static final int BOTTOM=0, MIDDLE=1, TOP=2;
    public static final double THICKNESS=3/16D;
    private TrapdoorGeometry() { }
    public static double low(int position) {
        return position==TOP?12/16D:position==MIDDLE?6.5/16D:1/16D;
    }
    /** Canonical leaf hinges/slides north. Quarter turns map it to world facing. */
    public static double[][] corners(int position,boolean sliding,int quarterTurns,double pose) {
        return corners(position,sliding,quarterTurns,pose,1/16D,15/16D);
    }
    public static double[][] corners(int position,boolean sliding,int quarterTurns,double pose,double hinge,double travel) {
        double low=low(position), high=low+THICKNESS;
        double p=Math.max(0,Math.min(1,pose));
        double angle=(position==TOP?-1:1)*p*Math.PI/2;
        double pivot=position==TOP?high:low;
        double cos=Math.cos(angle),sin=Math.sin(angle);
        double[][] out=new double[8][3];
        for(int i=0;i<8;i++) {
            double x=(i&1)==0?0:1,y=(i&2)==0?low:high,z=(i&4)==0?0:1;
            if(sliding) z-=p*travel;
            else {
                double dy=y-pivot;
                y=pivot+dy*cos+(z-hinge)*sin;
                z=hinge+(z-hinge)*cos-dy*sin;
            }
            for(int turn=0;turn<(quarterTurns&3);turn++) {double old=x;x=1-z;z=old;}
            out[i]=new double[]{x,y,z};
        }
        return out;
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
