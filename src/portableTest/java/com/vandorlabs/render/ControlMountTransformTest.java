package com.vandorlabs.render;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Geometry invariants across the complete mount domain; legacy raw-bit goldens live in the adapter checks. */
public final class ControlMountTransformTest {
    private static final ControlMountTransform.PointFactory<double[]> POINTS=(x,y,z)->new double[]{x,y,z};
    private static final ControlMountTransform.BoxFactory<double[]> BOXES=(a,b,c,d,e,f)->new double[]{a,b,c,d,e,f};
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double expected,double actual,String message){require(Math.abs(expected-actual)<1e-12,message+" expected="+expected+" actual="+actual);}
    // Minecraft face ordering is Y,Y,Z,Z,X,X, while point coordinates are X,Y,Z.
    private static double n(int face,int axis){return face<2?(axis==1?(face==0?-1:1):0):face<4?(axis==2?(face==2?-1:1):0):(axis==0?(face==4?-1:1):0);}
    private static ControlMountTransform<double[],double[]> mount(int face,int rotation,int height,int tilt,int direction){return new ControlMountTransform<>(face,rotation,height,tilt,direction,.25,.25,.25,.75,.75,.75,POINTS,BOXES);}
    public static void main(String[] ignored)throws Exception{
        int cases=0;
        for(int face=0;face<6;face++)for(int rotation=0;rotation<4;rotation++)for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
            checkMount(mount(face,rotation,height,tilt,direction),mount(face,rotation,0,tilt,direction),face,height);cases++;
        }
        ControlMountTransform<double[],double[]> flat=mount(1,0,0,0,0);for(double value:flat.transform(-0.0,-0.0,-0.0))require(Double.doubleToRawLongBits(value)==0,"vector result must normalize signed zero");
        double[] first=flat.transform(.25,.5,.75);first[0]=99;near(.25,flat.transform(.25,.5,.75)[0],"output mutation leaked into immutable kernel");
        for(int invalid:new int[]{-1,6})try{mount(invalid,0,0,0,0);throw new AssertionError("invalid face accepted");}catch(IllegalArgumentException expected){}
        final ControlMountTransform<double[],double[]> shared=mount(0,3,3,3,2);final double[] expected=shared.bounds(.11330098,.0625,.30394670,.88669902,.9375,.87565048);AtomicReference<Throwable> failed=new AtomicReference<>();Thread[] workers=new Thread[4];
        for(int i=0;i<workers.length;i++)workers[i]=new Thread(()->{try{for(int repeat=0;repeat<1000;repeat++){double[] actual=shared.bounds(.11330098,.0625,.30394670,.88669902,.9375,.87565048);for(int axis=0;axis<6;axis++)near(expected[axis],actual[axis],"shared transform retained call-local scratch");shared.wedgeBoxes(.25,.25,.25,.75,.75,.75);}}catch(Throwable failure){failed.compareAndSet(null,failure);}});
        for(Thread worker:workers)worker.start();for(Thread worker:workers)worker.join();if(failed.get()!=null)throw new AssertionError(failed.get());
        System.out.println("Control mount scalar transform PASS cases="+cases+" upright height, support projection, bounds/wedges, signed zero, independent results and concurrent immutable reuse");
    }
    private static void checkMount(ControlMountTransform<double[],double[]> mount,ControlMountTransform<double[],double[]> standard,int face,int height){
        double[] bounds=mount.bounds(.25,.25,.25,.75,.75,.75);List<double[]> wedges=mount.wedgeBoxes(.25,.25,.25,.75,.75,.75);require(!wedges.isEmpty(),"empty support strips");
        for(int corner=0;corner<8;corner++){
            double x=(corner&4)==0?.25:.75,y=(corner&2)==0?.25:.75,z=(corner&1)==0?.25:.75;double[] point=mount.transform(x,y,z),unraised=standard.transform(x,y,z),projected=mount.project(point[0],point[1],point[2]);near(0,mount.depth(projected[0],projected[1],projected[2]),"support projection depth");
            for(int axis=0;axis<3;axis++){near(n(face,axis)*height/8D,point[axis]-unraised[axis],"height tilted sideways");require(point[axis]>=bounds[axis]-1e-12&&point[axis]<=bounds[axis+3]+1e-12,"selection excludes transformed point");}
        }
    }
}
