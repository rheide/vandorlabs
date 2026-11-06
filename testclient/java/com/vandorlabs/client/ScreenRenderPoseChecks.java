package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.screen.*;
import java.io.*;

/** Compares the world-free adapter with matrices captured from the actual released GL call path. */
public final class ScreenRenderPoseChecks {
 public static void main(String[] args)throws Exception {
  JsonObject reference;try(Reader in=new InputStreamReader(new FileInputStream("testclient/fixtures/screen-role-gl-reference.json"),"UTF-8")){reference=new JsonParser().parse(in).getAsJsonObject();}
  double[] matrix=new double[16];double maximum=0;int profiles=0;
  for(JsonElement value:reference.getAsJsonArray("poses")){JsonObject row=value.getAsJsonObject();ScreenFamily family=ScreenFamily.fromId(row.get("id").getAsString());ScreenFamily.State state=family.decode(row.get("meta").getAsInt());int variant=row.get("variant").getAsInt();ScreenRenderPose.write(family,state,variant==2,1,matrix);JsonArray expected=row.getAsJsonArray("modelview");
   for(int i=0;i<16;i++){double error=Math.abs(matrix[i]-Float.intBitsToFloat(expected.get(i).getAsInt()));maximum=Math.max(maximum,error);if(error>2e-7)throw new AssertionError("Released Screen GL pose mismatch "+family+" meta="+row.get("meta")+" variant="+variant+" element="+i+" error="+error);}profiles++;
  }
  ScreenRenderPose.write(ScreenFamily.HALF_CONSOLE,ScreenFamily.HALF_CONSOLE.decode(0),false,-1,matrix);double[] east=matrix.clone();ScreenRenderPose.write(ScreenFamily.HALF_CONSOLE,ScreenFamily.HALF_CONSOLE.decode(5),false,-1,matrix);if(!java.util.Arrays.equals(east,matrix))throw new AssertionError("Saved vertical Half Console must retain released Y-only transform");
  com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();ScreenFamily.State state=ScreenFamily.HALF_DIAGONAL.decode(13);for(int i=0;i<12000;i++)ScreenRenderPose.write(ScreenFamily.HALF_DIAGONAL,state,false,-1,matrix);long before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)ScreenRenderPose.write(ScreenFamily.HALF_DIAGONAL,state,false,-1,matrix);long bytes=meter.getThreadAllocatedBytes(thread)-before;if(bytes!=0)throw new AssertionError("Screen pose allocated "+bytes);
  if(profiles!=210)throw new AssertionError("Screen pose coverage "+profiles);System.out.println("Screen released GL pose PASS profiles="+profiles+" maxError="+maximum+" warmed10000writesBytes="+bytes);
 }
}
