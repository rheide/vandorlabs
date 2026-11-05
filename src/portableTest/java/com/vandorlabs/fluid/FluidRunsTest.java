package com.vandorlabs.fluid;

import java.util.*;

/** Independent ordered run goldens, strict solids, caller-owned outputs and scalar factory inputs. */
public final class FluidRunsTest {
    private interface Bounds<B>{double minimum(B box,int axis);double maximum(B box,int axis);}
    private interface Side<W,P>{int side(W world,P position,double[] aperture,double x,double y,double z);}
    private interface Factory<C>{C create(double a,double b,double c,double d,double e,double f,int side);}
    private static <B,C,W,P> void append(List<C> cells,List<B> solid,double[][] cuts,W world,P position,double[] aperture,Bounds<B> bounds,Side<W,P> side,Factory<C> factory){
        new FluidRuns<B,C,W,P>(){
            protected double minimum(B box,int axis){return bounds.minimum(box,axis);}
            protected double maximum(B box,int axis){return bounds.maximum(box,axis);}
            protected int side(W w,P p,double[] plane,double x,double y,double z){return side.side(w,p,plane,x,y,z);}
            protected C createCell(double a,double b,double c,double d,double e,double f,int owner){return factory.create(a,b,c,d,e,f,owner);}
        }.append(cells,solid,cuts,world,position,aperture);
    }
    private static final class Box {final double[] v;Box(double... values){v=values;}}
    private static final class Cell {final double[] v;final int side;Cell(int side,double... values){this.side=side;v=values;}}
    private static final class Context {final int mode;int calls;Context(int mode){this.mode=mode;}}
    private static final Object POSITION=new Object();
    private static final Bounds<Box> BOUNDS=new Bounds<Box>(){public double minimum(Box b,int axis){return b.v[axis];}public double maximum(Box b,int axis){return b.v[axis+3];}};
    private static final Side<Context,Object> SIDE=(context,position,aperture,x,y,z)->{need(position==POSITION,"opaque position identity");context.calls++;return context.mode==1?(x<.5?0:1):context.mode==2?(y<.25?0:1):0;};
    private static final Factory<Cell> CELL=(a,b,c,d,e,f,side)->new Cell(side,a,b,c,d,e,f);
    private static void need(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static void expected(List<Cell> cells,int[] sides,double[]... coordinates){need(cells.size()==coordinates.length,"ordered run count");for(int i=0;i<coordinates.length;i++){need(cells.get(i).side==sides[i],"ordered side");for(int j=0;j<6;j++)need(Double.doubleToRawLongBits(cells.get(i).v[j])==Double.doubleToRawLongBits(coordinates[i][j]),"raw coordinate "+i+"/"+j);}}
    private static List<Cell> build(Context context,double[][] cuts,Box... solid){List<Cell> cells=new ArrayList<>();double[][] before=new double[3][];for(int i=0;i<3;i++)before[i]=cuts[i].clone();append(cells,Arrays.asList(solid),cuts,context,POSITION,null,BOUNDS,SIDE,CELL);for(int i=0;i<3;i++)need(Arrays.equals(before[i],cuts[i]),"read-only cuts");return cells;}
    public static void main(String[] ignored){
        Context z=new Context(1);expected(build(z,new double[][]{{0,.5,1},{0,.5,1},{0,.5,1}}),new int[]{0,0,1,1},new double[]{0,0,0,.5,.5,1},new double[]{0,.5,0,.5,1,1},new double[]{.5,0,0,1,.5,1},new double[]{.5,.5,0,1,1,1});need(z.calls==8,"Z tie axis samples every partition");
        Context x=new Context(0);expected(build(x,new double[][]{{0,.3,.7,1},{0,.5,1},{0,1}},new Box(.3,0,0,.7,1,1)),new int[]{0,0,0,0},new double[]{0,0,0,.3,.5,1},new double[]{.7,0,0,1,.5,1},new double[]{0,.5,0,.3,1,1},new double[]{.7,.5,0,1,1,1});need(x.calls==6,"ownership sampled before solid rejection");
        Context y=new Context(2);expected(build(y,new double[][]{{0,1},{0,.25,.5,1},{0,.5,1}}),new int[]{0,1,0,1},new double[]{0,0,0,1,.25,.5},new double[]{0,.25,0,1,1,.5},new double[]{0,0,.5,1,.25,1},new double[]{0,.25,.5,1,1,1});need(y.calls==6,"Y longest axis");
        expected(build(new Context(0),new double[][]{{0,.5,1},{0,.5,1},{0,1}}),new int[]{0,0},new double[]{0,0,0,1,.5,1},new double[]{0,.5,0,1,1,1});
        expected(build(new Context(0),new double[][]{{-0.0,0.0,Double.MIN_VALUE,1},{0,1},{0,1}}),new int[]{0},new double[]{-0.0,0,0,1,1,1});
        expected(build(new Context(0),new double[][]{{0,1},{0,1},{0,1}},new Box(.5,0,0,1,1,1)),new int[]{0},new double[]{0,0,0,1,1,1});
        Context blocked=new Context(0);need(build(blocked,new double[][]{{0,1},{0,1},{0,1}},new Box(0,0,0,1,1,1)).isEmpty()&&blocked.calls==1,"strictly occupied sample emits no cell");
        List<Cell> target=new ArrayList<>();Cell sentinel=new Cell(9,-1,-1,-1,0,0,0);target.add(sentinel);double[] aperture={0,0,1,.25,.75};int[] created={0};append(target,Collections.<Box>emptyList(),new double[][]{{0,1},{0,1},{0,.25,.75,1}},new Context(0),POSITION,aperture,BOUNDS,(context,pos,plane,a,b,c)->{need(plane==aperture&&pos==POSITION,"caller aperture/context passed directly");return c<.5?0:1;},(a,b,c,d,e,f,side)->{created[0]++;return new Cell(side,a,b,c,d,e,f);});need(target.get(0)==sentinel&&created[0]==2&&target.size()==3,"direct factory called once per merged cell and caller list preserved");
        System.out.println("FluidRuns PASS ordered XYZ/tied axes strict occupancy blocked sampling raw coordinates direct factories caller ownership");
    }
}
