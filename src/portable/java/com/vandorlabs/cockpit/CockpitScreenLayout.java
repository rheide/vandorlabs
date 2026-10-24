package com.vandorlabs.cockpit;

/** Shared display geometry and slider hit regions, in square-pixel screen coordinates. */
public final class CockpitScreenLayout {
    public static final int WIDTH=128,SLIDER_LEFT=60,SLIDER_RIGHT=120;
    public final int height,rows;
    public CockpitScreenLayout(double surfaceWidth,double surfaceHeight,String role,int rows){
        height=Math.max(1,(int)Math.round(WIDTH*surfaceHeight/surfaceWidth));
        this.rows=Math.max(0,Math.min("screen".equals(role)?6:3,rows));
    }
    public int row(double v){return rows==0?-1:Math.max(0,Math.min(rows-1,(int)((1-v)*rows)));}
    public int top(int row){return row*height/rows;}
    public int bottom(int row){return (row+1)*height/rows;}
    public int widgetTop(int row){return (top(row)+bottom(row)-10)/2;}
    public double sliderColumn(double u){int segment=Math.max(0,Math.min(3,(int)((u*WIDTH-SLIDER_LEFT)*4/(SLIDER_RIGHT-SLIDER_LEFT))));return segment/3D;}
}
