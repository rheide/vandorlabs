package com.vandorlabs.render;

/** Wall-aligned rigid panel inset one pixel from both wall surfaces. */
public final class DiagonalTrapdoorGeometry {
    private DiagonalTrapdoorGeometry() { }
    public static double[][] corners(int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose) {
        return corners(mode,inverted,turns,sliding,reverse,pose,reverse?15/16D:1/16D,15/16D);
    }
    public static double[][] corners(int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose,double hinge,double travel) {
        return corners(mode,inverted,turns,sliding,reverse,pose,hinge,travel,1);
    }
    public static double[][] corners(int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose,double hinge,double travel,int slideLiftDirection) {
        return corners(mode,inverted,turns,sliding,reverse,pose,hinge,travel,slideLiftDirection,false);
    }
    public static double[][] corners(int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose,double hinge,double travel,int slideLiftDirection,boolean slideIntoWall) {
        double span=mode==1?.75:.375,p=Math.max(0,Math.min(1,pose));
        double slope=inverted?-span:span;
        double base=(inverted?span:0)+(mode==2 && inverted?.375:0)+1/16D;
        double ay=mode==2?slope:1,az=mode==2?1:slope;
        double length=Math.sqrt(ay*ay+az*az);ay/=length;az/=length;
        double pivotY=mode==2?base+1/16D:0,pivotZ=mode==2?0:base+1/16D;
        double px=hinge,angle=(reverse?-1:1)*(mode!=2 && inverted?-1:1)*p*Math.PI/2;
        double cos=Math.cos(angle),sin=Math.sin(angle);
        double[][] out=new double[8][3];
        for(int i=0;i<8;i++) {
            double x=(i&1)==0?TrapdoorGeometry.EDGE_CLEARANCE:1-TrapdoorGeometry.EDGE_CLEARANCE;
            double y=mode==2?base+slope*((i&4)==0?0:1)+((i&2)==0?0:2/16D):((i&2)==0?0:1);
            double z=mode==2?((i&4)==0?0:1):base+slope*y+((i&4)==0?0:2/16D);
            if(sliding){
                x+=(reverse?1:-1)*(slideIntoWall?p:Math.max(0,(p-.25)/.75))*travel;
                // Lift clear of a solid continuation wall before sliding across it.
                double lift=slideIntoWall?0:.25*Math.min(1,p*4);
                if(mode==2)y+=lift;else z+=slideLiftDirection*lift;
            }
            else {
                double dx=x-px,dy=y-pivotY,dz=z-pivotZ,dot=ay*dy+az*dz;
                x=px+dx*cos+(ay*dz-az*dy)*sin;
                y=pivotY+dy*cos+az*dx*sin+ay*dot*(1-cos);
                z=pivotZ+dz*cos-ay*dx*sin+az*dot*(1-cos);
            }
            for(int t=0;t<(turns&3);t++){double old=x;x=1-z;z=old;}
            out[i]=new double[]{x,y,z};
        }
        return out;
    }
    public static double[] bounds(double[][] corners) {
        double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(double[] v:corners)for(int a=0;a<3;a++){b[a]=Math.min(b[a],v[a]);b[a+3]=Math.max(b[a+3],v[a]);}
        return b;
    }
}
