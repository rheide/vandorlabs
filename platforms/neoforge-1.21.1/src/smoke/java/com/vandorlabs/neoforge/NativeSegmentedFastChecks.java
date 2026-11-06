package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.vandorlabs.segmented.*;
import java.nio.file.*;
public final class NativeSegmentedFastChecks {
 public static void main(String[] args)throws Exception{
  var meta=JsonParser.parseString(Files.readAllLines(Path.of(args[1])).getFirst()).getAsJsonObject();float u0=meta.get("u0").getAsFloat(),u1=meta.get("u1").getAsFloat(),v0=meta.get("v0").getAsFloat(),v1=meta.get("v1").getAsFloat();var texture=new SegmentedPanelMesh.Texture(){public float u(double p){return u0+(u1-u0)*(float)p/16F;}public float v(double p){return v0+(v1-v0)*(float)p/16F;}};int profiles=0,total=0;double[] section=new double[6],pose=new double[6],out=new double[6];
  for(String line:Files.readAllLines(Path.of(args[0]))){var record=JsonParser.parseString(line).getAsJsonObject();int height=record.get("height").getAsInt(),rotation=record.get("rotation").getAsInt();double progress=record.get("progress").getAsDouble();var expected=record.getAsJsonArray("vertices");int at=0;
   for(int row=0;row<height;row++){var builder=new NativeSegmentedMesh.Builder();SegmentedNativePanelMesh.build(row,height,progress==0||progress==1,texture,texture,builder);float[] source=builder.records();SegmentedDoorGeometry.section(row,height,progress,section);pose[0]=.5;pose[1]=section[0];pose[2]=section[1];pose[3]=section[2];pose[4]=section[3];pose[5]=1;for(int v=0;v<source.length;v+=8){SegmentedPanelTransform.vertex(source,v,pose,rotation,0,0,0,out);int normal=0;for(int a=0;a<3;a++)normal|=(((byte)((float)out[a+3]*127))&255)<<(a*8);int[] raw={Float.floatToRawIntBits((float)out[0]),Float.floatToRawIntBits((float)out[1]),Float.floatToRawIntBits((float)out[2]),-1,Float.floatToRawIntBits(source[v+3]),Float.floatToRawIntBits(source[v+4]),0x00c00070,normal};for(int value:raw)if(value!=expected.get(at++).getAsInt())throw new AssertionError("Eligible fast path profile="+profiles+" record="+(at-1));}}
   if(at!=expected.size())throw new AssertionError("Complete fast path topology");profiles++;total+=at/8;
  }if(profiles!=60)throw new AssertionError("Complete fast path reference");System.out.println("[vandorlabs][segmented-fast] CPU PASS actualForge profiles="+profiles+" vertices="+total+" exact eligible plain-panel UV/endpoint caps/section transform");
 }
}
