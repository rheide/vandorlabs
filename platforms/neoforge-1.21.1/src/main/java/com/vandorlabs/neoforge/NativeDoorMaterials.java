package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.render.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Bake-only material projection from the released Door frame/selected-leaf/pane adapters. */
final class NativeDoorMaterials {
 private NativeDoorMaterials(){}
 static List<BakedQuad> content(List<BakedQuad> original){return NativeDoorArtwork.content(original);}

 static BakedQuad copy(BakedQuad q,int[] data,TextureAtlasSprite sprite){return new BakedQuad(data,q.getTintIndex(),q.getDirection(),sprite,q.isShade(),q.hasAmbientOcclusion());}
 static boolean hinge(BakedQuad q){return q.getSprite().contents().name().getPath().endsWith("/hinge");}
 static double[] bounds(List<BakedQuad> source,java.util.function.Predicate<BakedQuad> accept){double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(var q:source)if(accept.test(q))for(int v=0;v<4;v++)for(int a=0;a<3;a++){double p=Float.intBitsToFloat(q.getVertices()[v*8+a]);b[a]=Math.min(b[a],p);b[a+3]=Math.max(b[a+3],p);}return b;}
 private static float u(ProgrammableModel.MaterialSnapshot m,double value){return m.sprite().getU((float)(value*m.usedU()));}
 private static float v(ProgrammableModel.MaterialSnapshot m,double value){return m.sprite().getV((float)(value*m.usedV()));}
 private record Point(int[] data,double u,double v){}
 private static List<Point> clip(List<Point> input,boolean u,double boundary,boolean greater){var out=new ArrayList<Point>();for(int i=0;i<input.size();i++){var a=input.get((i+input.size()-1)%input.size());var b=input.get(i);double av=u?a.u:a.v,bv=u?b.u:b.v;boolean ai=greater?av>=boundary:av<=boundary,bi=greater?bv>=boundary:bv<=boundary;if(ai!=bi){double t=(boundary-av)/(bv-av);int[] data=a.data.clone();for(int offset:new int[]{0,1,2,4,5})data[offset]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(a.data[offset])+(Float.intBitsToFloat(b.data[offset])-Float.intBitsToFloat(a.data[offset]))*t));out.add(new Point(data,a.u+(b.u-a.u)*t,a.v+(b.v-a.v)*t));}if(bi)out.add(b);}return out;}
 static List<BakedQuad> tile(BakedQuad q,ProgrammableModel.MaterialSnapshot target,double scale){
  int[] source=q.getVertices();var axis=q.getDirection().getAxis();int ua=axis==Direction.Axis.X?2:0,va=axis==Direction.Axis.Y?2:1;double aspect=target.usedU()/target.usedV(),width=Math.max(1,aspect),height=Math.max(1,1/aspect),u0=Double.POSITIVE_INFINITY,v0=u0,u1=-u0,v1=-u0;var polygon=new ArrayList<Point>();
  for(int i=0;i<4;i++){int[] d=Arrays.copyOfRange(source,i*8,(i+1)*8);double x=Float.intBitsToFloat(d[ua])*(ua==2?1:scale)/width,y=-Float.intBitsToFloat(d[va])*(va==2?1:scale)/height;polygon.add(new Point(d,x,y));u0=Math.min(u0,x);u1=Math.max(u1,x);v0=Math.min(v0,y);v1=Math.max(v1,y);}
  var result=new ArrayList<BakedQuad>();for(int x=(int)Math.floor(u0+1e-7);x<Math.ceil(u1-1e-7);x++)for(int y=(int)Math.floor(v0+1e-7);y<Math.ceil(v1-1e-7);y++){var p=clip(clip(clip(clip(polygon,true,x,true),true,x+1,false),false,y,true),false,y+1,false);for(int j=1;j+1<p.size();j++){Point[] tri={p.get(0),p.get(j),p.get(j+1),p.get(j+1)};int[] data=new int[32];for(int i=0;i<4;i++){System.arraycopy(tri[i].data,0,data,i*8,8);data[i*8+4]=Float.floatToRawIntBits(u(target,tri[i].u-x));data[i*8+5]=Float.floatToRawIntBits(v(target,tri[i].v-y));}result.add(copy(q,data,target.sprite()));}}
  return result;
 }
 static boolean interior(BakedQuad q,String particle){String name=q.getSprite().contents().name().getPath();boolean broad=q.getDirection().getAxis()==Direction.Axis.Z;return name.endsWith("/metal_side")||name.endsWith("/double_frame_metal")&&!broad||!broad&&name.equals(particle)&&(particle.endsWith("/light_alloy_hull")||particle.endsWith("/programmable_glass/door_side"));}
 static List<BakedQuad> frame(List<BakedQuad> source,String particle,NativeDoorSettings settings,int part,double scale,NativeFlatWallModel.Bake materials){
  boolean front=part==0&&settings.frames().front>=0,inner=settings.frames().interior>=0;if(!front&&!inner)return source;
  double[] b=bounds(source,q->q.getSprite().contents().name().getPath().endsWith("/double_frame_metal")||interior(q,particle));var result=new ArrayList<BakedQuad>();
  for(var q:source){String name=q.getSprite().contents().name().getPath();boolean broad=q.getDirection().getAxis()==Direction.Axis.Z;var selected=front&&name.endsWith("/double_frame_metal")&&broad?materials.glassRails.get(settings.frames().front):inner&&interior(q,particle)?materials.glassRails.get(settings.frames().interior):null;if(selected==null){result.add(q);continue;}
   if(selected.edges().isEmpty()){result.addAll(tile(new BakedQuad(q.getVertices(),-1,q.getDirection(),q.getSprite(),q.isShade(),q.hasAmbientOcclusion()),selected.surface(),scale));continue;}
   int[] data=q.getVertices().clone();double[] qb=bounds(List.of(q),ignored->true);boolean horizontal=q.getDirection().getAxis()==Direction.Axis.Y||broad&&qb[3]-qb[0]>=qb[4]-qb[1];int edge=horizontal?((qb[1]+qb[4])/2>=(b[1]+b[4])/2?FrameEdgeMapping.TOP:FrameEdgeMapping.BOTTOM):((qb[0]+qb[3])/2>=(b[0]+b[3])/2?FrameEdgeMapping.RIGHT:FrameEdgeMapping.LEFT);var target=selected.edges().get(edge);
   for(int i=0;i<4;i++){double x=Float.intBitsToFloat(data[i*8]),y=Float.intBitsToFloat(data[i*8+1]),z=Float.intBitsToFloat(data[i*8+2]),mappedU=(Float.intBitsToFloat(data[i*8+4])-q.getSprite().getU0())/(q.getSprite().getU1()-q.getSprite().getU0()),mappedV=(Float.intBitsToFloat(data[i*8+5])-q.getSprite().getV0())/(q.getSprite().getV1()-q.getSprite().getV0());if(b[3]>b[0]&&b[4]>b[1]){mappedU=horizontal?(x-b[0])/(b[3]-b[0]):(b[4]-y)/(b[4]-b[1]);if(qb[5]-qb[2]>1e-6)mappedV=(z-qb[2])/(qb[5]-qb[2]);else if(horizontal&&qb[4]>qb[1])mappedV=(qb[4]-y)/(qb[4]-qb[1]);else if(qb[3]>qb[0])mappedV=(x-qb[0])/(qb[3]-qb[0]);}data[i*8+4]=Float.floatToRawIntBits(u(target,mappedU));data[i*8+5]=Float.floatToRawIntBits(v(target,mappedV));}result.add(new BakedQuad(data,-1,q.getDirection(),target.sprite(),q.isShade(),q.hasAmbientOcclusion()));
  }return List.copyOf(result);
 }
 static List<BakedQuad> glass(List<BakedQuad> source,NativeDoorSettings settings,double scale,ProgrammableModel.MaterialSnapshot target){
  var glass=settings.glass();if(glass.material==NativeDoorGlass.ORIGINAL)return source;double[] b=bounds(source,q->true);boolean fit=TextureLayoutMode.fit(glass.layout,(b[3]-b[0])*scale,(b[4]-b[1])*scale,target.usedU()/target.usedV());int alpha=Math.round(GlassRenderPolicy.opacity(glass.material,glass.tint)*255);var result=new ArrayList<BakedQuad>();
  for(var q:source){List<BakedQuad> mapped;if(!fit)mapped=tile(q,target,scale);else{int[] d=q.getVertices().clone();for(int i=0;i<4;i++){double x=Float.intBitsToFloat(d[i*8]),y=Float.intBitsToFloat(d[i*8+1]);d[i*8+4]=Float.floatToRawIntBits(u(target,(x-b[0])/Math.max(1e-9,b[3]-b[0])));d[i*8+5]=Float.floatToRawIntBits(v(target,(b[4]-y)/Math.max(1e-9,b[4]-b[1])));}mapped=List.of(copy(q,d,target.sprite()));}for(var m:mapped){int[] d=m.getVertices().clone();for(int i=0;i<4;i++)d[i*8+3]=(d[i*8+3]&0xffffff)|((((d[i*8+3]>>>24)*alpha+127)/255)<<24);result.add(copy(m,d,target.sprite()));}}
  return List.copyOf(result);
 }
 static List<BakedQuad> face(List<BakedQuad> source,NativeDoorSettings settings,ProgrammableModel.MaterialSnapshot target,boolean mirror,double scale){
  double[] b=bounds(source,q->!hinge(q));if(!Double.isFinite(b[0]))throw new IllegalArgumentException("Door has no material surface");boolean fit=TextureLayoutMode.fit(settings.textureLayout(),b[3]-b[0],b[4]-b[1],target.usedU()/target.usedV());var result=new ArrayList<BakedQuad>();
  double width=fit?b[3]-b[0]:Math.max(1,target.usedU()/target.usedV())/scale,height=fit?b[4]-b[1]:Math.max(1,target.usedV()/target.usedU())/scale;
  for(double x=b[0];x<b[3]-1e-9;x+=width)for(double y=b[1];y<b[4]-1e-9;y+=height){double x1=Math.min(x+width,b[3]),y1=Math.min(y+height,b[4]),spanU=(x1-x)/width,spanV=(y1-y)/height;for(int side=0;side<2;side++){double z=b[side==0?2:5];double[][] p=side==0?new double[][]{{x,y,z,0,1},{x,y1,z,0,1-spanV},{x1,y1,z,spanU,1-spanV},{x1,y,z,spanU,1}}:new double[][]{{x,y,z,0,1},{x1,y,z,spanU,1},{x1,y1,z,spanU,1-spanV},{x,y1,z,0,1-spanV}};var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(settings.faceTexture());
   if(fit&&custom!=null&&custom.door())customFace(result,p,custom,side==0?Direction.NORTH:Direction.SOUTH,mirror?b[0]+b[3]:Double.NaN);else result.add(polygon(p,target,side==0?Direction.NORTH:Direction.SOUTH,mirror?b[0]+b[3]:Double.NaN));}}
  for(var q:source)if(hinge(q)||q.getDirection().getAxis()!=Direction.Axis.Z)result.add(q);return List.copyOf(result);
 }
 private static void customFace(List<BakedQuad> result,double[][] points,com.vandorlabs.neoforge.client.NativeBlockMaterials.Material custom,Direction normal,double mirror){
  for(boolean upper:new boolean[]{true,false}){var clipped=new ArrayList<double[]>();for(int j=0;j<points.length;j++){double[] a=points[(j+points.length-1)%points.length],b=points[j];boolean ai=upper?a[4]<=.5:a[4]>=.5,bi=upper?b[4]<=.5:b[4]>=.5;if(ai!=bi){double t=(.5-a[4])/(b[4]-a[4]);double[] p=new double[5];for(int c=0;c<5;c++)p[c]=a[c]+t*(b[c]-a[c]);clipped.add(p);}if(bi)clipped.add(b);}
   var material=new ProgrammableModel.MaterialSnapshot(upper?custom.upper():custom.lower(),1,1);for(int j=1;j+1<clipped.size();j++){double[][] tri={clipped.get(0).clone(),clipped.get(j).clone(),clipped.get(j+1).clone(),clipped.get(j+1).clone()};for(var p:tri)p[4]=upper?p[4]*2:(p[4]-.5)*2;result.add(polygon(tri,material,normal,mirror));}
  }
 }
 private static BakedQuad polygon(double[][] p,ProgrammableModel.MaterialSnapshot target,Direction direction,double mirror){int[] data=new int[32];for(int i=0;i<4;i++){int source=Double.isNaN(mirror)?i:3-i;double[] v=p[source];data[i*8]=Float.floatToRawIntBits((float)(Double.isNaN(mirror)?v[0]:mirror-v[0]));data[i*8+1]=Float.floatToRawIntBits((float)v[1]);data[i*8+2]=Float.floatToRawIntBits((float)v[2]);data[i*8+3]=-1;data[i*8+4]=Float.floatToRawIntBits(u(target,v[3]));data[i*8+5]=Float.floatToRawIntBits(v(target,v[4]));data[i*8+7]=((direction==Direction.NORTH?-127:127)&255)<<16;}return new BakedQuad(data,-1,direction,target.sprite(),false,false);}
 static List<BakedQuad> companyGlass(List<BakedQuad> leaf,ProgrammableModel.MaterialSnapshot target){double[] b=bounds(leaf,q->!hinge(q));double z=(b[2]+b[5])/2;return List.of(polygon(new double[][]{{b[0],b[1],z,0,1},{b[3],b[1],z,1,1},{b[3],b[4],z,1,0},{b[0],b[4],z,0,0}},target,Direction.SOUTH,Double.NaN));}
}
