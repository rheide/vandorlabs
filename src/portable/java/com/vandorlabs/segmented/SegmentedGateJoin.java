package com.vandorlabs.segmented;

import java.util.UUID;

/** Prepared owner-relative gate planes only; no world lookup or neighbour ownership is retained. */
public final class SegmentedGateJoin {
    public static final int NONE=-1,PICKET=0,INDUSTRIAL=1,LATTICE=2;
    public static final int LEFT=1,RIGHT=2,BELOW=4,ABOVE=8;
    private static final double EPS=1e-5;
    private static final String[] PARTS={"single","left","middle","right"};
    private static final String[][] MODELS=new String[3][4];
    static{for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)MODELS[kind][part]="vandorlabs:block/segmented_gates/"+style(kind)+"_"+PARTS[part];}
    private SegmentedGateJoin(){}

    /** Lower-left midplane origin, followed by unit right/up vectors. */
    public static final class Frame {
        private final double[] data;
        public Frame(double ox,double oy,double oz,double rx,double ry,double rz,double ux,double uy,double uz){
            data=new double[]{ox,oy,oz,rx,ry,rz,ux,uy,uz};
            for(double value:data)if(Double.isNaN(value)||Double.isInfinite(value))throw new IllegalArgumentException("Nonfinite gate plane");
            if(Math.abs(ox)>128||Math.abs(oy)>128||Math.abs(oz)>128)throw new IllegalArgumentException("Gate plane must be owner-relative");
            double rr=rx*rx+ry*ry+rz*rz,uu=ux*ux+uy*uy+uz*uz,ru=rx*ux+ry*uy+rz*uz;
            if(Math.abs(rr-1)>EPS||Math.abs(uu-1)>EPS||Math.abs(ru)>EPS)throw new IllegalArgumentException("Non-unit gate plane");
        }
    }
    public static final class Cell {
        public final int kind,facing,position,direction,ticks;
        public final boolean straight,moving;
        public final UUID motion;
        public final Frame frame;
        public Cell(int kind,int facing,int position,int direction,boolean straight,int ticks,boolean moving,UUID motion,Frame frame){
            if(kind<NONE||kind>LATTICE||facing<2||facing>5||position<0||position>2||direction<0||direction>3||ticks<5||ticks>30||frame==null)throw new IllegalArgumentException("Invalid gate cell");
            this.kind=kind;this.facing=facing;this.position=position;this.direction=direction;this.straight=straight;this.ticks=ticks;this.moving=moving;this.motion=motion;this.frame=frame;
        }
    }
    private static boolean mechanism(Cell a,Cell b){
        return a!=null&&b!=null&&a.kind>=0&&a.kind==b.kind&&a.facing==b.facing&&a.position==b.position&&a.direction==b.direction&&a.straight==b.straight&&a.ticks==b.ticks&&a.moving==b.moving&&(!a.moving||a.motion!=null&&a.motion.equals(b.motion));
    }
    private static boolean edge(Cell a,Cell b,int x,int y){
        if(!mechanism(a,b))return false;
        double[] p=a.frame.data,q=b.frame.data;
        for(int axis=0;axis<3;axis++){
            if(Math.abs(p[3+axis]-q[3+axis])>EPS||Math.abs(p[6+axis]-q[6+axis])>EPS)return false;
            if(Math.abs(p[axis]+x*p[3+axis]+y*p[6+axis]-q[axis])>EPS)return false;
        }
        return true;
    }
    /** Four direct prepared neighbours; missing/incompatible planes never produce a connector. */
    public static int mask(Cell center,Cell left,Cell right,Cell below,Cell above){
        int result=0;if(edge(center,left,-1,0))result|=LEFT;if(edge(center,right,1,0))result|=RIGHT;
        if(edge(center,below,0,-1))result|=BELOW;if(edge(center,above,0,1))result|=ABOVE;return result;
    }
    private static int variantIndex(int mask){
        if((mask&~15)!=0)throw new IllegalArgumentException("Invalid gate join mask");
        return (mask&LEFT)==0?(mask&RIGHT)==0?0:1:(mask&RIGHT)==0?3:2;
    }
    public static String variant(int mask){return PARTS[variantIndex(mask)];}
    public static String style(int kind){switch(kind){case PICKET:return "picket";case INDUSTRIAL:return "industrial";case LATTICE:return "lattice";default:throw new IllegalArgumentException("Invalid gate style");}}
    public static String model(int kind,int mask){if(kind<0||kind>2)throw new IllegalArgumentException("Invalid gate style");return MODELS[kind][variantIndex(mask)];}
}
