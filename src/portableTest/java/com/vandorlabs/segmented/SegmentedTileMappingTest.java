package com.vandorlabs.segmented;
import java.util.*;
/** Tile fronts are independent of section/window classification and complete-door dimensions. */
public final class SegmentedTileMappingTest {
 private static int checks,cases;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static final SegmentedPanelMesh.Texture IMAGE=new SegmentedPanelMesh.Texture(){public float u(double p){return(float)p/16;}public float v(double p){return(float)p/16;}};
 private static final class Capture implements SegmentedPanelMesh.Sink {final List<double[]> vertices=new ArrayList<>();public void vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz){vertices.add(new double[]{x,y,z,u,v,nx,ny,nz});}}
 public static void main(String[] args){
  for(double aspect:new double[]{.25,.5,1,1.5,2,3})for(int row=0;row<8;row++)for(int column=0;column<8;column++)for(boolean endpoint:new boolean[]{false,true}){
   SegmentedPanelMesh.Surface body=new SegmentedPanelMesh.Surface(IMAGE,IMAGE,aspect,false);Capture actual=new Capture(),reference=new Capture();SegmentedNativePanelMesh.build(row,8,column,endpoint,body,IMAGE,actual);SegmentedPanelMesh.build(false,0,1,column,row,19,23,0,0,1,1,1,false,0,body,null,IMAGE,reference);int fronts=0;
   for(double[] a:actual.vertices){if(Math.abs(a[7])<.5)continue;fronts++;boolean found=false;for(double[] b:reference.vertices){boolean same=true;for(int n=0;n<8;n++)same&=Math.abs(a[n]-b[n])<2e-6;if(same){found=true;break;}}need(found,"native Tile front differs from clipped window-path mapping aspect="+aspect+" row="+row+" column="+column);need(a[3]>=-1e-6&&a[3]<=1+1e-6&&a[4]>=-1e-6&&a[4]<=1+1e-6,"UV remains inside sprite");}
   need(fronts>=8,"both front/back faces emitted");if(aspect==1&&!endpoint)need(actual.vertices.size()==24,"square native fast path remains24vertices");cases++;
  }
  need(Math.abs(SegmentedDoorGeometry.HALF_THICKNESS*32-2)<1e-12,"body thickness2pixels");
  for(int direction=0;direction<4;direction++)for(int position=0;position<3;position++){Capture header=new Capture();SegmentedHeaderMesh.emit(direction,position,3,direction==3?2:0,3,0,0,0,0,IMAGE,header);if(header.vertices.isEmpty())continue;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(double[] p:header.vertices){min=Math.min(min,p[2]);max=Math.max(max,p[2]);}need(Math.abs(max-min-.25)<1e-12,"header remains4pixels");}
  System.out.println("Segmented Tile mapping PASS profiles="+cases+" checks="+checks+" allAtlasAspects/rowColumnPhase/frontBack/metricEdges/24vertexSquare/body2/header4");
 }
}
