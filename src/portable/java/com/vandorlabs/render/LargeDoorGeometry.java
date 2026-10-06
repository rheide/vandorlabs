package com.vandorlabs.render;

import java.util.*;
import com.vandorlabs.persistence.SpaceDoorData;

/** Collision uses the same scaled pivots and motion as the two rendered leaves. */
public final class LargeDoorGeometry {
    private LargeDoorGeometry(){}
    public static final class Box {
        public final double x0,y0,z0,x1,y1,z1;
        Box(double x0,double y0,double z0,double x1,double y1,double z1){this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;}
    }
    public static List<Box> boxes(boolean frame,boolean sliding,int direction,boolean open,double depth,int facing){
        return boxes(frame,sliding,direction,open,depth,facing,false,false);
    }
    public static List<Box> boxes(boolean frame,boolean sliding,int direction,boolean open,double depth,int facing,boolean panel,boolean farEdge){
        List<Box> result=new ArrayList<>();double jamb=1.5/16,z0=sliding?6/16.0:11.24/16,z1=z0+4/16.0;
        if(frame){add(result,0,0,z0,jamb,3,z1,depth,facing,0,0,0,0,0);add(result,3-jamb,0,z0,3,3,z1,depth,facing,0,0,0,0,0);add(result,jamb,0,z0,3-jamb,jamb,z1,depth,facing,0,0,0,0,0);add(result,jamb,3-jamb,z0,3-jamb,3,z1,depth,facing,0,0,0,0,0);}
        double low=frame?jamb:0,high=frame?3-jamb:3;
        if(sliding && direction==3)addXPanels(result,low,high,open,depth,facing,frame);
        else for(int hand=0;hand<2;hand++){
            boolean right=hand==1;double x0=right?1.5:(frame?jamb:0),x1=right?(frame?3-jamb:3):1.5;
            double leafZ=sliding?7/16.0:12.24/16;
            double pivotX=right?3-jamb:jamb,pivotZ=11.24/16;
            double angle=open && !sliding?(right?-90:90):0;
            double shiftX=open && sliding && direction==0?(right?1:-1)*(frame?1.5:22.5/16):0;
            double shiftY=open && sliding && direction!=0?1.5*SpaceDoorData.verticalTravel(frame,direction):0;
            add(result,x0,low+shiftY,leafZ,x1,high+shiftY,leafZ+2/16.0,depth,facing,shiftX,pivotX,pivotZ,angle,0);
        }
        if(panel){
            double x0=sliding?3-1.5/16:0,x1=sliding?3:1.5/16;
            double yOffset=SpaceDoorControlPanel.verticalOffset(1.5);
            add(result,x0,SpaceDoorControlPanel.Y0*1.5/16+yOffset,SpaceDoorControlPanel.z0(sliding,farEdge)/16,x1,SpaceDoorControlPanel.Y1*1.5/16+yOffset,SpaceDoorControlPanel.z1(sliding,farEdge)/16,depth,facing,0,0,0,0,0);
        }
        return result;
    }
    private static void addXPanels(List<Box> out,double low,double high,boolean open,double depth,int facing,boolean frame) {
        if(!open){add(out,low,low,7/16.0,high,high,9/16.0,depth,facing,0,0,0,0,0);return;}
        // Narrow strips conservatively cover the diagonal faces for vanilla AABB collision.
        // Bare panels retain one pixel at their tips; the center stays clear.
        for(int panel=0;panel<4;panel++)for(int strip=0;strip<64;strip++) {
            double a=low+(high-low)*strip/64,b=low+(high-low)*(strip+1)/64;
            List<float[]> rectangle=new ArrayList<>();
            if(panel<2) {
                rectangle.add(new float[]{(float)(low/1.5),(float)(a/1.5)});rectangle.add(new float[]{(float)(high/1.5),(float)(a/1.5)});
                rectangle.add(new float[]{(float)(high/1.5),(float)(b/1.5)});rectangle.add(new float[]{(float)(low/1.5),(float)(b/1.5)});
            }else {
                rectangle.add(new float[]{(float)(a/1.5),(float)(low/1.5)});rectangle.add(new float[]{(float)(b/1.5),(float)(low/1.5)});
                rectangle.add(new float[]{(float)(b/1.5),(float)(high/1.5)});rectangle.add(new float[]{(float)(a/1.5),(float)(high/1.5)});
            }
            List<float[]> clipped=XDoorPanel.clip(rectangle,0,panel);if(clipped.size()<3)continue;
            double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
            for(float[] v:clipped){x0=Math.min(x0,v[0]*1.5);x1=Math.max(x1,v[0]*1.5);y0=Math.min(y0,v[1]*1.5);y1=Math.max(y1,v[1]*1.5);}
            if(x1-x0<1e-7 || y1-y0<1e-7)continue;
            double travel=XDoorPanel.travel(1,frame,1.5);
            double sx=1.5*XDoorPanel.shiftX(panel,travel),sy=1.5*XDoorPanel.shiftY(panel,travel);
            add(out,x0,y0+sy,7/16.0,x1,y1+sy,9/16.0,depth,facing,sx,0,0,0,0);
        }
    }
    private static void add(List<Box> out,double x0,double y0,double z0,double x1,double y1,double z1,double depth,int facing,double shift,double px,double pz,double angle,double unused){
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));
        for(double x:new double[]{x0,x1})for(double z:new double[]{z0,z1}){
            double dx=x-px,dz=z-pz,a=c*dx+s*dz+px+shift,b=-s*dx+c*dz+pz+depth;
            double wx=facing==2?1-a:facing==3?b:facing==1?1-b:a;
            double wz=facing==2?1-b:facing==3?1-a:facing==1?a:b;
            minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
        }
        out.add(new Box(minX,y0,minZ,maxX,y1,maxZ));
    }
}
