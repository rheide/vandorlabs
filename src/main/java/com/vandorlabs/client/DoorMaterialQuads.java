package com.vandorlabs.client;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
/** Clip world-sized material repeats within an atlas sprite; retain the source vertex format. */
final class DoorMaterialQuads {
 private static final class Point {final int[] data;final double u,v;Point(int[] d,double u,double v){data=d;this.u=u;this.v=v;}}
 static List<BakedQuad> tile(BakedQuad quad,TextureAtlasSprite sprite,double scale){
  int[] source=quad.getVertexData();int stride=source.length/4;EnumFacing.Axis normal=quad.getFace().getAxis();int u=normal==EnumFacing.Axis.X?2:0,v=normal==EnumFacing.Axis.Y?2:1;
  double width=Math.max(1,UnifiedTextureSprites.aspect(sprite)),height=Math.max(1,1/UnifiedTextureSprites.aspect(sprite));
  List<Point> polygon=new ArrayList<>(4);double u0=Double.POSITIVE_INFINITY,v0=u0,u1=-u0,v1=-u0;
  for(int i=0;i<4;i++){int[] d=Arrays.copyOfRange(source,i*stride,(i+1)*stride);double x=Float.intBitsToFloat(d[u])*(u==2?1:scale)/width,y=-Float.intBitsToFloat(d[v])*(v==2?1:scale)/height;polygon.add(new Point(d,x,y));u0=Math.min(u0,x);u1=Math.max(u1,x);v0=Math.min(v0,y);v1=Math.max(v1,y);}
  List<BakedQuad> result=new ArrayList<>();
  for(int x=(int)Math.floor(u0+1e-7);x<Math.ceil(u1-1e-7);x++)for(int y=(int)Math.floor(v0+1e-7);y<Math.ceil(v1-1e-7);y++){
   List<Point> p=clip(clip(clip(clip(polygon,true,x,true,quad.getFormat()),true,x+1,false,quad.getFormat()),false,y,true,quad.getFormat()),false,y+1,false,quad.getFormat());
   for(int j=1;j+1<p.size();j++){
    Point[] tri={p.get(0),p.get(j),p.get(j+1),p.get(j+1)};int[] data=new int[stride*4];
    for(int i=0;i<4;i++){System.arraycopy(tri[i].data,0,data,i*stride,stride);data[i*stride+quad.getFormat().getUvOffsetById(0)/4]=Float.floatToRawIntBits(sprite.getInterpolatedU((tri[i].u-x)*16));data[i*stride+quad.getFormat().getUvOffsetById(0)/4+1]=Float.floatToRawIntBits(sprite.getInterpolatedV((tri[i].v-y)*16));}
    result.add(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
   }
  }
  return result;
 }
 private static List<Point> clip(List<Point> input,boolean u,double boundary,boolean greater,VertexFormat format){
  List<Point> out=new ArrayList<>();for(int i=0;i<input.size();i++){Point a=input.get((i+input.size()-1)%input.size()),b=input.get(i);double av=u?a.u:a.v,bv=u?b.u:b.v;boolean ai=greater?av>=boundary:av<=boundary,bi=greater?bv>=boundary:bv<=boundary;
   if(ai!=bi){double t=(boundary-av)/(bv-av);int[] d=a.data.clone();for(int e=0;e<format.getElementCount();e++){VertexFormatElement element=format.getElement(e);if(element.getType()!=VertexFormatElement.EnumType.FLOAT)continue;int offset=format.getOffset(e)/4;for(int c=0;c<element.getElementCount();c++)d[offset+c]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(a.data[offset+c])+(Float.intBitsToFloat(b.data[offset+c])-Float.intBitsToFloat(a.data[offset+c]))*t));}out.add(new Point(d,a.u+(b.u-a.u)*t,a.v+(b.v-a.v)*t));}
   if(bi)out.add(b);
  }return out;
 }
 private DoorMaterialQuads(){}
}
