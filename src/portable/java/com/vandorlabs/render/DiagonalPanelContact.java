package com.vandorlabs.render;

/** Numeric plane/end contact. Hosts select compatible walls and project neighbour offsets. */
public final class DiagonalPanelContact {
    private DiagonalPanelContact() { }
    public static boolean samePlane(int mode,boolean ai,boolean bi,boolean opposite,int deltaY,double normal){
        double span=DiagonalWallGeometry.span(mode);
        double slopeA=ai?-span:span,slopeB=bi?-span:span;
        double baseA=ai?span:0,baseB=bi?span:0;
        if(mode==2){
            baseA+=ai?.5:0;baseB+=bi?.5:0;
            if(opposite){baseB+=slopeB;slopeB=-slopeB;}
            baseB+=deltaY-slopeB*normal;
        }else{
            if(opposite){baseB=1-baseB;slopeB=-slopeB;}
            baseB+=normal-slopeB*deltaY;
        }
        return Math.abs(slopeA-slopeB)<1e-8&&Math.abs(baseA-baseB)<1e-8;
    }
    public static boolean endsMeet(int mode,boolean ai,boolean bi,boolean opposite,int deltaY,double normal,boolean upper){
        double t=upper?1:0,span=DiagonalWallGeometry.span(mode);
        double ac=(ai?1-t:t)*span,bc;
        if(mode==2){
            ac+=ai?.5:0;double bt=t-normal;if(opposite)bt=1-bt;
            bc=deltaY+(bi?.5:0)+(bi?1-bt:bt)*span;
        }else{
            double bt=t-deltaY;bc=(bi?1-bt:bt)*span;
            if(opposite)bc=1-bc;bc+=normal;
        }
        return Math.abs(ac-bc)<1e-8;
    }
}
