package com.vandorlabs.render;

/** Current immediate wall arm faces; scalar callbacks keep the non-frame hot path allocation-free. */
public final class PanelBoxEmitter {
    public interface Sink<C,M>{void quad(C context,M material,double x0,double y0,double z0,double x1,double y1,double z1,double x2,double y2,double z2,double x3,double y3,double z3);}
    private PanelBoxEmitter(){}
    public static <C,M> void box(Sink<C,M> sink,C context,M face,M side,boolean faceAlongZ,double x0,double y0,double z0,double x1,double y1,double z1,boolean front){
        sink.quad(context, side, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1);
        sink.quad(context, side, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0);
        M xFace = faceAlongZ ? side : face;
        M zFace = faceAlongZ ? face : side;
        sink.quad(context, xFace, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0);
        sink.quad(context, xFace, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1);
        if (front) sink.quad(context, zFace, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0);
        sink.quad(context, zFace, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1);
    }
}
