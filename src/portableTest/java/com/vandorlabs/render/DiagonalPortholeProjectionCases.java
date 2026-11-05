package com.vandorlabs.render;

import java.util.function.Consumer;

/** Frozen numeric inputs shared by actual Forge and portable projection checks. */
public final class DiagonalPortholeProjectionCases {
    private DiagonalPortholeProjectionCases(){}
    public static final class Case {
        public final int id,mode;public final boolean inverted,reverse,raw,textured;
        public final double lower,upper;public final double[] bounds;
        public final PanelCornerGeometry.Corner corner;public final double[][] vertices;
        private Case(int id,int mode,boolean inverted,int ends,int corner,int bounds,int polygon,boolean reverse,boolean raw,boolean textured){
            this.id=id;this.mode=mode;this.inverted=inverted;this.reverse=reverse;this.raw=raw;this.textured=textured;
            lower=ends==0?Double.NaN:ends==1?-.125:.5;upper=ends==0?Double.NaN:ends==1?.875:.25;
            this.corner=corner(corner);this.bounds=bounds==0?null:bounds==1?new double[]{-2,-8,-2,18,24,18}:bounds==2?new double[]{0,0,0,16,16,16}:new double[]{0,0,0,16,16,16,4,4,4,12,12,12};
            vertices=polygon(polygon);if(raw)for(double[] vertex:vertices){vertex[3]/=16;vertex[4]/=16;}
        }
    }
    private static PanelCornerGeometry.Corner corner(int kind){
        if(kind==0)return null;
        PanelCornerGeometry.Corner result=kind==1?new PanelCornerGeometry.Corner(true,null,false,false):kind==2?new PanelCornerGeometry.Corner(null,false,false,true):kind==3?new PanelCornerGeometry.Corner(true,false,true,false):new PanelCornerGeometry.Corner(false,true,false,true);
        if(kind==4){result.frontFlat=3.25;result.backFlat=10.5;}return result;
    }
    private static double[][] polygon(int kind){
        switch(kind){
            case 0:return new double[][]{{0,0,6,0,16},{16,0,6,16,16},{16,16,6,16,0},{0,16,6,0,0}};
            case 1:return new double[][]{{0,16,10,0,0},{16,16,10,16,0},{16,0,10,16,16},{0,0,10,0,16}};
            case 2:return new double[][]{{0,4,6,0,0},{16,4,6,16,0},{16,4,10,16,4},{0,4,10,0,4}};
            case 3:return new double[][]{{-2,-1,6,-2,17},{18,3,6,18,13},{7,18,6,7,-2}};
            case 4:return new double[][]{{0,0,6,0,16},{12,2,6,12,14},{16,8,6,16,8},{12,14,6,12,2},{0,16,6,0,0}};
            case 5:return new double[][]{{4,4,6,4,12},{4,4,6,4,12},{4,4,6,4,12},{4,4,6,4,12}};
            case 6:return new double[][]{{0,4,6,0,0},{16,4+1e-9,6,16,0},{16,4+1e-9,10,16,4},{0,4,10,0,4}};
            default:return new double[][]{{0,2,6,0,14},{16,2,6,16,14},{16,4,6,16,12},{0,4,6,0,12}};
        }
    }
    public static void visit(Consumer<Case> sink){int id=0;
        for(int polygon=0;polygon<8;polygon++)for(boolean reverse:new boolean[]{false,true})for(boolean raw:new boolean[]{false,true})for(boolean textured:new boolean[]{false,true})sink.accept(new Case(id++,-1,false,0,0,0,polygon,reverse,raw,textured));
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true})for(int ends=0;ends<3;ends++)for(int corner=0;corner<5;corner++)for(int bounds=0;bounds<4;bounds++)for(int polygon=0;polygon<8;polygon++)for(boolean reverse:new boolean[]{false,true})for(boolean raw:new boolean[]{false,true})for(boolean textured:new boolean[]{false,true})sink.accept(new Case(id++,mode,inverted,ends,corner,bounds,polygon,reverse,raw,textured));
    }
}
