package com.vandorlabs.segmented;
/** Eligible released plain panels use four-vertex faces and omit internal endpoint caps. */
public final class SegmentedNativePanelMesh {
 private SegmentedNativePanelMesh(){}
 private static final double H=SegmentedDoorGeometry.HALF_THICKNESS;
 private static final double[][][] FACES={{{0,-.5,H},{0,-.5,-H},{1,-.5,-H},{1,-.5,H}},{{0,.5,-H},{0,.5,H},{1,.5,H},{1,.5,-H}},{{1,-.5,-H},{0,-.5,-H},{0,.5,-H},{1,.5,-H}},{{0,-.5,H},{1,-.5,H},{1,.5,H},{0,.5,H}},{{0,-.5,-H},{0,-.5,H},{0,.5,H},{0,.5,-H}},{{1,-.5,H},{1,-.5,-H},{1,.5,-H},{1,.5,H}}};
 private static final int[][] NORMALS={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
 public static void build(int row,int height,boolean endpoint,SegmentedPanelMesh.Texture front,SegmentedPanelMesh.Texture edge,SegmentedPanelMesh.Sink out){
  for(int face=0;face<6;face++){if(endpoint&&(face==0&&row<height-1||face==1&&row>0))continue;SegmentedPanelMesh.Texture image=face==2||face==3?front:edge;
   for(int v=0;v<4;v++){double[] p=FACES[face][v];out.vertex(p[0]*16,(p[1]+.5)*16,p[2]*16,image.u(v==1||v==2?16:0),image.v(v<2?16:0),NORMALS[face][0],NORMALS[face][1],NORMALS[face][2]);}
  }
 }
}
