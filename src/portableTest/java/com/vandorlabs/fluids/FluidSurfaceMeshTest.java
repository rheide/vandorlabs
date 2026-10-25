package com.vandorlabs.fluids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FluidSurfaceMeshTest {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double area(List<FluidSurfaceMesh.Surface> surfaces){double total=0;for(FluidSurfaceMesh.Surface surface:surfaces){List<double[]> points=new ArrayList<double[]>();for(FluidSurfaceMesh.Vertex p:surface.vertices)points.add(new double[]{p.x,p.y,p.z});total+=FluidSurfaceMesh.area(points);}return total;}
    private static FluidVolume slab(boolean upper){return FluidVolume.build(Collections.singletonList(new FluidVolume.Box(0,upper?.5:0,0,1,upper?1:.5,1)),null,new FluidVolume.SideMap(){public int side(double x,double y,double z){return 0;}});}
    private static double oldCorner(List<FluidSurfaceMesh.Column> columns){float depth=0;int count=0;for(FluidSurfaceMesh.Column c:columns){if(c.above)return 1;if(c.same){int level=c.level>=8?0:c.level;float d=(level+1)/9F;if(c.level>=8||c.level==0){depth+=d*10;count+=10;}depth+=d;count++;}else if(!c.solid){depth++;count++;}}return count==0?8D/9:1-depth/count;}
    public static void main(String[] args){
        FluidVolume bottom=slab(false),top=slab(true);List<FluidSurfaceMesh.Surface> dry=FluidSurfaceMesh.surfaces(bottom,0,null,null);need(Math.abs(area(dry)-4)<1e-12,"slab void surface area");
        FluidSurfaceMesh.Neighbor[] neighbors=new FluidSurfaceMesh.Neighbor[6];neighbors[5]=new FluidSurfaceMesh.Neighbor(bottom,true,false,false);need(Math.abs(area(FluidSurfaceMesh.surfaces(bottom,0,neighbors,null))-3.5)<1e-8,"matching adjacent wet half must remove exactly shared half-face");
        neighbors[5]=new FluidSurfaceMesh.Neighbor(top,true,false,false);need(Math.abs(area(FluidSurfaceMesh.surfaces(bottom,0,neighbors,null))-4)<1e-8,"corner-only fluid contact must retain full side face");
        FluidVolume quarter=FluidVolume.build(Collections.singletonList(new FluidVolume.Box(0,0,0,1,.75,1)),null,(x,y,z)->0);neighbors[5]=new FluidSurfaceMesh.Neighbor(quarter,true,false,false);need(Math.abs(area(FluidSurfaceMesh.surfaces(bottom,0,neighbors,null))-3.75)<1e-8,"partial wet region subtraction must retain exposed lower quarter");
        neighbors[5]=new FluidSurfaceMesh.Neighbor(bottom,false,false,false);need(Math.abs(area(FluidSurfaceMesh.surfaces(bottom,0,neighbors,null))-4)<1e-8,"different-fluid negative control must retain boundary");neighbors[5]=new FluidSurfaceMesh.Neighbor(null,false,false,true);need(Math.abs(area(FluidSurfaceMesh.surfaces(bottom,0,neighbors,null))-3.5)<1e-12,"native same-fluid or opaque boundary");
        List<double[]> rectangle=Arrays.asList(new double[]{0,0,0,0},new double[]{1,0,0,10},new double[]{1,1,0,10},new double[]{0,1,0,0});List<List<double[]>> pieces=new ArrayList<List<double[]>>();FluidSurfaceMesh.subtract(rectangle,Collections.singletonList(new double[]{1,0,0,.5}),0,0,0,pieces);need(pieces.size()==1&&Math.abs(FluidSurfaceMesh.area(pieces.get(0))-.5)<1e-7,"half-space preserves exact retained area");for(double[] p:pieces.get(0))need(Math.abs(p[3]-10*p[0])<1e-10,"interpolated vertex payload preserved");
        FluidSurfaceMesh.Column[] states={new FluidSurfaceMesh.Column(false,false,0,true),new FluidSurfaceMesh.Column(false,false,0,false),new FluidSurfaceMesh.Column(false,true,0,false),new FluidSurfaceMesh.Column(false,true,3,false),new FluidSurfaceMesh.Column(false,true,8,false),new FluidSurfaceMesh.Column(true,true,0,false)};int heights=0;
        for(FluidSurfaceMesh.Column a:states)for(FluidSurfaceMesh.Column b:states)for(FluidSurfaceMesh.Column c:states)for(FluidSurfaceMesh.Column d:states){List<FluidSurfaceMesh.Column> columns=Arrays.asList(a,b,c,d);need(Double.doubleToLongBits(FluidSurfaceMesh.cornerHeight(columns))==Double.doubleToLongBits(oldCorner(columns)),"legacy four-column float accumulation parity");heights++;}
        need(Math.abs(FluidSurfaceMesh.height(1,1,1,1,.25,.75)-.999)<1e-12,"covered-fluid interpolation/inset");need(bottom.sideAt(.5,.5,.5)==0&&bottom.sideAt(.5,.499,.5)==-1,"half-open solid/fluid partition");try{dry.get(0).vertices.clear();throw new AssertionError("mutable published surface");}catch(UnsupportedOperationException expected){}
        System.out.println("FluidSurfaceMesh PASS exact non-panel surface/partial-neighbour subtraction, attribute interpolation, immutable publication, different-fluid negative and "+heights+" legacy corner-height cases");
    }
}
