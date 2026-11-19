package com.vandorlabs.neoforge;

import com.vandorlabs.render.DoorPanelSplit;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Shared Large Door clipping and caps, prepared once in unscaled two-leaf space. */
final class NativeDoorXPanels {
 private NativeDoorXPanels(){}
 static List<BakedQuad>[] build(List<BakedQuad> source,boolean right,boolean caps){return build(source,right,caps,DoorPanelSplit.DIAGONAL);}
 @SuppressWarnings("unchecked") static List<BakedQuad>[] build(List<BakedQuad> source,boolean right,boolean caps,DoorPanelSplit split){
  List<BakedQuad>[] result=new List[split.panelCount()];for(int panel=0;panel<result.length;panel++){var out=new ArrayList<BakedQuad>();for(var q:source){int[] raw=q.getVertices();var polygon=new ArrayList<float[]>();for(int v=0;v<4;v++){int at=v*8;polygon.add(new float[]{Float.intBitsToFloat(raw[at]),Float.intBitsToFloat(raw[at+1]),Float.intBitsToFloat(raw[at+2]),Float.intBitsToFloat(raw[at+4]),Float.intBitsToFloat(raw[at+5]),q.getDirection().getStepX(),q.getDirection().getStepY(),q.getDirection().getStepZ()});}
    float[] original=polygon.getFirst();polygon=new ArrayList<>(split.clip(polygon,right?1:0,panel));for(int v=1;v+1<polygon.size();v++)out.add(quad(q,new float[][]{polygon.get(0),polygon.get(v),polygon.get(v+1),polygon.get(v+1)}));
    if(caps&&original[7]>.5F&&Math.abs(original[2]-9F/16)<1e-6){
     for(int v=0;v<polygon.size();v++)for(int plane=0;plane<split.planeCount(panel);plane++){
      float[][] cap=split.cap(right?1:0,panel,plane,polygon.get(v),polygon.get((v+1)%polygon.size()));
      if(cap!=null)out.add(quad(q,cap));
     }
    }
   }result[panel]=List.copyOf(out);}return result;
 }
 private static BakedQuad quad(BakedQuad source,float[][] points){int[] raw=new int[32];float[] n=points[0];int normal=(((byte)(n[5]*127))&255)|((((byte)(n[6]*127))&255)<<8)|((((byte)(n[7]*127))&255)<<16);for(int v=0;v<4;v++){for(int a=0;a<3;a++)raw[v*8+a]=Float.floatToRawIntBits(points[v][a]);raw[v*8+3]=-1;raw[v*8+4]=Float.floatToRawIntBits(points[v][3]);raw[v*8+5]=Float.floatToRawIntBits(points[v][4]);raw[v*8+7]=normal;}return new BakedQuad(raw,-1,Direction.getNearest(n[5],n[6],n[7]),source.getSprite(),false,false);}
}
