package com.vandorlabs.propulsion;

/** Placed-engine tick submissions; the caller owns loaded membership and tick scheduling. */
public final class PropulsionEmission {
    private PropulsionEmission(){}
    public interface Sink {void particle(double x,double y,double z,int facing,int color,int style,float speed,float spread,boolean blockCenter);}
    public static int style(String id){if(id.contains("rocket"))return 0;if(id.contains("ion"))return 1;if(id.contains("plasma")||id.contains("repulsor"))return 2;if(id.contains("impulse"))return 3;return 4;}
    public static int color(int style){return style==0?0xFF8A24:style==2?0xC36AFF:style==3?0xFF4438:0x4DEBFF;}
    public static float speed(int style){return style==0?.25F:style==1?.17F:style==2?.13F:style==3?.085F:.10F;}
    public static int right(int face){return face<2?5:PropulsionPlacement.rotateFacing(face,1);}
    public static int up(int face){return face==1?3:face==0?2:1;}
    public static int dx(int face){return face==4?-1:face==5?1:0;}
    public static int dy(int face){return face==0?-1:face==1?1:0;}
    public static int dz(int face){return face==2?-1:face==3?1:0;}
    /** size/x/y are frozen partition coordinates, not a request to scan the world. */
    public static void emit(String id,int px,int py,int pz,int face,boolean powered,boolean selected,int level,int size,int x,int y,Sink sink){
        if(!powered||!selected||level<=0||size>1&&(x!=0||y!=0))return;
        int style=style(id),base=style==2?3:style==4?1:2;
        int core=size>1?base*size+size-1:base,count=core*(level==2?3:level==3?6:1);
        float wider=level==2?1.75F:2.75F,spread=size>1?Math.min(1.25F,.70F+size*.08F):1;
        double half=(size-1)*.5D,cx=px+.5D,cy=py+.5D,cz=pz+.5D;
        if(size>1){int right=right(face),up=up(face);cx+=dx(right)*(half-x)+dx(up)*(half-y);cy+=dy(right)*(half-x)+dy(up)*(half-y);cz+=dz(right)*(half-x)+dz(up)*(half-y);}
        for(int i=0;i<count;i++)sink.particle(cx,cy,cz,face,color(style),style,speed(style),i<core?spread:spread*wider,size==1&&i<core);
    }
}
