package com.vandorlabs.garage;

/** Rigid sections rise, turn over the header, then travel along the overhead track. */
public final class GarageDoorGeometry {
    public static final double HALF_THICKNESS=3D/32,TURN=.5,ARC=Math.PI/4;
    /** Writes centre Y/Z and sin/cos of the section angle; no per-frame objects. */
    public static void section(int row,int height,double progress,double[] pose){
        double p=Math.max(0,Math.min(1,progress)),travel=p*(height+ARC-.5),t=.5-row+travel;
        if(t<=.5){pose[0]=t;pose[1]=.5;pose[2]=0;pose[3]=1;}
        else if(t<.5+ARC){double a=(t-.5)/TURN;pose[2]=Math.sin(a);pose[3]=Math.cos(a);pose[0]=.5+TURN*pose[2];pose[1]=.5+TURN*(1-pose[3]);}
        else{pose[0]=1;pose[1]=1+t-(.5+ARC);pose[2]=1;pose[3]=0;}
    }
    private GarageDoorGeometry(){}
}
