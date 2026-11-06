package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.programmer.JournalOverlay;
import java.util.*;

/** Independent actual released rectangle words, font fitting matrices and navigation artwork. */
public final class JournalOverlayChecks {
 private static int words,texts,logos;
 private static int rgba(int argb){return argb&0xff00ff00|(argb>>>16&255)|(argb&255)<<16;}
 private static float value(JsonArray matrix,int index){return Float.intBitsToFloat(matrix.get(index).getAsInt());}
 public static void main(String[] args)throws Exception{
  JsonArray profiles;try(java.io.InputStream input=new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(java.nio.file.Paths.get(args[0])))){byte[] raw=new byte[2*1024*1024];int size=0,count;while((count=input.read(raw,size,raw.length-size))>0)size+=count;if(size==raw.length)throw new AssertionError("Journal fixture bound");profiles=new JsonParser().parse(new String(raw,0,size,"UTF-8")).getAsJsonObject().getAsJsonArray("profiles");}
  JournalOverlay.Contents warmed=null;
  for(JsonElement element:profiles){final JsonObject profile=element.getAsJsonObject();final JsonArray draws=profile.getAsJsonArray("draws"),expected=profile.getAsJsonArray("text"),data=draws.get(0).getAsJsonObject().getAsJsonArray("data");final int[] vertex={0},command={0},logo={0};List<String> lines=new ArrayList<String>();if(profile.has("lines"))for(JsonElement line:profile.getAsJsonArray("lines"))lines.add(line.getAsString());JournalOverlay.Contents contents=new JournalOverlay.Contents(profile.get("occupied").getAsBoolean(),profile.get("master").getAsBoolean(),profile.get("company").getAsInt(),profile.get("displayName").getAsString(),profile.has("title")?profile.get("title").getAsString():"",profile.has("source")?profile.get("source").getAsString():"",lines,profile.get("page").getAsInt(),profile.has("pages")?profile.get("pages").getAsInt():0);if(contents.pages>0)warmed=contents;
   JournalOverlay.emit(contents,profile.get("height").getAsInt(),new JournalOverlay.Sink(){
    public void rectangle(int l,int t,int r,int b,double z,int color){int[] xs={l,r,r,l},ys={b,b,t,t};for(int v=0;v<4;v++){int at=vertex[0]++*8;int[] actual={Float.floatToRawIntBits(xs[v]),Float.floatToRawIntBits(ys[v]),Float.floatToRawIntBits((float)z),rgba(color)};for(int axis=0;axis<4;axis++){if(data.get(at+axis).getAsInt()!=actual[axis])throw new AssertionError("Journal Source rectangle profile="+profile.get("variant")+" vertex="+vertex[0]+" axis="+axis);words++;}}}
    private void check(String text,int x,int y,int color,float scale,int tx,int ty){if(command[0]>=expected.size())throw new AssertionError("Unexpected journal text");JsonObject row=expected.get(command[0]++).getAsJsonObject();if(!text.equals(row.get("text").getAsString())||x!=row.get("x").getAsInt()||y!=row.get("y").getAsInt()||color!=row.get("color").getAsInt())throw new AssertionError("Journal Source text: "+text+" expected="+row);JsonArray matrix=row.getAsJsonArray("matrix");for(int i=0;i<16;i++){float actual=i==0||i==5?scale:i==10||i==15?1:i==12?tx:i==13?ty:i==14?.04F:0;if(Math.abs(actual-value(matrix,i))>1e-6)throw new AssertionError("Journal Source font affine "+text+" component="+i);}texts++;}
    public void fitted(String text,int x,int y,int width,int color){int measured=expected.get(command[0]).getAsJsonObject().get("width").getAsInt();if(measured>width)check(text,0,0,color,width/(float)measured,x,y);else check(text,x,y,color,1,0,0);}
    public void text(String text,int x,int y,int color){check(text,x,y,color,1,0,0);}
    public void centered(String text,int center,int y,int color){int measured=expected.get(command[0]).getAsJsonObject().get("width").getAsInt();check(text,(center*2-measured)/2,y,color,1,0,0);}
    public boolean companyLogo(int company,int height){logo[0]++;logos++;if(company!=profile.get("company").getAsInt()||profile.get("companyChoice").getAsInt()<0||draws.size()!=2)throw new AssertionError("Journal Source company artwork route");return true;}
   });
   if(vertex[0]!=draws.get(0).getAsJsonObject().get("vertices").getAsInt()||command[0]!=expected.size()||logo[0]!=(profile.get("companyChoice").getAsInt()<0?0:1))throw new AssertionError("Journal Source submission coverage");
  }
  final JournalOverlay.Sink discard=new JournalOverlay.Sink(){public void rectangle(int l,int t,int r,int b,double z,int color){}public void fitted(String value,int x,int y,int width,int color){}public void text(String value,int x,int y,int color){}public void centered(String value,int center,int y,int color){}public boolean companyLogo(int company,int height){return true;}};for(int i=0;i<30000;i++)JournalOverlay.emit(warmed,320,discard);com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);long before=bean.getThreadAllocatedBytes(Thread.currentThread().getId());for(int i=0;i<4096;i++)JournalOverlay.emit(warmed,320,discard);long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-before;if(allocated>1024)throw new AssertionError("Journal overlay warm bytes="+allocated);
  if(profiles.size()!=144)throw new AssertionError("Journal Source profile count");System.out.println("Shared journal overlay PASS actual Source profiles="+profiles.size()+" vertexWords="+words+" texts="+texts+" logos="+logos+" warm4096Bytes="+allocated);
 }
}
