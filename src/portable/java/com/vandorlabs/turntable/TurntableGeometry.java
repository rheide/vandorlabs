package com.vandorlabs.turntable;

/** Surface coordinates and rotation shared by scanning, rendering and collision. */
public final class TurntableGeometry {
    private TurntableGeometry() { }
    public enum Face {
        DOWN(0,-1,0, 1,0,0, 0,0,-1), UP(0,1,0, 1,0,0, 0,0,1),
        NORTH(0,0,-1, -1,0,0, 0,-1,0), SOUTH(0,0,1, 1,0,0, 0,-1,0),
        WEST(-1,0,0, 0,0,1, 0,-1,0), EAST(1,0,0, 0,0,-1, 0,-1,0);
        public final int x,y,z,ux,uy,uz,vx,vy,vz;
        Face(int x,int y,int z,int ux,int uy,int uz,int vx,int vy,int vz) {
            this.x=x;this.y=y;this.z=z;this.ux=ux;this.uy=uy;this.uz=uz;this.vx=vx;this.vy=vy;this.vz=vz;
        }
        public int axis(){return x!=0?0:y!=0?1:2;}
        public Face opposite(){return values()[ordinal()^1];}
        public int u(int x,int y,int z){return x*ux+y*uy+z*uz;}
        public int v(int x,int y,int z){return x*vx+y*vy+z*vz;}
        public int plane(int x,int y,int z){return x*this.x+y*this.y+z*this.z;}
        public double[] point(double u,double v,double normal){return new double[]{ux*u+vx*v+x*normal,uy*u+vy*v+y*normal,uz*u+vz*v+z*normal};}
    }

    public static double wrap(double degrees){return ((degrees%360)+360)%360;}
    public static double degreesPerTick(double rpm){
        if(!Double.isFinite(rpm) || rpm<.1 || rpm>30)throw new IllegalArgumentException("Speed must be between 0.1 and 30 RPM");
        return rpm*.3;
    }
    /** Positive degrees are clockwise when looking at the active face from outside. */
    public static double[] rotate(double x,double y,double z,Face normal,double degrees){
        double angle=Math.toRadians(-degrees),c=Math.cos(angle),s=Math.sin(angle),dot=x*normal.x+y*normal.y+z*normal.z;
        return new double[]{x*c+(normal.y*z-normal.z*y)*s+normal.x*dot*(1-c),
                y*c+(normal.z*x-normal.x*z)*s+normal.y*dot*(1-c),
                z*c+(normal.x*y-normal.y*x)*s+normal.z*dot*(1-c)};
    }
    public static double[] bounds(double[] box,Face normal,double degrees){
        double[] result={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(int i=0;i<8;i++){
            double[] p=rotate(box[(i&1)==0?0:3],box[(i&2)==0?1:4],box[(i&4)==0?2:5],normal,degrees);
            for(int axis=0;axis<3;axis++){result[axis]=Math.min(result[axis],p[axis]);result[axis+3]=Math.max(result[axis+3],p[axis]);}
        }
        return result;
    }
    public static boolean square(int minU,int minV,int maxU,int maxV,int cells){
        long width=(long)maxU-minU+1,height=(long)maxV-minV+1;
        return width>0 && width<=16 && width==height && width*height==cells;
    }
}
