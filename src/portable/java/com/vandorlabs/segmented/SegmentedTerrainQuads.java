package com.vandorlabs.segmented;

/** Released settled cell quads. Atlas lookup and native quad objects belong to the bake. */
public final class SegmentedTerrainQuads {
 private SegmentedTerrainQuads(){}
 public interface Sink {void quad(int direction,boolean header,boolean shade,int[] vertices);}
 private static final int[][] VERTICES={{1,0,4,5},{2,3,7,6},{6,4,0,2},{3,1,5,7},{2,0,1,3},{7,5,4,6}};
 private static final int[][] NORMALS={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
 public static void build(int rotation,boolean header,boolean body,boolean top,boolean bottom,SegmentedPanelMesh.Texture front,SegmentedPanelMesh.Texture edge,Sink out){build(rotation,header,body,top,bottom,new SegmentedPanelMesh.Surface(front,front,1,false),edge,0,0,out);}
 public static void build(int rotation,boolean header,boolean body,boolean top,boolean bottom,SegmentedPanelMesh.Surface front,SegmentedPanelMesh.Texture edge,int column,int row,Sink out){build(rotation,header,body,top,bottom,front,edge,SegmentedPanelMesh.tilePhase(column,row,front.door?.5:front.aspect),out);}
 public static void build(int rotation,boolean header,boolean body,boolean top,boolean bottom,SegmentedPanelMesh.Surface front,SegmentedPanelMesh.Texture edge,long phase,Sink out){
  if(rotation<0||rotation>3)throw new IllegalArgumentException("Segmented terrain rotation");
  if(header)header(rotation,edge,out);
  if(body){
   final int[] at={0},face={0};final int[][] data={new int[32]};
   SegmentedPanelMesh.nativeCellPhase(top,bottom,phase,front,edge,(x,y,z,u,v,nx,ny,nz)->{
    float px=(float)(x/16),py=(float)(y/16),pz=(float)(z/16+.5);int i=at[0];data[0][i]=Float.floatToRawIntBits(rx(px,pz,rotation));data[0][i+1]=Float.floatToRawIntBits(py);data[0][i+2]=Float.floatToRawIntBits(rz(px,pz,rotation));data[0][i+3]=-1;data[0][i+4]=Float.floatToRawIntBits(u);data[0][i+5]=Float.floatToRawIntBits(v);
    if(i==0)face[0]=direction(nx,ny,nz,rotation);at[0]+=8;if(at[0]==32){out.quad(face[0],false,false,data[0]);at[0]=0;data[0]=new int[32];}
   });
  }
 }
 private static float rx(float x,float z,int r){return r==1?z:r==2?1-x:r==3?1-z:x;}
 private static float rz(float x,float z,int r){return r==1?1-x:r==2?1-z:r==3?x:z;}
 private static int direction(float nx,float ny,float nz,int r){float x=r==1?nz:r==2?-nx:r==3?-nz:nx,z=r==1?-nx:r==2?-nz:r==3?nx:nz;return ny<0?0:ny>0?1:z<0?2:z>0?3:x<0?4:5;}
 private static void header(int rotation,SegmentedPanelMesh.Texture edge,Sink out){
  float[] lo={0,14.032F/16F,6/16F},hi={1,16.032F/16F,10/16F};
  float v0=16F-16.032F,v1=16F-14.032F;float[][] matrix=com.vandorlabs.render.LegacyVariantMatrix.y(((4-rotation)&3)*90);
  for(int face=0;face<6;face++){
   float u0=face<4?0:6,u1=face<4?16:10,w0=face<2?6:v0,w1=face<2?10:v1;
   float[][] points=new float[4][3];float[] us=new float[4],vs=new float[4],min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
   for(int v=0;v<4;v++){int corner=VERTICES[face][v];float x=((corner&4)==0?lo:hi)[0],y=((corner&2)==0?lo:hi)[1],z=((corner&1)==0?lo:hi)[2];if(rotation==0){points[v][0]=x;points[v][1]=y;points[v][2]=z;}else com.vandorlabs.render.LegacyVariantMatrix.apply(matrix,x,y,z,points[v]);for(int a=0;a<3;a++){min[a]=Math.min(min[a],points[v][a]);max[a]=Math.max(max[a],points[v][a]);}
    // FaceBakery1.12 contracts each default UV towards its opposite corner in double precision.
    float u=v<2?u0:u1,oppositeU=v<2?u1:u0,w=v==0||v==3?w0:w1,oppositeV=v==0||v==3?w1:w0;us[v]=edge.u((double)u*.999+oppositeU*.001);vs[v]=edge.v((double)w*.999+oppositeV*.001);
   }
   int d=direction(NORMALS[face][0],NORMALS[face][1],NORMALS[face][2],rotation),normal=(((byte)(NORMALS[d][0]*127))&255)|((((byte)(NORMALS[d][1]*127))&255)<<8)|((((byte)(NORMALS[d][2]*127))&255)<<16);int[] data=new int[32];
   for(int v=0;v<4;v++){int corner=VERTICES[d][v];float x=((corner&4)==0?min:max)[0],y=((corner&2)==0?min:max)[1],z=((corner&1)==0?min:max)[2];int source=0;while(source<4&&(Math.abs(points[source][0]-x)>=1e-5F||Math.abs(points[source][1]-y)>=1e-5F||Math.abs(points[source][2]-z)>=1e-5F))source++;if(source==4)throw new IllegalStateException("Header face corner");int i=v*8;data[i]=Float.floatToRawIntBits(x);data[i+1]=Float.floatToRawIntBits(y);data[i+2]=Float.floatToRawIntBits(z);data[i+3]=-1;data[i+4]=Float.floatToRawIntBits(us[source]);data[i+5]=Float.floatToRawIntBits(vs[source]);data[i+7]=normal;}
   out.quad(d,true,true,data);
  }
 }
}
