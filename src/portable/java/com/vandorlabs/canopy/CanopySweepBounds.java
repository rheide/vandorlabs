package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;

/** Released analytic coordinate extrema; a context is worker-owned and contains no world references. */
public final class CanopySweepBounds {
    private CanopySweepBounds(){}
    public static final class Context {
        private final boolean large,opening,hinged;
        private final CanopyTransform start,finish,zero,half,end;
        private final double low,high,c,s,d,t,det,padding;
        private final boolean interval;private int uncertainAxes;
        private final double[] min=new double[3],max=new double[3];
        public Context(String kind,int width,CanopySettingsData settings,double from,double to){
            if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||from>1||to<0||to>1)throw new IllegalArgumentException("Canopy sweep interval");
            interval=from!=to;padding=8*Math.ulp(Math.max(1,width+settings.depth/16D+2));large=CanopySettingsData.supports(kind)&&settings.openable;opening=kind.equals("canopy")||kind.equals("long_canopy");hinged=settings.motion!=0;
            double angle=105/180D*Math.PI,middle=angle/2;c=Math.cos(middle)-1;s=Math.sin(middle);d=Math.cos(angle)-1;t=Math.sin(angle);det=c*t-d*s;
            low=angle*Math.min(from*from*(3-2*from),to*to*(3-2*to));high=angle*Math.max(from*from*(3-2*from),to*to*(3-2*to));
            start=new CanopyTransform(kind,width,settings,from);finish=new CanopyTransform(kind,width,settings,to);zero=new CanopyTransform(kind,width,settings,0);half=new CanopyTransform(kind,width,settings,.5);end=new CanopyTransform(kind,width,settings,1);
        }
        public int uncertainAxes(){return uncertainAxes;}
        public double padding(){return padding;}
        private static double coordinate(CanopyPoint p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}
        public CanopyPhysics.Box bounds(CanopyFace face){
            uncertainAxes=0;boolean rotating=large||opening&&hinged&&face.group.equals("shell");for(int axis=0;axis<3;axis++){min[axis]=Double.POSITIVE_INFINITY;max[axis]=Double.NEGATIVE_INFINITY;}
            for(int vertex=0;vertex<face.points();vertex++){CanopyPoint original=face.vertex(vertex),a=start.apply(original,face.group),b=finish.apply(original,face.group),z=rotating?zero.apply(original,face.group):a,h=rotating?half.apply(original,face.group):a,e=rotating?end.apply(original,face.group):b;
                for(int axis=0;axis<3;axis++){double av=coordinate(a,axis),bv=coordinate(b,axis),zv=coordinate(z,axis),hv=coordinate(h,axis),ev=coordinate(e,axis);min[axis]=Math.min(min[axis],Math.min(av,bv));max[axis]=Math.max(max[axis],Math.max(av,bv));if(!rotating)continue;double ac=((hv-zv)*t-(ev-zv)*s)/det,bs=(c*(ev-zv)-d*(hv-zv))/det,constant=zv-ac;if(Math.abs(ac)+Math.abs(bs)<1e-12)continue;if(interval)uncertainAxes|=1<<axis;double extremum=Math.atan2(bs,ac);for(int turn=-2;turn<=2;turn++){double theta=extremum+turn*Math.PI;if(theta>low&&theta<high){double value=constant+ac*Math.cos(theta)+bs*Math.sin(theta);min[axis]=Math.min(min[axis],value);max[axis]=Math.max(max[axis],value);}}}
            }return new CanopyPhysics.Box(min[0],min[1],min[2],max[0],max[1],max[2]);
        }
    }
    public static int worldAxes(int axes,int facing){return facing==4||facing==5?(axes&2)|((axes&1)<<2)|((axes&4)>>2):axes;}
    /** Small moving-axis-only discovery bound covers measured cross-JDK trig and world addition rounding. */
    public static CanopyPhysics.Box conservativeWorld(Context context,CanopyPhysics.Box local,int facing,int x,int y,int z){int axes=context.uncertainAxes();double p=context.padding();CanopyPhysics.Box expanded=new CanopyPhysics.Box(local.minX-((axes&1)!=0?p:0),local.minY-((axes&2)!=0?p:0),local.minZ-((axes&4)!=0?p:0),local.maxX+((axes&1)!=0?p:0),local.maxY+((axes&2)!=0?p:0),local.maxZ+((axes&4)!=0?p:0));CanopyPhysics.Box world=world(expanded,facing,x,y,z);int mapped=worldAxes(axes,facing);return new CanopyPhysics.Box((mapped&1)!=0?Math.nextDown(world.minX):world.minX,(mapped&2)!=0?Math.nextDown(world.minY):world.minY,(mapped&4)!=0?Math.nextDown(world.minZ):world.minZ,(mapped&1)!=0?Math.nextUp(world.maxX):world.maxX,(mapped&2)!=0?Math.nextUp(world.maxY):world.maxY,(mapped&4)!=0?Math.nextUp(world.maxZ):world.maxZ);}
    public static CanopyPhysics.Box world(CanopyPhysics.Box local,int facing,int x,int y,int z){CanopyPoint a=CanopyTransform.rotate(new CanopyPoint(local.minX,local.minY,local.minZ),facing),b=CanopyTransform.rotate(new CanopyPoint(local.maxX,local.maxY,local.maxZ),facing);return new CanopyPhysics.Box(a.x,a.y,a.z,b.x,b.y,b.z).offset(x,y,z);}
}
