package com.vandorlabs.client;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;
/** No GL context: validate open-center geometry and pose transforms before live capture. */
final class TrapdoorFrameCoreChecks {
 static void run() throws Exception {
  int choice=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");
  java.lang.reflect.Field field=ScreenFrameBorders.class.getDeclaredField("CACHE");field.setAccessible(true);
  ((Map<Integer,double[]>)field.get(null)).put(choice,new double[]{.08,.12,.06,.09});
  TextureAtlasSprite sprite=new TextureAtlasSprite("frame_core"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(16,16,0,0,false);
  int cases=0;
  for(boolean tall:new boolean[]{false,true})for(int turns=0;turns<4;turns++)for(boolean flip:new boolean[]{false,true})for(boolean tiled:new boolean[]{false,true})for(double pose:new double[]{0,.5,1}){
   double[][] corners=tall?DiagonalTrapdoorGeometry.corners(1,flip,turns,false,false,pose,1/16D,15/16D):TrapdoorGeometry.corners(0,false,turns,pose);
   double[][] uv=new double[8][3],work=new double[8][3];int first=tall?2:0,v=tall?1:2;
   for(int i=0;i<8;i++){uv[i][0]=(i&1)==0?.001:.999;uv[i][v]=(i&(1<<v))==0?.001:.999;if(flip){uv[i][0]=1-uv[i][0];uv[i][v]=1-uv[i][v];}uv[i][tall?2:1]=(i&(tall?4:2))==0?0:1;}
   BufferBuilder b=new BufferBuilder(65536);b.begin(7,BlockSurfaceFormat.get());TrapdoorFrameMesh.draw(b,sprite,sprite,corners,uv,work,0,choice,first,tiled);b.finishDrawing();
   if(b.getVertexCount()<64)throw new AssertionError("frame strips absent "+tall+"/"+flip+"/"+tiled);
   cases++;
  }
  for(int n=1;n<=3;n++)for(int row=0;row<n;row++)for(int col=0;col<n;col++){
   double[][] corners=TrapdoorGeometry.corners(0,false,0,0),uv=new double[8][3],work=new double[8][3];
   for(int i=0;i<8;i++){uv[i][0]=(col+((i&1)==0?.001:.999))/n;uv[i][2]=(row+((i&4)==0?.001:.999))/n;uv[i][1]=(i&2)==0?0:1;}
   BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,BlockSurfaceFormat.get());TrapdoorFrameMesh.draw(buffer,sprite,sprite,corners,uv,work,0,choice,0,false);buffer.finishDrawing();
   if(covers(buffer,corners,.473,.519))throw new AssertionError("joined aperture covered");
   if(col>0&&covers(buffer,corners,.001,.519)||col<n-1&&covers(buffer,corners,.999,.519)||row>0&&covers(buffer,corners,.473,.001)||row<n-1&&covers(buffer,corners,.473,.999))throw new AssertionError("joined frame retained an internal rail");
   if(col==0&&!covers(buffer,corners,.04,.519)||col==n-1&&!covers(buffer,corners,.94,.519)||row==0&&!covers(buffer,corners,.473,.03)||row==n-1&&!covers(buffer,corners,.473,.96))throw new AssertionError("joined frame lost outer rail");
   cases++;
  }
  for(int i=0;i<2300;i++){
   double[][] uv=new double[8][3],work=new double[8][3],corners=TrapdoorGeometry.corners(0,false,0,0);
   for(int j=0;j<8;j++){uv[j][0]=.1+i/100000D+((j&1)==0?0:.5);uv[j][2]=.1+((j&4)==0?0:.5);uv[j][1]=(j&2)==0?0:1;}
   BufferBuilder b=new BufferBuilder(1024);b.begin(7,BlockSurfaceFormat.get());TrapdoorFrameMesh.draw(b,sprite,sprite,corners,uv,work,0,choice,0,false);b.finishDrawing();
  }
  if(TrapdoorFrameMesh.cachedLayouts()>2048)throw new AssertionError("unbounded frame layout cache");
  TrapdoorFrameMesh.clear();if(TrapdoorFrameMesh.cachedLayouts()!=0)throw new AssertionError("frame reload cache clear");ScreenFrameBorders.clear();System.out.println("trapdoor-frame-core PASS cases="+cases+" affine poses asymmetric widths clearances reversed UVs and joined 1x1/2x2/3x3 outer rails");
 }
 private static boolean covers(BufferBuilder buffer,double[][] corners,double u,double v){
  java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=BlockSurfaceFormat.get().getNextOffset();
  for(int i=0;i<buffer.getVertexCount();i+=4){double[][] p=new double[4][3];for(int j=0;j<4;j++){p[j][0]=(data.getFloat((i+j)*stride)-corners[0][0])/(corners[1][0]-corners[0][0]);p[j][1]=data.getFloat((i+j)*stride+4);p[j][2]=(data.getFloat((i+j)*stride+8)-corners[0][2])/(corners[4][2]-corners[0][2]);}
   if(Math.abs(p[0][1]-p[1][1])>1e-6||Math.abs(p[0][1]-p[2][1])>1e-6)continue;
   if(inside(p[0],p[1],p[2],u,v)||inside(p[0],p[2],p[3],u,v))return true;
  }return false;
 }
 private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[2]-a[2])-(b[2]-a[2])*(c[0]-a[0]);}
 private static boolean inside(double[] a,double[] b,double[] c,double u,double v){double area=cross(a,b,c);if(Math.abs(area)<1e-10)return false;double[] p={u,0,v};return cross(p,b,c)/area>1e-6&&cross(a,p,c)/area>1e-6&&cross(a,b,p)/area>1e-6;}
}
