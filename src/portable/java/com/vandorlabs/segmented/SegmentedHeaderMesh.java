package com.vandorlabs.segmented;
/** Released fixed leading-edge border with cropped metric edge artwork. */
public final class SegmentedHeaderMesh {
 private SegmentedHeaderMesh(){}
    private static final double H=SegmentedDoorGeometry.HALF_THICKNESS;
    private static final double[][][] FACES={
        {{0,-.5,H},{0,-.5,-H},{1,-.5,-H},{1,-.5,H}},{{0,.5,-H},{0,.5,H},{1,.5,H},{1,.5,-H}},
        {{1,-.5,-H},{0,-.5,-H},{0,.5,-H},{1,.5,-H}},{{0,-.5,H},{1,-.5,H},{1,.5,H},{0,.5,H}},
        {{0,-.5,-H},{0,-.5,H},{0,.5,H},{0,.5,-H}},{{1,-.5,H},{1,-.5,-H},{1,.5,-H},{1,.5,H}}};
    private static final int[][] NORMALS={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
 public static void emit(int direction,int position,int height,int column,int columns,int rotation,double x,double y,double z,SegmentedPanelMesh.Texture edge,SegmentedPanelMesh.Sink out){
        if(direction==0&&position==0)return;if(direction==2&&column!=0||direction==3&&column!=columns-1)return;
        double minX=0,maxX=1,minY=.877,maxY=1.002;
        if(direction==1){minY=1-height-.002;maxY=minY+.125;}
        if(direction>=2){minX=direction==2?-.002:.877;maxX=minX+.125;minY=1-height;maxY=1;}
        double offset=position==1?-13D/32:position==2?13D/32:0;double lo=.375+offset,hi=.625+offset;
        for(int face=0;face<6;face++){
            int ua=face<4?0:2,va=face<2?2:1;double[] low={minX,minY,lo},high={maxX,maxY,hi};
            for(int u=(int)Math.floor(low[ua]);u<(int)Math.ceil(high[ua]);u++)for(int v=(int)Math.floor(low[va]);v<(int)Math.ceil(high[va]);v++){
                double u0=Math.max(u,low[ua]),u1=Math.min(u+1,high[ua]),v0=Math.max(v,low[va]),v1=Math.min(v+1,high[va]);
                for(int corner=0;corner<4;corner++){
                    double[] source=FACES[face][corner],point={source[0]==0?minX:maxX,source[1]<0?minY:maxY,source[2]<0?lo:hi};
                    double cu=(ua==0?source[0]>0:source[2]>0)?u1:u0,cv=(va==1?source[1]>0:source[2]>0)?v1:v0;point[ua]=cu;point[va]=cv;
                    double px=point[0],py=point[1],pz=point[2],rx=rotation==1?pz:rotation==2?1-px:rotation==3?1-pz:px,rz=rotation==1?1-px:rotation==2?1-pz:rotation==3?px:pz;
                    double nx=NORMALS[face][0],nz=NORMALS[face][2];out.vertex(x+rx,y+py,z+rz,edge.u((cu-u)*16),edge.v((va==1?1-(cv-v):cv-v)*16),(float)(rotation==1?nz:rotation==2?-nx:rotation==3?-nz:nx),NORMALS[face][1],(float)(rotation==1?-nx:rotation==2?-nz:rotation==3?nx:nz));
                }
            }
        }
    }
}
