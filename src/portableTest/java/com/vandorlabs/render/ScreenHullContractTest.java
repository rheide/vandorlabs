package com.vandorlabs.render;

/** Geometry acceptance independent of either platform's ray and voxel APIs. */
public final class ScreenHullContractTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(double expected,double actual,String label){check(Math.abs(expected-actual)<1e-8,label+": "+actual+" != "+expected);}
    private static DiagonalScreenHull.Hit trace(DiagonalScreenHull hull,int facing,double sx,double sy,double sz,double ex,double ey,double ez){double[] a=DiagonalScreenHull.worldPoint(sx,sy,sz,facing),b=DiagonalScreenHull.worldPoint(ex,ey,ez,facing);return hull.trace(a[0],a[1],a[2],b[0],b[1],b[2]);}
    public static void main(String[] args){
        for(int facing=2;facing<6;facing++)for(boolean upper:new boolean[]{false,true}){
            DiagonalScreenHull hull=DiagonalScreenHull.of(facing,upper);check(hull==DiagonalScreenHull.of(facing,upper),"cached hull");check(hull.collision.length==16,"one pixel slices");check(hull.outline.length>0,"nonempty outline");
            double volume=0;for(DiagonalScreenHull.Bounds box:hull.collision){check(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"bounded slices");volume+=(box.maxX-box.minX)*(box.maxY-box.minY)*(box.maxZ-box.minZ);}near(17D/32,volume,"stair collision volume");
            DiagonalScreenHull.Hit hit=trace(hull,facing,.5,upper?-1:2,.5,.5,upper?2:-1,.5);check(hit!=null,"slope hit");double[] expected=DiagonalScreenHull.worldPoint(.5,.5,.5,facing);near(expected[0],hit.x,"slope x");near(expected[1],hit.y,"slope y");near(expected[2],hit.z,"slope z");near(1,Math.sqrt(hit.nx*hit.nx+hit.ny*hit.ny+hit.nz*hit.nz),"unit outward normal");check(upper?hit.ny<0:hit.ny>0,"outward slope y");
            check(trace(hull,facing,-1,upper?.1:.9,.2,2,upper?.1:.9,.2)==null,"empty wedge pocket passes");
            hit=trace(hull,facing,.5,upper?.875:.125,.5,.5,upper?.875:.125,-1);check(hit!=null,"inside exits actual slope");expected=DiagonalScreenHull.worldPoint(.5,upper?.875:.125,.125,facing);near(expected[0],hit.x,"inside exit x");near(expected[1],hit.y,"inside exit y");near(expected[2],hit.z,"inside exit z");
        }
        DiagonalScreenHull.Bounds[] box={new DiagonalScreenHull.Bounds(0,0,0,1,1,1)};
        int[] faces={4,5,0,1,2,3};
        for(int plane=0;plane<6;plane++){int axis=plane/2;double sign=(plane&1)==0?-1:1;double[] a={.5,.5,.5},b={.5,.5,.5};a[axis]=sign<0?-1:2;b[axis]=.5;BoxRayTrace.Hit hit=BoxRayTrace.trace(box,a[0],a[1],a[2],b[0],b[1],b[2]);check(hit!=null&&hit.facing==faces[plane],"outside entry face "+plane);hit=BoxRayTrace.trace(box,b[0],b[1],b[2],a[0],a[1],a[2]);check(hit!=null&&hit.facing==faces[plane],"inside exit face "+plane);}
        BoxRayTrace.Hit tie=BoxRayTrace.trace(box,-1,-1,.5,.5,.5,.5);check(tie!=null&&tie.facing==4,"legacy tie uses west before down");check(BoxRayTrace.trace(box,-1,2,.5,2,2,.5)==null,"parallel outside misses");check(BoxRayTrace.trace(box,.5,.5,.5,.5,.5,.5)==null,"zero ray misses");
        System.out.println("ScreenHullContractTest PASS "+assertions+" assertions");
    }
}
