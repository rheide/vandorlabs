package com.vandorlabs.screen;

/** Rendered bounds and click actions for full and half-height speed panels. */
public final class SpeedAutopilotPanel {
    public static final int TOGGLE=7, DECREASE=8, CAPTURE=9, INCREASE=10;
    public static int left(int action){return action==CAPTURE?38:action==INCREASE?94:6;}
    public static int right(int action){return action==DECREASE?34:action==CAPTURE?90:122;}
    public static int top(int action,int height){return action==TOGGLE?height/2+height/5:height/2-height/8;}
    public static int bottom(int action,int height){return action==TOGGLE?height-4:height/2+height/8;}
    public static int action(int height,double x,double y){
        for(int i=TOGGLE;i<=INCREASE;i++)
            if(x>=left(i)&&x<right(i)&&y>=top(i,height)&&y<bottom(i,height))return i;
        return -1;
    }
    private SpeedAutopilotPanel(){}
}
