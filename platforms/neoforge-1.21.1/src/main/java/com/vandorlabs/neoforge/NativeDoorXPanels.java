package com.vandorlabs.neoforge;

import com.vandorlabs.render.XDoorPanel;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Released StaticSurfaceMesh diagonal clipping/caps, prepared once in unscaled two-leaf space. */
final class NativeDoorXPanels {
 private NativeDoorXPanels(){}
 @SuppressWarnings("unchecked") static List<BakedQuad>[] build(List<BakedQuad> source,boolean right,boolean caps){
  List<BakedQuad>[] result=new List[4];for(int panel=0;panel<4;panel++){var out=new ArrayList<BakedQuad>();for(var q:source){int[] raw=q.getVertices();var polygon=new ArrayList<float[]>();for(int v=0;v<4;v++){int at=v*8;polygon.add(new float[]{Float.intBitsToFloat(raw[at]),Float.intBitsToFloat(raw[at+1]),Float.intBitsToFloat(raw[at+2]),Float.intBitsToFloat(raw[at+4]),Float.intBitsToFloat(raw[at+5]),q.getDirection().getStepX(),q.getDirection().getStepY(),q.getDirection().getStepZ()});}
    float[] original=polygon.getFirst();polygon=new ArrayList<>(XDoorPanel.clip(polygon,right?1:0,panel));for(int v=1;v+1<polygon.size();v++)out.add(quad(q,new float[][]{polygon.get(0),polygon.get(v),polygon.get(v+1),polygon.get(v+1)}));
    if(caps&&original[7]>.5F&&Math.abs(original[2]-9F/16)<1e-6){for(int v=0;v<polygon.size();v++){float[] a=polygon.get(v),b=polygon.get((v+1)%polygon.size());double length=Math.hypot(a[0]-b[0],a[1]-b[1]);if(length<1e-6)continue;for(int plane=0;plane<2;plane++){if(Math.abs(XDoorPanel.distance(panel,plane,a[0]+(right?1:0),a[1]))>1e-6||Math.abs(XDoorPanel.distance(panel,plane,b[0]+(right?1:0),b[1]))>1e-6)continue;float[] c=b.clone(),d=a.clone();c[2]=d[2]=7F/16;float nx=(float)((b[1]-a[1])/length),ny=(float)((a[0]-b[0])/length);float[][] points={a.clone(),d,c,b.clone()};for(var p:points){p[5]=nx;p[6]=ny;p[7]=0;}out.add(quad(q,points));}}}
   }result[panel]=List.copyOf(out);}return result;
 }
 private static BakedQuad quad(BakedQuad source,float[][] points){int[] raw=new int[32];float[] n=points[0];int normal=(((byte)(n[5]*127))&255)|((((byte)(n[6]*127))&255)<<8)|((((byte)(n[7]*127))&255)<<16);for(int v=0;v<4;v++){for(int a=0;a<3;a++)raw[v*8+a]=Float.floatToRawIntBits(points[v][a]);raw[v*8+3]=-1;raw[v*8+4]=Float.floatToRawIntBits(points[v][3]);raw[v*8+5]=Float.floatToRawIntBits(points[v][4]);raw[v*8+7]=normal;}return new BakedQuad(raw,-1,Direction.getNearest(n[5],n[6],n[7]),source.getSprite(),false,false);}
}
