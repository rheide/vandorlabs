package com.vandorlabs.neoforge.propulsion;

import com.google.gson.*;
import com.vandorlabs.propulsion.*;
import com.vandorlabs.render.ConnectedSquare;
import java.nio.file.*;
import java.util.*;

/** Compares the inactive world-free submission backend with actual released client ticks. */
public final class PropulsionEmissionChecks {
 public static void main(String[] args)throws Exception {
  int rows=0,members=0,particles=0;
  try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){JsonObject r=JsonParser.parseString(line).getAsJsonObject();String id=r.get("block").getAsString();int face=r.get("face").getAsInt(),max=r.get("max").getAsInt(),pattern=r.get("pattern").getAsInt();JsonArray cells=r.getAsJsonArray("cells"),actual=new JsonArray();Map<String,JsonArray> map=new HashMap<>();for(JsonElement e:cells){JsonArray c=e.getAsJsonArray();map.put(key(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),c);}
   PropulsionEmission.Sink sink=(x,y,z,f,color,style,speed,spread,block)->{JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);a.add(f);a.add(color);a.add(style);a.add(Float.floatToRawIntBits(speed));a.add(Float.floatToRawIntBits(spread));a.add(block);actual.add(a);};
   for(JsonElement e:cells){JsonArray c=e.getAsJsonArray();int px=c.get(0).getAsInt(),py=c.get(1).getAsInt(),pz=c.get(2).getAsInt(),f=c.get(3).getAsInt();boolean powered=c.get(4).getAsBoolean(),join=c.get(5).getAsBoolean(),selected=c.get(6).getAsBoolean();int size=c.get(7).getAsInt(),x=c.get(8).getAsInt(),y=c.get(9).getAsInt(),level=c.get(10).getAsInt();
    if(PropulsionDefinition.of(id).maxConnected>0&&join){boolean[] cold={false};int right=PropulsionEmission.right(f),up=PropulsionEmission.up(f),mode=powered?selected?2:1:0;ConnectedSquare.Part p=ConnectedSquare.findPartitioned(max,(dx,dy)->{int ax=px+PropulsionEmission.dx(right)*dx+PropulsionEmission.dx(up)*dy,ay=py+PropulsionEmission.dy(right)*dx+PropulsionEmission.dy(up)*dy,az=pz+PropulsionEmission.dz(right)*dx+PropulsionEmission.dz(up)*dy;if(pattern==7&&(ax>>4!=0||az>>4!=0)){cold[0]=true;return false;}JsonArray found=map.get(key(ax,ay,az));return found!=null&&found.get(3).getAsInt()==f&&found.get(5).getAsBoolean()&&(found.get(4).getAsBoolean()?found.get(6).getAsBoolean()?2:1:0)==mode;});if(cold[0])p=ConnectedSquare.Part.SINGLE;if(size!=p.size||x!=p.x||y!=p.y)throw new AssertionError("Actual Source partition "+id+" "+face+" "+pattern+" member"+c);}
    PropulsionEmission.emit(id,px,py,pz,f,powered,selected,level,size,x,y,sink);members++;
   }
   if(!actual.equals(r.getAsJsonArray("particles")))throw new AssertionError("Actual Source submissions "+id+" face="+face+" pattern="+pattern+" variant="+r.get("variant")+" actual="+actual+" expected="+r.get("particles"));particles+=actual.size();rows++;
  }}if(rows!=4992||particles==0)throw new AssertionError("Coverage");System.out.println("Native propulsion emission PASS rows="+rows+" members="+members+" submissions="+particles+" exactSource float/center/core/density/spread/partition/cold");
 }
 private static String key(int x,int y,int z){return x+","+y+","+z;}
}
