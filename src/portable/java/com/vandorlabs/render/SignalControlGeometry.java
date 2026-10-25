package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.vandorlabs.render.ControlMount.Box;
import com.vandorlabs.render.ControlMount.Point;

/** Authored detent selection bounds, support and collision, shared by both adapters. */
public final class SignalControlGeometry {
    private static final java.util.Map<String,Box> bounds=new java.util.HashMap<>();
    static {
        bounds.put("32/thruster_lever/north",new Box(0.18750000,0.11330098,0.30394670,0.81250000,0.87565048,1.00000000));
        bounds.put("32/thruster_lever/south",new Box(0.18750000,0.11330098,0.00000000,0.81250000,0.87565048,0.69605330));
        bounds.put("32/thruster_lever/east",new Box(0.00000000,0.11330098,0.18750000,0.69605330,0.87565048,0.81250000));
        bounds.put("32/thruster_lever/west",new Box(0.30394670,0.11330098,0.18750000,1.00000000,0.87565048,0.81250000));
        bounds.put("32/thruster_lever/floor_x",new Box(0.12434952,0.00000000,0.18750000,0.88669902,0.69605330,0.81250000));
        bounds.put("32/thruster_lever/floor_z",new Box(0.18750000,0.00000000,0.11330098,0.81250000,0.69605330,0.87565048));
        bounds.put("32/thruster_lever/ceiling_x",new Box(0.11330098,0.30394670,0.18750000,0.87565048,1.00000000,0.81250000));
        bounds.put("32/thruster_lever/ceiling_z",new Box(0.18750000,0.30394670,0.12434952,0.81250000,1.00000000,0.88669902));
        bounds.put("32/wall_slider/north",new Box(0.18750000,0.06250000,0.85937500,0.81250000,0.93750000,1.00000000));
        bounds.put("32/wall_slider/south",new Box(0.18750000,0.06250000,0.00000000,0.81250000,0.93750000,0.14062500));
        bounds.put("32/wall_slider/east",new Box(0.00000000,0.06250000,0.18750000,0.14062500,0.93750000,0.81250000));
        bounds.put("32/wall_slider/west",new Box(0.85937500,0.06250000,0.18750000,1.00000000,0.93750000,0.81250000));
        bounds.put("32/wall_slider/floor_x",new Box(0.06250000,0.00000000,0.18750000,0.93750000,0.14062500,0.81250000));
        bounds.put("32/wall_slider/floor_z",new Box(0.18750000,0.00000000,0.06250000,0.81250000,0.14062500,0.93750000));
        bounds.put("32/wall_slider/ceiling_x",new Box(0.06250000,0.85937500,0.18750000,0.93750000,1.00000000,0.81250000));
        bounds.put("32/wall_slider/ceiling_z",new Box(0.18750000,0.85937500,0.06250000,0.81250000,1.00000000,0.93750000));
        bounds.put("32/airliner_throttle/north",new Box(0.06250000,0.06250000,0.25000000,0.93750000,0.93750000,1.00000000));
        bounds.put("32/airliner_throttle/south",new Box(0.06250000,0.06250000,0.00000000,0.93750000,0.93750000,0.75000000));
        bounds.put("32/airliner_throttle/east",new Box(0.00000000,0.06250000,0.06250000,0.75000000,0.93750000,0.93750000));
        bounds.put("32/airliner_throttle/west",new Box(0.25000000,0.06250000,0.06250000,1.00000000,0.93750000,0.93750000));
        bounds.put("32/airliner_throttle/floor_z",new Box(0.06250000,0.00000000,0.06250000,0.93750000,0.75000000,0.93750000));
        bounds.put("32/airliner_throttle/floor_x",new Box(0.06250000,0.00000000,0.06250000,0.93750000,0.75000000,0.93750000));
        bounds.put("32/airliner_throttle/ceiling_z",new Box(0.06250000,0.25000000,0.06250000,0.93750000,1.00000000,0.93750000));
        bounds.put("32/airliner_throttle/ceiling_x",new Box(0.06250000,0.25000000,0.06250000,0.93750000,1.00000000,0.93750000));
        bounds.put("32/fighter_throttle/north",new Box(0.09375000,0.06250000,0.31250000,0.90625000,0.93750000,1.00000000));
        bounds.put("32/fighter_throttle/south",new Box(0.09375000,0.06250000,0.00000000,0.90625000,0.93750000,0.68750000));
        bounds.put("32/fighter_throttle/east",new Box(0.00000000,0.06250000,0.09375000,0.68750000,0.93750000,0.90625000));
        bounds.put("32/fighter_throttle/west",new Box(0.31250000,0.06250000,0.09375000,1.00000000,0.93750000,0.90625000));
        bounds.put("32/fighter_throttle/floor_z",new Box(0.09375000,0.00000000,0.06250000,0.90625000,0.68750000,0.93750000));
        bounds.put("32/fighter_throttle/floor_x",new Box(0.06250000,0.00000000,0.09375000,0.93750000,0.68750000,0.90625000));
        bounds.put("32/fighter_throttle/ceiling_z",new Box(0.09375000,0.31250000,0.06250000,0.90625000,1.00000000,0.93750000));
        bounds.put("32/fighter_throttle/ceiling_x",new Box(0.06250000,0.31250000,0.09375000,0.93750000,1.00000000,0.90625000));
    }
    public static Box rawBounds(int detail,String kind,String mount){Box box=bounds.get(detail+"/"+kind+"/"+mount);return box==null?new Box(0,0,0,1,1,1):box;}
    private static String mount(String kind,int face,int rotation){return face<2?(face==1?"floor":"ceiling")+(("wall_slider".equals(kind)||rotation%2==0)?"_z":"_x"):new String[]{"down","up","north","south","west","east"}[face];}
    public static String modelPath(String kind,int face,int rotation,int step){return "/assets/vandorlabs/models/block/"+(("thruster_lever".equals(kind)||"wall_slider".equals(kind))?"thruster_controls":"aircraft_throttles")+"/32px/"+kind+"_"+new String[]{"off","low","medium","high"}[step]+"_"+mount(kind,face,rotation)+".json";}
    public static Box originalBounds(String kind,int face,int rotation){return originalBounds(32,kind,face,rotation);}
    public static Box originalBounds(int detail,String kind,int face,int rotation){
        // The original slider selection uses rotation parity, unlike its collision model choice.
        String name=face<2?(face==1?"floor":"ceiling")+(rotation%2==0?"_z":"_x"):new String[]{"down","up","north","south","west","east"}[face];
        Box box=rawBounds(detail,kind,name);
        if("wall_slider".equals(kind)){Point origin=new Point(.5,.5,.5).subtract(ControlMount.normal(face).scale(.5));box=new Box(origin.x+(box.minX-origin.x)*.92,origin.y+(box.minY-origin.y)*.92,origin.z+(box.minZ-origin.z)*.92,origin.x+(box.maxX-origin.x)*.92,origin.y+(box.maxY-origin.y)*.92,origin.z+(box.maxZ-origin.z)*.92);}
        return face<2&&rotation>=2?new Box(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ):box;
    }
    public static Box supportBounds(String kind,int face,int rotation){
        double a="thruster_lever".equals(kind)||"wall_slider".equals(kind)?3/16D:"fighter_throttle".equals(kind)?1.5/16D:1/16D;
        double b="thruster_lever".equals(kind)?2/16D:1/16D;if("wall_slider".equals(kind)){a=.5-(.5-a)*.92;b=.5-(.5-b)*.92;}
        if(face<2){if(rotation%2==1){double swap=a;a=b;b=swap;}double y=face==1?0:1;return new Box(a,y,b,1-a,y,1-b);}
        if(face==2||face==3){double z=face==3?0:1;return new Box(a,b,z,1-a,1-b,z);}
        double x=face==5?0:1;return new Box(x,b,a,x,1-b,1-a);
    }
    public static List<Box> collision(String kind,int face,int rotation,int height,int tilt,int direction,List<ControlPose.Element> elements){
        Box support=supportBounds(kind,face,rotation);ControlMount transform=new ControlMount(face,rotation,height,tilt,direction,support);List<Box> result=new ArrayList<Box>();
        for(ControlPose.Element element:elements){List<Point> points=new ArrayList<Point>();for(Point corner:ControlMount.corners(element.bounds)){
            Point p=element.origin==null?corner:ControlMount.rotate(corner,element.origin,element.axis,element.degrees);
            if("wall_slider".equals(kind)&&face<2)p=sliderYaw(p,rotation,face);
            else if(face<2&&rotation>=2)p=new Point(1-p.x,p.y,1-p.z);
            points.add(transform.transform(p));
        }result.add(ControlMount.hull(points));}
        if(height>0||tilt>0)result.addAll(transform.wedgeBoxes(support));return Collections.unmodifiableList(result);
    }
    private static Point sliderYaw(Point point,int rotation,int face){
        // Match Vec3d.rotateYaw's float angle and 65536-entry MathHelper lookup exactly.
        float angle=(float)(-((rotation+(face==1?2:0))&3)*Math.PI/2);
        int sinIndex=(int)(angle*10430.378F)&65535,cosIndex=(int)(angle*10430.378F+16384F)&65535;
        float sin=(float)Math.sin((double)sinIndex*Math.PI*2/65536),cos=(float)Math.sin((double)cosIndex*Math.PI*2/65536);
        double x=point.x-.5,z=point.z-.5;return new Point(x*cos+z*sin+.5,point.y,z*cos-x*sin+.5);
    }
    private SignalControlGeometry(){}
}
