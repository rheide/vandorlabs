package com.vandorlabs.render;

import java.util.Arrays;

/** Shared leaf-plane cuts and full-aperture travel; no world or rendering dependencies. */
public final class PanelMotion {
    public final double[] u,v;
    public final double lowU,lowV,width,height;
    public final int mode;
    public PanelMotion(double[] u,double[] v,double lowU,double lowV,double width,double height,int mode) {
        this.u=u.clone();this.v=v.clone();this.lowU=lowU;this.lowV=lowV;
        this.width=width;this.height=height;this.mode=mode;
    }
    public static boolean valid(int mode){return mode==0 || mode==3 || mode>=4 && mode<=7;}
    public int count(){return mode==3?4:mode>=6?2:1;}
    public int planes(){return mode==3?2:mode>=6?1:0;}
    /** Ax+By+Cz+D >= 0 describes one retained half-space. */
    public double[] plane(int panel,int index) {
        double a=0,b=0,c=0;
        if(mode==6){a=panel==0?-1:1;c=-a*(lowU+width/2);}
        else if(mode==7){b=panel==0?-1:1;c=-b*(lowV+height/2);}
        else if(mode==3) {
            double[][][] p={{{-1,1,0},{-1,-1,2}},{{1,-1,0},{1,1,-2}},{{-1,1,0},{1,1,-2}},{{1,-1,0},{-1,-1,2}}};
            a=p[panel][index][0]*2/width;b=p[panel][index][1]*2/height;
            c=p[panel][index][2]-a*lowU-b*lowV;
        }
        return new double[]{a*u[0]+b*v[0],a*u[1]+b*v[1],a*u[2]+b*v[2],c};
    }
    public double[] shift(int panel,double progress) {
        double a=mode==4?-width:mode==5?width:mode==6?(panel==0?-width/2:width/2):mode==3?(panel==0?-width/2:panel==1?width/2:0):0;
        double b=mode==7?(panel==0?-height/2:height/2):mode==3?(panel==2?height/2:panel==3?-height/2:0):0;
        return new double[]{(a*u[0]+b*v[0])*progress,(a*u[1]+b*v[1])*progress,(a*u[2]+b*v[2])*progress};
    }
    public static double distance(double[] plane,double[] point){return plane[0]*point[0]+plane[1]*point[1]+plane[2]*point[2]+plane[3];}
    @Override public boolean equals(Object value){if(!(value instanceof PanelMotion))return false;PanelMotion m=(PanelMotion)value;return mode==m.mode && Arrays.equals(u,m.u) && Arrays.equals(v,m.v) && lowU==m.lowU && lowV==m.lowV && width==m.width && height==m.height;}
    @Override public int hashCode(){return 31*Arrays.hashCode(u)+Arrays.hashCode(v)+mode;}
}
