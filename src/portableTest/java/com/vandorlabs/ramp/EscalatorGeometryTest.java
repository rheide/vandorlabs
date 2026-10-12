package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

public final class EscalatorGeometryTest {
    private EscalatorGeometryTest() { }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void run() {
        int checks=0;
        for(RampGeometry.Direction face:RampGeometry.Direction.values())for(int length:new int[]{2,4,16})
        for(int pixels:new int[]{1,2,4,8,16})for(double rise:new double[]{-16,-2.5,0,.5,16})
        for(int interpolation=0;interpolation<3;interpolation++)
        for(double distance:new double[]{-32.01,-.51,-.001,0,.001,.49,.999,3.9,16.01}) {
            List<RampGeometry.Box> boxes=new ArrayList<>();
            int dx=face==RampGeometry.Direction.EAST?1:face==RampGeometry.Direction.WEST?-1:0;
            int dz=face==RampGeometry.Direction.SOUTH?1:face==RampGeometry.Direction.NORTH?-1:0;
            for(int row=0;row<length;row++)boxes.addAll(EscalatorGeometry.treads(face,dx*row,30,dz*row,0,.5,row,length,pixels,rise,distance,interpolation));
            double volume=0;
            for(RampGeometry.Box b:boxes) {
                require(b.maxX>b.minX && b.maxY>b.minY && b.maxZ>b.minZ,"degenerate tread");
                volume+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);
                require(b.minY>=30+Math.min(0,rise)-1e-7 && b.maxY<=30.5+Math.max(0,rise)+1e-7,"height escaped fixed envelope");
            }
            require(Math.abs(volume-length*.5)<1e-7,"gap or duplicate at entrance/wrap");
            // Sample the entire lane to ensure exactly one surface, even at loop boundaries.
            for(int sample=0;sample<length*16;sample++) {
                double along=(sample+.37)/16D;
                double x=dx>0?along:dx<0?1-along:.5,z=dz>0?along:dz<0?1-along:.5;
                int count=0;for(RampGeometry.Box b:boxes)if(x>b.minX && x<b.maxX && z>b.minZ && z<b.maxZ)count++;
                require(count==1,"lane has a hole or overlapping treads");
            }
            checks++;
        }
        System.out.println("PASS: escalator geometry "+checks+" direction/length/tread/height/phase combinations, continuous coverage and fixed bounds");
    }
}
