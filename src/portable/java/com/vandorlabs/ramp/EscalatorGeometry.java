package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** A periodic train of treads, clipped to a fixed rectangular lane. */
public final class EscalatorGeometry {
    private EscalatorGeometry() { }
    public static double rate(int speed) { return speed==0?.05:speed==2?.0125:.025; }
    public static double phase(double distance,int pixels) {
        double pitch=pixels/16D;
        return distance-Math.floor(distance/pitch)*pitch;
    }
    public static double height(double along,int length,int pixels,double rise) {
        return height(along,length,pixels,rise,0);
    }
    public static double height(double along,int length,int pixels,double rise,int interpolation) {
        double last=length-pixels/16D;
        return rise*ControllerPlatform.profile(along/last,interpolation);
    }
    public static List<RampGeometry.Box> treads(RampGeometry.Direction face,
            int x,int y,int z,double low,double high,int row,int length,int pixels,
            double rise,double distance) {
        return treads(face,x,y,z,low,high,row,length,pixels,rise,distance,0);
    }
    public static List<RampGeometry.Box> treads(RampGeometry.Direction face,
            int x,int y,int z,double low,double high,int row,int length,int pixels,
            double rise,double distance,int interpolation) {
        List<RampGeometry.Box> result=new ArrayList<>();
        double pitch=pixels/16D,shift=phase(distance,length*16);
        int count=ControllerPlatform.treadCount(pixels);
        for(int step=0;step<count;step++) {
            double anchor=phase(row+step*pitch+shift,length*16);
            for(int copy=0;copy<2;copy++) {
                double at=anchor-copy*length;
                double a=Math.max(0,at),b=Math.min(length,at+pitch);
                if(b-a<1e-8)continue;
                double offset=height(at,length,pixels,rise,interpolation);
                double relativeA=a-row,relativeB=b-row;
                boolean reverse=face==RampGeometry.Direction.NORTH || face==RampGeometry.Direction.WEST;
                double lo=reverse?1-relativeB:relativeA,hi=reverse?1-relativeA:relativeB;
                boolean axisX=face==RampGeometry.Direction.EAST || face==RampGeometry.Direction.WEST;
                result.add(new RampGeometry.Box(x+(axisX?lo:0),y+low+offset,z+(axisX?0:lo),
                        x+(axisX?hi:1),y+high+offset,z+(axisX?1:hi)));
            }
        }
        return result;
    }
}
