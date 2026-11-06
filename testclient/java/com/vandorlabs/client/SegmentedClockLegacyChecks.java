package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.client.segmented.SegmentedDoorVisuals;
import java.io.*;
import java.util.*;
import net.minecraft.util.math.BlockPos;
/** Actual released sequenced clock, including shared late columns and stale packets. */
public final class SegmentedClockLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int cases=0;try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int direction:new int[]{0,2})for(boolean shared:new boolean[]{false,true}){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false){@Override public long getTotalWorldTime(){return 100;}};SegmentedDoorTile[] tiles={new SegmentedDoorTile(),new SegmentedDoorTile()};for(int n=0;n<2;n++){tiles[n].setWorld(world);tiles[n].setPos(new BlockPos(n,0,0));tiles[n].column=n;tiles[n].columns=4;tiles[n].height=3;tiles[n].ticksPerSection=2;tiles[n].identity=new UUID(direction+1,n+1);tiles[n].settings=new SegmentedDoorSettings(direction,false,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass);}
    int[] columns={0,0,1,0,0,1,0,0,0,0};long[] sequences={1,1,1,1,2,2,1,2,3,3};double[] times={0,2,2,2.5,3,3.5,4,5,5,8};
    for(int step=0;step<times.length;step++){SegmentedDoorTile tile=tiles[columns[step]];long sequence=sequences[step];tile.sequence=sequence;tile.motionIdentity=shared?new UUID(20,sequence):null;tile.targetOpen=sequence!=2;tile.from=sequence==1?(step==3?.8:0):sequence==2?.5:1D/6;tile.started=sequence==1?100:sequence==2?103:105;tile.duration=sequence==1?6:sequence==2?3:5;double initial=tile.pose(0),pose=SegmentedDoorVisuals.sample(tile,times[step]);JsonObject row=new JsonObject();row.addProperty("scenario",direction+":"+shared);row.addProperty("direction",direction);row.addProperty("column",columns[step]);row.addProperty("shared",shared);row.addProperty("sequence",sequence);row.addProperty("target",tile.targetOpen);row.addProperty("initial",initial);row.addProperty("started",tile.started);row.addProperty("duration",tile.duration);row.addProperty("time",times[step]);row.addProperty("expected",pose);out.println(gson.toJson(row));cases++;}
   }
  }System.out.println("Segmented clock actual Forge PASS cases="+cases+" sharedlatecolumns/repeat/stalesequence/reversal/directionspan");
 }
}
