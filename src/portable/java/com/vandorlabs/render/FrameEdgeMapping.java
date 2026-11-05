package com.vandorlabs.render;

/** Current authored rail selection and UV orientation. Pixel preparation and atlas lookup stay native. */
public final class FrameEdgeMapping {
    public static final int TOP=0,BOTTOM=1,LEFT=2,RIGHT=3;
    public static int edge(double[]... points){
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX,cx=0,cy=0;
        for(double[] p:points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);cx+=p[0];cy+=p[1];}
        // Horizontal rails/caps use their top/bottom authored edge, vertical rails their side.
        boolean horizontal=maxX-minX>=maxY-minY;
        return horizontal?(cy/points.length>=8?TOP:BOTTOM):(cx/points.length>=8?RIGHT:LEFT);
    }
    public static void map(int edge,double[]... points){
        boolean horizontal=edge==TOP||edge==BOTTOM;
        // Legacy box sides use U across the wall, while polygonal porthole
        // segments always use V across it. Find the UV axis independent of
        // the edge's geometric direction, before replacing either coordinate.
        int alongAxis=horizontal?0:1;
        double alongMean=0,uMean=0,vMean=0;
        for(double[] p:points){alongMean+=p[alongAxis];uMean+=p[3];vMean+=p[4];}
        alongMean/=points.length;uMean/=points.length;vMean/=points.length;
        double cu=0,cv=0,uu=0,vv=0;
        for(double[] p:points){double a=p[alongAxis]-alongMean,u=p[3]-uMean,v=p[4]-vMean;cu+=a*u;cv+=a*v;uu+=u*u;vv+=v*v;}
        int acrossAxis=(uu>0?cu*cu/uu:0)>=(vv>0?cv*cv/vv:0)?4:3;
        double lo=Double.POSITIVE_INFINITY,hi=-lo;
        for(double[] p:points){double across=p[acrossAxis];lo=Math.min(lo,across);hi=Math.max(hi,across);}
        for(double[] p:points){double across=p[acrossAxis];p[3]=horizontal?p[0]:16-p[1];p[4]=hi>lo?(across-lo)*16/(hi-lo):0;}

    }
    private FrameEdgeMapping(){}
}
