package com.vandorlabs.screen;

/** Immutable physical chip tray frame; shared by hit testing and version-specific item rendering. */
public final class ScreenChipMount {
    public static final class Point {
        public final double x,y,z;
        private Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    public static final class Frame {
        public final Point center,u,v,normal;
        public final double width=.34,height=.34;
        private final double[] bounds=new double[6];
        private Frame(Point center,Point u,Point v,Point normal){
            this.center=center;this.u=u;this.v=v;this.normal=normal;
            for(int axis=0;axis<3;axis++){bounds[axis]=Double.POSITIVE_INFINITY;bounds[axis+3]=Double.NEGATIVE_INFINITY;}
            for(int a=-1;a<=1;a+=2)for(int b=-1;b<=1;b+=2)for(int c=0;c<2;c++){
                double x=center.x+u.x*(a*width/2)+v.x*(b*height/2)+normal.x*(c*.09),y=center.y+u.y*(a*width/2)+v.y*(b*height/2)+normal.y*(c*.09),z=center.z+u.z*(a*width/2)+v.z*(b*height/2)+normal.z*(c*.09);
                bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.min(bounds[2],z);bounds[3]=Math.max(bounds[3],x);bounds[4]=Math.max(bounds[4],y);bounds[5]=Math.max(bounds[5],z);
            }
        }
        public double bound(int coordinate){return bounds[coordinate];}
        public boolean hit(double sx,double sy,double sz,double ex,double ey,double ez){
            double dx=ex-sx,dy=ey-sy,dz=ez-sz,divisor=dx*normal.x+dy*normal.y+dz*normal.z;if(divisor>=-1e-8)return false;
            double t=((center.x-sx)*normal.x+(center.y-sy)*normal.y+(center.z-sz)*normal.z)/divisor;if(t<0||t>1)return false;
            double x=sx+dx*t-center.x,y=sy+dy*t-center.y,z=sz+dz*t-center.z;
            return Math.abs(x*u.x+y*u.y+z*u.z)<=width/2+.025&&Math.abs(x*v.x+y*v.y+z*v.z)<=height/2+.025;
        }
    }
    // Only four roles, six faces, two upper/ceiling flags and three fixed ceiling positions are admitted.
    private static final Frame[] FRAMES=new Frame[7*6*2*2*3];
    public static boolean integrated(ScreenFamily family){return family==ScreenFamily.CONSOLE||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.DIAGONAL||family==ScreenFamily.HALF_DIAGONAL;}
    public static synchronized Frame frame(ScreenFamily family,int facing,boolean upper,boolean ceiling,int position){
        if(!integrated(family)||facing<0||facing>5||position<0||position>2)throw new IllegalArgumentException("Unsupported chip mounting pose");
        int key=((((family.ordinal()*6+facing)*2+(upper?1:0))*2+(ceiling?1:0))*3+position);Frame cached=FRAMES[key];if(cached!=null)return cached;
        Frame local=local(family,upper,ceiling,position);
        return FRAMES[key]=new Frame(rotate(local.center,facing),direction(local.u,facing),direction(local.v,facing),direction(local.normal,facing));
    }
    private static final Frame[] LOCAL=new Frame[7*2*2*3];
    /** Authored North-local values, before the released world-facing arithmetic. */
    public static synchronized Frame local(ScreenFamily family,boolean upper,boolean ceiling,int position){
        if(!integrated(family)||position<0||position>2)throw new IllegalArgumentException("Unsupported local chip mounting pose");
        int key=((family.ordinal()*2+(upper?1:0))*2+(ceiling?1:0))*3+position;Frame cached=LOCAL[key];if(cached!=null)return cached;
        Point center,normal,v;
        if(family==ScreenFamily.HALF_DIAGONAL){center=new Point(.5,upper?.937:.063,.25);normal=new Point(0,upper?-1:1,0);}
        else if(family==ScreenFamily.CONSOLE||family==ScreenFamily.HALF_CONSOLE){center=new Point(.22,.068,.24+(ceiling?com.vandorlabs.render.InputSurfaceLayout.ceilingStart(16,position)/16:0));normal=new Point(0,1,0);}
        else{center=new Point(.5,upper?.86:.14,.14);/* 1.12 Vec3d.normalize uses MathHelper.sqrt, which returns float. */double length=(double)(float)Math.sqrt(2);normal=new Point(0,(upper?-1D:1D)/length,-1D/length);}
        v=family==ScreenFamily.DIAGONAL?new Point(1,0,0):new Point(0,0,-1);Point u=new Point(normal.y*v.z-normal.z*v.y,normal.z*v.x-normal.x*v.z,normal.x*v.y-normal.y*v.x);
        return LOCAL[key]=new Frame(center,u,v,normal);
    }
    private static Point direction(Point p,int facing){Point q=rotate(new Point(p.x+.5,p.y+.5,p.z+.5),facing);return new Point(q.x-.5,q.y-.5,q.z-.5);}
    private static Point rotate(Point p,int facing){double x=p.x-.5,y=p.y-.5,z=p.z-.5;switch(facing){case 5:return new Point(.5-z,p.y,.5+x);case 3:return new Point(.5-x,p.y,.5-z);case 4:return new Point(.5+z,p.y,.5-x);case 1:return new Point(.5-x,.5-z,.5-y);case 0:return new Point(p.x,.5+z,.5-y);default:return p;}}
    private ScreenChipMount(){}
}
