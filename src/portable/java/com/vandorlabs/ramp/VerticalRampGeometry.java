package com.vandorlabs.ramp;

import java.util.ArrayList;
import java.util.List;

/** Y-profile counterpart of the horizontal ramp. Source cuboids stay in their native orientation. */
public final class VerticalRampGeometry {
    public static final class Piece {
        public final RampGeometry.Box box;
        public final double dx,dy,dz;
        public final int step;
        Piece(RampGeometry.Box box,double dx,double dy,double dz,int step){this.box=box;this.dx=dx;this.dy=dy;this.dz=dz;this.step=step;}
    }
    public static int dx(RampGeometry.Direction face,int travel){
        if(travel==RampGeometry.VERTICAL)return face==RampGeometry.Direction.EAST?1:face==RampGeometry.Direction.WEST?-1:0;
        int left=face==RampGeometry.Direction.NORTH?-1:face==RampGeometry.Direction.SOUTH?1:0;
        return travel==RampGeometry.LEFT?left:-left;
    }
    public static int dz(RampGeometry.Direction face,int travel){
        if(travel==RampGeometry.VERTICAL)return face==RampGeometry.Direction.SOUTH?1:face==RampGeometry.Direction.NORTH?-1:0;
        int left=face==RampGeometry.Direction.EAST?-1:face==RampGeometry.Direction.WEST?1:0;
        return travel==RampGeometry.LEFT?left:-left;
    }
    public static List<Piece> pieces(RampGeometry.Direction face,int x,int y,int z,RampGeometry.Box nativeBox,
            int baseY,int length,int pixels,double start,double end,double pose,boolean lift,int travel,
            boolean fill,boolean fast,int interpolation,boolean belt,double distance){
        List<Piece> result=new ArrayList<>();
        for(int step=0;step<(lift?1:ControllerPlatform.treadCount(pixels));step++){
            double a=Math.max(nativeBox.minY,lift?0:ControllerPlatform.treadStart(step,pixels));
            double b=Math.min(nativeBox.maxY,lift?1:ControllerPlatform.treadEnd(step,pixels));
            if(b-a<1e-8)continue;
            double offset=ControllerPlatform.offsetPixels(y-baseY,step,length,pixels,start,end,pose,lift,fast,interpolation);
            if(belt){
                double pitch=pixels/16D;
                double segment=EscalatorGeometry.phase(y-baseY+step*pitch+distance,length*16);
                offset=EscalatorGeometry.height(segment,length,pixels,end,interpolation);
                double shift=segment-(y-baseY+step*pitch);
                // Split a tread at the belt seam instead of drawing outside its lane.
                add(result,face,x,y,z,nativeBox,a,Math.min(b,baseY+length-y-shift),travel,offset,offset,shift,step);
                if(y+b+shift>baseY+length)add(result,face,x,y,z,nativeBox,Math.max(a,baseY+length-y-shift),b,travel,offset,offset,shift-length,step);
            }else{
                double initial=ControllerPlatform.offsetPixels(y-baseY,step,length,pixels,start,end,0,lift,fast,interpolation);
                add(result,face,x,y,z,nativeBox,a,b,travel,fill?Math.min(initial,offset):offset,fill?Math.max(initial,offset):offset,0,step);
            }
        }
        return result;
    }
    private static void add(List<Piece> result,RampGeometry.Direction face,int x,int y,int z,RampGeometry.Box n,double a,double b,int travel,double lo,double hi,double shift,int step){
        if(b-a<1e-8)return;
        int dx=dx(face,travel),dz=dz(face,travel);
        RampGeometry.Box box=new RampGeometry.Box(x+n.minX+(dx>0?lo:dx<0?-hi:0),y+a+shift,z+n.minZ+(dz>0?lo:dz<0?-hi:0),
                x+n.maxX+(dx>0?hi:dx<0?-lo:0),y+b+shift,z+n.maxZ+(dz>0?hi:dz<0?-lo:0));
        result.add(new Piece(box,dx*lo,shift,dz*lo,step));
    }
    public static RampGeometry.Box sweep(RampGeometry.Direction face,int x,int y,int z,double low,double high,
            int baseY,int length,int pixels,int step,double start,double end,double from,double to,boolean lift,int travel,boolean fill,boolean fast,int interpolation,boolean belt){
        double a=ControllerPlatform.offsetPixels(y-baseY,step,length,pixels,start,end,from,lift,fast,interpolation);
        double b=ControllerPlatform.offsetPixels(y-baseY,step,length,pixels,start,end,to,lift,fast,interpolation);
        double lo=Math.min(a,b),hi=Math.max(a,b);
        if(fill){double initial=ControllerPlatform.offsetPixels(y-baseY,step,length,pixels,start,end,0,lift,fast,interpolation);lo=Math.min(lo,initial);hi=Math.max(hi,initial);}
        double y0=y+Math.max(low,lift?0:ControllerPlatform.treadStart(step,pixels)),y1=y+Math.min(high,lift?1:ControllerPlatform.treadEnd(step,pixels));
        if(belt){lo=Math.min(0,end);hi=Math.max(0,end);y0=baseY;y1=baseY+length;}
        if(y1-y0<1e-8)return new RampGeometry.Box(x,y,z,x,y,z);
        int dx=dx(face,travel),dz=dz(face,travel);
        return new RampGeometry.Box(x+(dx>0?lo:dx<0?-hi:0),y0,z+(dz>0?lo:dz<0?-hi:0),x+1+(dx>0?hi:dx<0?-lo:0),Math.max(y0,y1),z+1+(dz>0?hi:dz<0?-lo:0));
    }
    private VerticalRampGeometry(){}
}
