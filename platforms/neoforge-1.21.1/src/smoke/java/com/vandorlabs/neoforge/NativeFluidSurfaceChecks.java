package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.fluid.*;
import java.util.*;
import net.minecraft.world.phys.AABB;
/** Independent area/boundary/corner contracts without launching a client or world. */
public final class NativeFluidSurfaceChecks {
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static double area(List<NativeFluidSurface.Surface> surfaces){double result=0;for(var s:surfaces){List<double[]> p=new ArrayList<>();for(var v:s.vertices)p.add(new double[]{v.x,v.y,v.z});result+=NativeFluidSurface.area(p);}return result;}
 private static void same(List<List<double[]>> a,List<List<double[]>> b){require(a.size()==b.size(),"scalar clipping piece count");for(int i=0;i<a.size();i++){require(a.get(i).size()==b.get(i).size(),"scalar clipping vertex count");for(int j=0;j<a.get(i).size();j++)for(int k=0;k<a.get(i).get(j).length;k++)require(Double.doubleToRawLongBits(a.get(i).get(j)[k])==Double.doubleToRawLongBits(b.get(i).get(j)[k]),"scalar clipping coordinate/attribute bits");}}
 public static void main(String[] args){
  List<double[]> polygon=List.of(new double[]{.5,-.25,-.25,-0D,1},new double[]{.5,1.25,-.25,2,3},new double[]{.5,1.25,1.25,4,5},new double[]{.5,-.25,1.25,6,7});int scalarCases=0;
  for(AABB box:List.of(new AABB(0,0,0,1,1,1),new AABB(.25,0,.25,.75,1,.75),new AABB(.5,.5,.5,1,1,1),new AABB(-.25,-.5,-0D,1.25,1.5,1)))for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
   List<List<double[]>> reference=new ArrayList<>(),scalar=new ArrayList<>();NativeFluidSurface.subtract(polygon,NativeFluidSurface.planes(box),x,y,z,reference);NativeFluidSurface.subtractBox(polygon,box,x,y,z,scalar);same(reference,scalar);
   List<double[]> clipped=polygon;for(double[] plane:NativeFluidSurface.planes(box)){clipped=com.vandorlabs.render.DiagonalMeshClip.plane(clipped,plane[0],plane[1],plane[2],plane[3]+plane[0]*x+plane[1]*y+plane[2]*z);if(NativeFluidSurface.area(clipped)<1e-10){clipped=List.of();break;}}same(List.of(clipped),List.of(NativeFluidSurface.clipBox(polygon,box,x,y,z)));scalarCases++;
  }
  var unit=NativeFluidVolume.build(List.of(),NativeFluidVolume.SINGLE_SIDE);require(!unit.facesMaterialized(),"physics eager faces");
  var full=NativeFluidSurface.surfaces(unit,0,null,null);require(full.size()==6&&area(full)==6,"unit boundary area");
  var neighbors=new NativeFluidSurface.Neighbor[6];neighbors[5]=new NativeFluidSurface.Neighbor(unit,true,false,false);var joined=NativeFluidSurface.surfaces(unit,0,neighbors,null);require(joined.size()==5&&area(joined)==5,"same fluid full neighbor hides exactly one face");
  var upper=NativeFluidVolume.build(List.of(new AABB(0,0,0,1,.5,1)),NativeFluidVolume.SINGLE_SIDE);neighbors[5]=new NativeFluidSurface.Neighbor(upper,true,false,false);var partial=NativeFluidSurface.surfaces(unit,0,neighbors,null);require(Math.abs(area(partial)-5.5)<1e-7,"partial wet neighbor clips only matching upper half");
  neighbors[5]=new NativeFluidSurface.Neighbor(upper,false,false,false);require(area(NativeFluidSurface.surfaces(unit,0,neighbors,null))==6,"different fluid keeps boundary");neighbors[5]=new NativeFluidSurface.Neighbor(null,false,false,true);require(area(NativeFluidSurface.surfaces(unit,0,neighbors,null))==5,"solid face cull");
  var lowerTop=NativeFluidSurface.surfaces(upper,0,null,(x,z)->.8);require(Math.abs(area(lowerTop)-3.2)<1e-12,"free surface height affects tops and connected side corners");
  for(var surface:lowerTop)for(var v:surface.vertices)require(v.y>=.5&&v.y<=.8,"height stays inside fluid gap");
  var emptyColumns=List.of(new NativeFluidSurface.Column(false,false,0,false),new NativeFluidSurface.Column(false,false,0,false),new NativeFluidSurface.Column(false,false,0,false),new NativeFluidSurface.Column(false,false,0,false));require(NativeFluidSurface.cornerHeight(emptyColumns)==0,"dry corner");
  var covered=List.of(new NativeFluidSurface.Column(true,false,0,false));require(NativeFluidSurface.cornerHeight(covered)==1,"covered corner full");
  require(NativeFluidSurface.height(1,1,1,1,.3,.7)==.999,"native source top inset");
  System.out.println("Native fluid surface PASS scalar-box-cases="+scalarCases+" raw-bit parity; unit/partial/different/solid neighbor areas, free top heights, corner coverage; no world/client bootstrap");
 }
}
