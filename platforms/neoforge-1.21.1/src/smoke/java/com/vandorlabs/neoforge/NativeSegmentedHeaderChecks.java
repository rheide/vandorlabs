package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.vandorlabs.segmented.*;
import java.nio.file.*;
public final class NativeSegmentedHeaderChecks {
 public static void main(String[] args)throws Exception{
  var panel=JsonParser.parseString(Files.readAllLines(Path.of(args[1])).getFirst()).getAsJsonObject();float u0=panel.get("u0").getAsFloat(),u1=panel.get("u1").getAsFloat(),v0=panel.get("v0").getAsFloat(),v1=panel.get("v1").getAsFloat();var texture=new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}};int profiles=0,total=0;
  for(String line:Files.readAllLines(Path.of(args[0]))){var row=JsonParser.parseString(line).getAsJsonObject();var expected=row.getAsJsonArray("vertices");int[] at={0};var builder=new NativeSegmentedMesh.Builder();SegmentedHeaderMesh.emit(row.get("direction").getAsInt(),row.get("position").getAsInt(),row.get("height").getAsInt(),row.get("column").getAsInt(),3,row.get("rotation").getAsInt(),0,0,0,texture,(x,y,z,u,v,nx,ny,nz)->{
   int normal=(((byte)(nx*127))&255)|((((byte)(ny*127))&255)<<8)|((((byte)(nz*127))&255)<<16);int[] raw={Float.floatToRawIntBits((float)x),Float.floatToRawIntBits((float)y),Float.floatToRawIntBits((float)z),-1,Float.floatToRawIntBits(u),Float.floatToRawIntBits(v),0x00c00070,normal};for(int value:raw)if(value!=expected.get(at[0]++).getAsInt())throw new AssertionError("Actual Forge header record index="+(at[0]-1));builder.vertex(x*16,y*16,z*16,u,v,nx,ny,nz);
  });if(at[0]!=expected.size()||builder.finish().vertices()!=expected.size()/8)throw new AssertionError("Header topology");profiles++;total+=expected.size()/8;}
  if(profiles!=432)throw new AssertionError("Complete header profiles");System.out.println("[vandorlabs][segmented-header] CPU PASS actualForge profiles="+profiles+" vertices="+total+" exact fixed border position/UV/light/normal records");
 }
}
