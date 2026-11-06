package com.vandorlabs.render;
import java.util.*;
/** Immutable frame strips; keeps the released two-stage affine operation order. */
public final class TrapdoorFrameLayout {
 private static final class Strip {final double[][] corners=new double[8][3],uv=new double[8][3],edgeUv=new double[8][3];TrapdoorSurfaceLayout surface;}
 private final int first;private final List<Strip> strips;
 private TrapdoorFrameLayout(int first,List<Strip> strips){this.first=first;this.strips=strips;}
 public long retainedBytes(){long bytes=64;for(Strip strip:strips)bytes+=1024+strip.surface.retainedBytes();return bytes;}
 public static TrapdoorFrameLayout build(double[][] uv,int first,boolean tiled,double edgeDepth,int joins,double[] borders,double aspect){
 if((first!=0&&first!=2)||joins< -1||joins>15||borders.length!=4||!Double.isFinite(aspect)||aspect<=0)throw new IllegalArgumentException("frame descriptor");
 for(double border:borders)if(!Double.isFinite(border)||border<0||border>1)throw new IllegalArgumentException("frame border");
 if(!Double.isFinite(edgeDepth)||edgeDepth<0||edgeDepth>1)throw new IllegalArgumentException("frame depth");
        int v=first==0?2:1;
        int uAxis=axis(uv,0),vAxis=axis(uv,v),depth=3-uAxis-vAxis;
        double du=uv[1<<uAxis][0]-uv[0][0],dv=uv[1<<vAxis][v]-uv[0][v];
        double u0=Math.min(uv[0][0],uv[1<<uAxis][0]),u1=Math.max(uv[0][0],uv[1<<uAxis][0]);
        double v0=Math.min(uv[0][v],uv[1<<vAxis][v]),v1=Math.max(uv[0][v],uv[1<<vAxis][v]);
        // Tiled artwork has one aperture per leaf. Joined Fit/Stretch keeps one
        // outer frame and omits internal rails, retaining authored width per block.
        boolean left=tiled||(joins<0?u0<.002:(joins&1)==0),right=tiled||(joins<0?u1>1-.002:(joins&2)==0),top=tiled||(joins<0?v0<.002:(joins&4)==0),bottom=tiled||(joins<0?v1>1-.002:(joins&8)==0);
        double l=left?borders[0]:0,r=right?borders[1]:0,t=top?borders[2]:0,b=bottom?borders[3]:0;
        double[] us={0,l,1-r,1},vs={0,t,1-b,1};
        double[] sourceU={left?0:borders[0],borders[0],1-borders[1],right?1:1-borders[1]};
        double[] sourceV={top?0:borders[2],borders[2],1-borders[3],bottom?1:1-borders[3]};
        List<Strip> result=new ArrayList<>(8);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            if(row==1&&col==1 || us[col+1]-us[col]<1e-9 || vs[row+1]-vs[row]<1e-9)continue;
            Strip strip=new Strip();
            for(int i=0;i<8;i++){
                int ui=col+(((i&(1<<uAxis))==0)==(du>0)?0:1),vi=row+(((i&(1<<vAxis))==0)==(dv>0)?0:1);
                double pu=us[ui],pv=vs[vi];
                if(du<0)pu=1-pu;if(dv<0)pv=1-pv;
                strip.corners[i][uAxis]=pu;strip.corners[i][vAxis]=pv;strip.corners[i][depth]=(i&(1<<depth))==0?0:1;
                // Preserve the ordinary leaf's pixel scale on every outside and aperture wall.
                for(int axis=0;axis<3;axis++)strip.edgeUv[i][axis]=strip.corners[i][axis]*(axis==depth?edgeDepth:1);
                strip.uv[i][0]=sourceU[ui];strip.uv[i][v]=sourceV[vi]/aspect;strip.uv[i][depth]=(i&(1<<depth))==0?0:1;
            }
            strip.surface=TrapdoorSurfaceLayout.build(strip.uv,first,first+2,false,false,false);result.add(strip);
        }
        return new TrapdoorFrameLayout(first,result);
    }
 public void emit(double[][] moving,double[][] work,TrapdoorSurfaceLayout.Sink artwork,TrapdoorSurfaceLayout.Sink edge){
 for(Strip strip:strips){
  for(int i=0;i<8;i++)for(int axis=0;axis<3;axis++)work[i][axis]=moving[0][axis]+strip.corners[i][0]*(moving[1][axis]-moving[0][axis])+strip.corners[i][1]*(moving[2][axis]-moving[0][axis])+strip.corners[i][2]*(moving[4][axis]-moving[0][axis]);
  strip.surface.emit(work,artwork);
  for(int face=0;face<6;face++)if(face<first||face>=first+2){int[] indices=TrapdoorGeometry.FACES[face];double[] a=work[indices[0]],b=work[indices[1]],c=work[indices[2]];
   double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
   for(int index:indices){double[] p=work[index],uv=strip.edgeUv[index];edge.vertex(p[0],p[1],p[2],face<4?uv[0]:uv[2],face<2?uv[2]:uv[1],false,(float)(nx/length),(float)(ny/length),(float)(nz/length));}
  }
 }
 }
    private static int axis(double[][] uv,int component){int best=0;for(int axis=1;axis<3;axis++)if(Math.abs(uv[1<<axis][component]-uv[0][component])>Math.abs(uv[1<<best][component]-uv[0][component]))best=axis;return best;}
 }
