package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.fluid.*;
import java.util.*;
import net.minecraft.world.phys.AABB;
/** Independent area/boundary/corner contracts without launching a client or world. */
public final class NativeFluidSurfaceChecks {
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static double area(List<NativeFluidSurface.Surface> surfaces){double result=0;for(var s:surfaces){List<double[]> p=new ArrayList<>();for(var v:s.vertices)p.add(new double[]{v.x,v.y,v.z});result+=NativeFluidSurface.area(p);}return result;}
 public static void main(String[] args){
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
  System.out.println("Native fluid surface PASS unit/partial/different/solid neighbor areas, free top heights, corner coverage; no world/client bootstrap");
 }
}
