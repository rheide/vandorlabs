package com.vandorlabs.ramp;

import java.util.HashSet;
import java.util.Set;

public final class ControllerPlatformTest {
    private static int assertions;
    private static void check(boolean value,String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static void close(double a,double b,String message) { check(Math.abs(a-b)<1e-8,message); }
    private static Set<ControllerPlatform.Cell> cells(int width,int length) {
        Set<ControllerPlatform.Cell> result=new HashSet<>();
        for (int x=0;x<width;x++) for (int z=0;z<length;z++) result.add(new ControllerPlatform.Cell(x,0,z));
        return result;
    }
    public static void main(String[] args) {
        ControllerPlatform.Cell seed=new ControllerPlatform.Cell(0,0,0);
        Set<ControllerPlatform.Cell> platform=cells(4,3);
        platform.add(seed.offset(0,1,0)); platform.add(seed.offset(8,0,8));
        check(ControllerPlatform.discover(seed,platform::contains).size()==12,"plane and face adjacency");
        check(ControllerPlatform.discover(seed,cells(8,8)::contains).size()==64,"exact eight-by-eight footprint");
        check(ControllerPlatform.discover(seed,cells(17,8)::contains).equals(cells(8,8)),"oversized matching floor clipped without rejection");
        check(ControllerPlatform.discover(seed,cells(1,40)::contains).equals(cells(1,8)),"length capped independently of area");
        check(ControllerPlatform.discover(seed,cells(40,1)::contains).equals(cells(8,1)),"width capped independently of area");
        int[] reads={0};
        Set<ControllerPlatform.Cell> infinite=ControllerPlatform.discover(seed,c->{reads[0]++;return true;});
        check(infinite.size()==64 && reads[0]==64,"unbounded floor stops querying beyond footprint");
        check(infinite.equals(ControllerPlatform.discover(seed,c->true)),"centered seed clipping deterministic");
        check(infinite.stream().mapToInt(c->c.x).max().getAsInt()-infinite.stream().mapToInt(c->c.x).min().getAsInt()==7,"centered width at most eight");
        check(infinite.stream().mapToInt(c->c.z).max().getAsInt()-infinite.stream().mapToInt(c->c.z).min().getAsInt()==7,"centered length at most eight");
        platform.remove(seed.offset(1,0,0));
        // A painted point must keep its UV as the segment crosses cell boundaries,
        // for whole blocks, both slab halves, both travel signs and every tread.
        for (boolean top:new boolean[]{true,false}) for (boolean lift:new boolean[]{true,false})
        for (int segments:new int[]{2,8}) for (int step=0;step<segments;step++)
        for (double sourceY:new double[]{0,.125,.5,.75,1}) for (int tick=0;tick<=80;tick++) {
            double offset=ControllerPlatform.offset(2,step,3,segments,8,tick/80.0,top,lift);
            double worldY=40+sourceY+offset;
            int cellY=(int)Math.floor(worldY);
            close(ControllerPlatform.sideTextureV(cellY,worldY-cellY,40,offset),1-sourceY,"side texture moves with painted point");
            // Both representations of a world-cell boundary must sample the same UV.
            close(ControllerPlatform.sideTextureV(cellY,0,40,offset),
                    ControllerPlatform.sideTextureV(cellY-1,1,40,offset),"no UV seam between clipped cells");
        }
        check(!ControllerPlatform.discover(seed,platform::contains).contains(seed.offset(1,0,0)),"holes preserved");
        for (int length=1;length<=128;length++) for (int height=1;height<=16;height++)
        for (int segments:new int[]{2,8}) for (boolean top:new boolean[]{true,false})
        for (boolean lift:new boolean[]{true,false}) {
            double sign=top?-1:1;
            close(ControllerPlatform.offset(0,0,length,segments,height,0,top,lift),0,"closed offset");
            close(ControllerPlatform.offset(length-1,segments-1,length,segments,height,1,top,lift),sign*height,"exact far endpoint");
            close(ControllerPlatform.offset(0,0,length,segments,height,1,top,lift),lift?sign*height:0,"elevator translates hinge too");
            double last=0;
            for (int i=0;i<=20;i++) {
                double p=i/20.0;
                double value=ControllerPlatform.offset(length-1,segments-1,length,segments,height,p,top,lift);
                check(Math.abs(value)>=Math.abs(last)-1e-8,"monotone travel"); last=value;
                close(value,-ControllerPlatform.offset(length-1,segments-1,length,segments,height,p,!top,lift),"mirrored top/bottom");
            }
            int duration=ControllerPlatform.duration(length,height,false);
            check(ControllerPlatform.duration(length,height,true)==2*duration,"slow is half base speed");
            check(duration==10*Math.max(length,height),"fast is twice the old fast speed");
            check(ControllerPlatform.duration(length,height,true)==20*Math.max(length,height),"slow matches old fast speed");
            close(ControllerPlatform.pose(0,true,duration/2.0,duration),.5,"midpoint");
            close(ControllerPlatform.pose(.5,false,0,duration),.5,"reversal continuity");
            close(ControllerPlatform.pose(.5,false,duration/4.0,duration),.25,"reversal speed");
            close(ControllerPlatform.pose(.5,false,duration,duration),0,"closed clamp");
        }
        System.out.println("Controller geometry PASS ("+assertions+" assertions)");
    }
}
