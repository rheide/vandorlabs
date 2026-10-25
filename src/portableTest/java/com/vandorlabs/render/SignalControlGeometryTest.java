package com.vandorlabs.render;

import java.util.Arrays;
import java.util.List;
import com.vandorlabs.render.ControlMount.Box;
import com.vandorlabs.render.ControlMount.Point;

/** Authored reference bounds and placement-specific collision contracts. */
public final class SignalControlGeometryTest {
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void close(double a,double b){need(Math.abs(a-b)<1e-12,a+" != "+b);}
    public static void main(String[] args){
        Box thruster=SignalControlGeometry.originalBounds("thruster_lever",2,0);
        close(thruster.minX,3/16D);close(thruster.maxZ,1);close(thruster.minY,.11330098);
        Box slider=SignalControlGeometry.originalBounds("wall_slider",2,0);
        close(slider.minX,.2125);close(slider.maxY,.9025);close(slider.maxZ,1);
        Box fallback=SignalControlGeometry.rawBounds(64,"thruster_lever","north");close(fallback.minX,0);close(fallback.maxY,1);
        need(SignalControlGeometry.modelPath("wall_slider",1,1,3).endsWith("wall_slider_high_floor_z.json"),"slider collision retains native floor_z then rotates");
        need(SignalControlGeometry.modelPath("fighter_throttle",0,1,2).endsWith("fighter_throttle_medium_ceiling_x.json"),"throttle ceiling parity");
        int poses=0;
        for(String kind:new String[]{"thruster_lever","airliner_throttle","fighter_throttle","wall_slider"})for(int face=0;face<6;face++)for(int rotation=0;rotation<4;rotation++){
            Box support=SignalControlGeometry.supportBounds(kind,face,rotation);Point normal=ControlMount.normal(face),origin=new Point(.5,.5,.5).subtract(normal.scale(.5));
            for(Point p:ControlMount.corners(support))close(p.subtract(origin).dot(normal),0);
            Box bounds=SignalControlGeometry.originalBounds(kind,face,rotation);need(bounds.maxX>bounds.minX&&bounds.maxY>bounds.minY&&bounds.maxZ>bounds.minZ,"nonempty authored union");poses++;
        }
        List<ControlPose.Element> elements=Arrays.asList(new ControlPose.Element(new Box(.25,0,.25,.75,.125,.75),null,0,0));
        Box plain=SignalControlGeometry.collision("thruster_lever",1,0,0,0,0,elements).get(0);close(plain.minY,0);close(plain.maxY,.125);close(plain.minZ,.25);
        Box opposite=SignalControlGeometry.collision("thruster_lever",1,2,0,0,0,Arrays.asList(new ControlPose.Element(new Box(.1,0,.2,.3,.125,.4),null,0,0))).get(0);close(opposite.minX,.7);close(opposite.maxZ,.8);
        int mounts=0;
        for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
            List<Box> boxes=SignalControlGeometry.collision("thruster_lever",1,0,height,tilt,direction,elements);
            need(boxes.size()==1+(height>0||tilt>0?(direction%2==0?12:10):0),"solid support wedge strip count");
            for(Box box:boxes)need(Double.isFinite(box.minX)&&Double.isFinite(box.maxY)&&box.minX<=box.maxX&&box.minY<=box.maxY&&box.minZ<=box.maxZ,"finite ordered collision");mounts++;
        }
        System.out.println("SignalControlGeometry PASS "+poses+" authored support/selection poses and "+mounts+" mounted collision configurations");
    }
}
