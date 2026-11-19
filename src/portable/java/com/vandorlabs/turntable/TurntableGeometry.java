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
    public static void checkSnapTolerance(double tolerance){
        if(!Double.isFinite(tolerance)||tolerance<0||tolerance>5)throw new IllegalArgumentException("Auto-snap must be between 0 and 5 degrees");
    }
    public static boolean canAutoSnap(double angle,double tolerance){
        checkSnapTolerance(tolerance);
        if(!Double.isFinite(angle))return false;
        double heading=wrap(angle);
        return Math.abs(heading-Math.round(heading/90)*90)<=Math.max(.001,tolerance)+1e-7;
    }
    public static double degreesPerTick(double rpm){
        if(!Double.isFinite(rpm) || rpm<.5 || rpm>10 || Math.abs(rpm*2-Math.rint(rpm*2))>1e-7)throw new IllegalArgumentException("Speed must be between 0.5 and 10 RPM in 0.5 RPM steps");
        return rpm*.3;
    }
    /** Positive degrees are clockwise when looking at the active face from outside. */
    public static double[] rotate(double x,double y,double z,Face normal,double degrees){
        double angle=Math.toRadians(-degrees),c=Math.cos(angle),s=Math.sin(angle),dot=x*normal.x+y*normal.y+z*normal.z;
        return new double[]{x*c+(normal.y*z-normal.z*y)*s+normal.x*dot*(1-c),
                y*c+(normal.z*x-normal.x*z)*s+normal.y*dot*(1-c),
                z*c+(normal.x*y-normal.y*x)*s+normal.z*dot*(1-c)};
    }
    /** Project a block center to the grid at a supported quarter-turn heading. */
    public static int[] rotateCell(int x,int y,int z,double pivotX,double pivotY,double pivotZ,Face normal,int quarter){
        double dx=x+.5-pivotX,dy=y+.5-pivotY,dz=z+.5-pivotZ;
        double u=dx*normal.ux+dy*normal.uy+dz*normal.uz;
        double v=dx*normal.vx+dy*normal.vy+dz*normal.vz;
        double depth=dx*normal.x+dy*normal.y+dz*normal.z;
        switch(Math.floorMod(quarter,4)){
            case 1: double before=u;u=-v;v=before;break;
            case 2: u=-u;v=-v;break;
            case 3: double original=u;u=v;v=-original;break;
            default: break;
        }
        return new int[]{
            Math.toIntExact(Math.round(normal.ux*u+normal.vx*v+normal.x*depth+pivotX-.5)),
            Math.toIntExact(Math.round(normal.uy*u+normal.vy*v+normal.y*depth+pivotY-.5)),
            Math.toIntExact(Math.round(normal.uz*u+normal.vz*v+normal.z*depth+pivotZ-.5))
        };
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
        return width>0 && width<=32 && width==height && width*height==cells;
    }
}
