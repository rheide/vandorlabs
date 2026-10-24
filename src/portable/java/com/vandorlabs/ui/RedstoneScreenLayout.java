package com.vandorlabs.ui;

import com.vandorlabs.render.ScreenSurface;

/** Authored 128-pixel overlay and front-only ray mapping shared by both adapters. */
public final class RedstoneScreenLayout {
    public static final int WIDTH=128,ROW_TOP=24,ROW_HEIGHT=12,ROW_LEFT=6,ROW_RIGHT=122,BUTTON_LEFT=98,BUTTON_RIGHT=120;
    public final boolean header;
    public final int height,rowTop,maxRows;
    private static final RedstoneScreenLayout FULL=new RedstoneScreenLayout(false),HALF=new RedstoneScreenLayout(true);
    public static RedstoneScreenLayout of(boolean half){return half?HALF:FULL;}
    public RedstoneScreenLayout(boolean half){header=!half;height=half?64:128;rowTop=half?8:ROW_TOP;maxRows=half?4:8;}
    /** Facing indices retain the vanilla saved order: down, up, north, south, west, east. */
    public static double[] local(double x,double y,double z,int facing){
        x-=.5;y-=.5;z-=.5;double lx=x,ly=y,lz=z;
        switch(facing){case 5:lx=z;lz=-x;break;case 3:lx=-x;lz=-z;break;case 4:lx=-z;lz=x;break;case 1:lx=-x;ly=-z;lz=-y;break;case 0:ly=-z;lz=y;break;default:break;}
        return new double[]{(lx+.5)*16,(ly+.5)*16,(lz+.5)*16};
    }
    public static final class Hit {
        public final int row;
        public final double column,pixelY;
        private Hit(int row,double column,double pixelY){this.row=row;this.column=column;this.pixelY=pixelY;}
    }
    public Hit hit(ScreenSurface.Quad surface,int facing,double[] start,double[] end,int count){
        double[] a=local(start[0],start[1],start[2],facing),b=local(end[0],end[1],end[2],facing);
        double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        double denom=surface.ny*dy+surface.nz*dz;if(denom>=-1e-8)return new Hit(-1,-1,-1);
        double t=(surface.ny*(surface.topRight.y-a[1])+surface.nz*(surface.topRight.z-a[2]))/denom;
        if(t<0||t>1)return new Hit(-1,-1,-1);
        double x=a[0]+dx*t,y=a[1]+dy*t,z=a[2]+dz*t;
        double u=(surface.topRight.x-x)/(surface.topRight.x-surface.topLeft.x)*WIDTH;
        double qy=surface.bottomRight.y-surface.topRight.y,qz=surface.bottomRight.z-surface.topRight.z;
        double v=((y-surface.topRight.y)*qy+(z-surface.topRight.z)*qz)/(qy*qy+qz*qz)*height;
        int row=(int)Math.floor((v-rowTop)/ROW_HEIGHT);
        boolean within=u>=ROW_LEFT&&u<=ROW_RIGHT&&row>=0&&row<count&&v-rowTop-row*ROW_HEIGHT<10;
        return new Hit(within?row:-1,u,v);
    }
    public static int sliderSegment(double column,int segments){return column<60||column>=120?-1:(int)((column-60)*segments/60);}
}
