package com.vandorlabs.segmented;

import com.vandorlabs.physics.RigidTransform;
import com.vandorlabs.render.LegacyBoxTrace;

public final class SegmentedTreadsTest {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void near(double a,double b,String why){need(Math.abs(a-b)<1e-6,why+" "+a+" / "+b);}
 public static void main(String[] args){
  float[] quad={0,0,1,0,1,0,0,1,16,0,1,1,1,0,0,1,16,16,1,1,0,0,0,1,0,16,1,0,0,0,0,1};
  for(int pixels:new int[]{1,2,4,8,16})for(int direction=0;direction<4;direction++){
   float[] split=SegmentedTreads.split(quad,direction<2?1:0,pixels);double area=0;for(int q=0;q<split.length;q+=32){double x0=split[q],y0=split[q+1],x1=split[q+8],y1=split[q+9],x2=split[q+16],y2=split[q+17];area+=Math.abs((x1-x0)*(y2-y0)-(x2-x0)*(y1-y0));}near(area,256,"split preserves area");
   for(int row=0;row<3;row++)for(int column=0;column<4;column++)for(int rotation=0;rotation<4;rotation++)for(double progress:new double[]{0,.05,.25,.5,.95,1}){
    double[] descriptor=new double[14],pose=new double[6],vertex=new double[6];SegmentedTreads.describe(descriptor,row,3,column,4,progress,direction,pixels,.1);
    for(int q=0;q<split.length;q+=32){SegmentedPanelTransform.treadPose(split,q,descriptor,pose);int axis=direction<2?1:0;double midpoint=0;for(int v=0;v<4;v++)midpoint+=split[q+v*8+axis]/4D;int strip=Math.max(0,Math.min(16/pixels-1,(int)(midpoint/pixels)));
     RigidTransform t=SegmentedHitGeometry.treadTransform(0,0,0,rotation,row,3,column,4,progress,direction,pixels,.1,strip);
     for(int v=0;v<4;v++){int at=q+v*8;SegmentedPanelTransform.vertex(split,at,pose,rotation,0,0,0,vertex);double[] point=t.point(split[at]/16D,split[at+1]/16D-.5,split[at+2]/16D);for(int a=0;a<3;a++)near(vertex[a],point[a],"render and interaction transform");
      if(progress==0){double x=split[at]/16D,z=.1+split[at+2]/16D+.5;near(vertex[0],rotation==1?z:rotation==2?1-x:rotation==3?1-z:x,"closed X");near(vertex[1],split[at+1]/16D-row,"closed Y");near(vertex[2],rotation==1?1-x:rotation==2?1-z:rotation==3?x:z,"closed Z");}
     }
     double x=direction<2?.5:(strip+.5)*pixels/16D,y=direction<2?(strip+.5)*pixels/16D-.5:0;double[] start=t.point(x,y,-1),end=t.point(x,y,1);LegacyBoxTrace.Hit hit=SegmentedHitGeometry.traceStrip(t,direction,pixels,strip,null,null,null,null,start[0],start[1],start[2],end[0],end[1],end[2]);need(hit!=null,"moved strip remains selectable");
    }
   }
  }
  apertureAreas();
  System.out.println("Segmented treads PASS checks="+checks+" fiveSizes/fourDirections/fourFacings/closedAlignment/UVArea/movedRays");
 }
 private static void apertureAreas(){
  SegmentedPanelMesh.Texture uv=new SegmentedPanelMesh.Texture(){public float u(double p){return(float)p;}public float v(double p){return(float)p;}};
  for(double scale:new double[]{1,1.5})for(double aspect:new double[]{.25,.5,1,2,4})for(int layout=0;layout<3;layout++){
   double[] area={0};double[][] points=new double[4][2];int[] count={0};com.vandorlabs.render.ReactorArtworkMesh.pane(new double[]{0,0,0,1,2,.125},new SegmentedPanelMesh.Surface(uv,uv,aspect,false),layout,scale,(x,y,z,u,v,nx,ny,nz)->{need(u>=-1e-6&&u<=16.000001&&v>=-1e-6&&v<=16.000001,"replacement glass stays inside its atlas sprite");points[count[0]][0]=x;points[count[0]][1]=y;if(++count[0]==4){double sum=0;for(int a=0;a<4;a++)sum+=points[a][0]*points[(a+1)%4][1]-points[(a+1)%4][0]*points[a][1];area[0]+=Math.abs(sum)/2;count[0]=0;}});near(area[0],2294D/(128*256)*2,"authored octagonal window area survives all layouts");
  }
  double[] paneArea={0};double[][] points=new double[4][2];int[] count={0};SegmentedPanelMesh.Surface material=new SegmentedPanelMesh.Surface(uv,uv,1,false);SegmentedPanelMesh.spiral(1,3,0,false,material,material,uv,(x,y,z,u,v,nx,ny,nz)->{points[count[0]][0]=x;points[count[0]][1]=y;if(++count[0]==4){double sum=0;for(int a=0;a<4;a++)sum+=points[a][0]*points[(a+1)%4][1]-points[(a+1)%4][0]*points[a][1];paneArea[0]+=Math.abs(sum)/2;count[0]=0;}});near(paneArea[0],7137D/64,"four authored Spiral openings have exact area");
 }

}
