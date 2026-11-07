package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import java.nio.file.*;

/** Native authored physical geometry replays actual Source windows, triangle boxes and face rays. */
public final class CanopyPhysicsChecks {
    private static long checks,boxes,rays;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static CanopyPoint point(JsonArray value){return new CanopyPoint(Double.longBitsToDouble(value.get(0).getAsLong()),Double.longBitsToDouble(value.get(1).getAsLong()),Double.longBitsToDouble(value.get(2).getAsLong()));}
    private static void box(JsonArray expected,net.minecraft.world.phys.AABB actual,String context){double[] values={actual.minX,actual.minY,actual.minZ,actual.maxX,actual.maxY,actual.maxZ};for(int i=0;i<6;i++)need(expected.get(i).getAsLong()==Double.doubleToRawLongBits(values[i]),"Source physical box "+context+" axis="+i);}
    private static volatile Object sink;
    private static void cost(NativeCanopyGeometry.Snapshot prepared){
        var output=new java.util.ArrayList<net.minecraft.world.phys.AABB>(prepared.boxes().size());var query=prepared.bounds();
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int i=0;i<2000;i++){output.clear();prepared.appendIntersecting(query,output);sink=prepared.outline();}
        long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<1000;i++){output.clear();prepared.appendIntersecting(query,output);sink=prepared.outline();}long bytes=bean.getThreadAllocatedBytes(thread)-before;
        need(output.size()==prepared.boxes().size(),"Prepared collision references reused");need(bytes==0,"Prepared repeated collision/outline allocation="+bytes);
        System.out.println("[vandorlabs][canopy-prepared] queries=1000 boxes="+output.size()+" allocated="+bytes+" retained="+prepared.retainedBytes());
    }
    public static void main(String[] args)throws Exception {
        var cache=new NativeCanopyGeometry.Cache(2*1024*1024);int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString(),context=kind+"/"+row.get("facing")+"/"+row.get("profile");int facing=row.get("facing").getAsInt(),width=row.get("width").getAsInt();var settings=NativeCanopySettings.read(CanopySettingsChecks.tags(row.getAsJsonObject("settings"))).data();boolean moving=kind.equals("canopy")||kind.equals("long_canopy")||CanopySettingsData.supports(kind)&&settings.openable;double progress=moving?row.get("progress").getAsDouble():0;
            var visual=new NativeCanopyRenderSnapshot(kind,facing,width,0,0,row.get("centered").getAsBoolean(),new NativeCanopySettings(settings));var prepared=cache.prepare(visual,progress,8,80,8);if(prepared.retainedBytes()+4096<=2*1024*1024)need(prepared==cache.prepare(visual,progress,8,80,8),"Prepared cache identity");need(cache.retainedBytes()<=2*1024*1024,"Physical cache byte bound");need(prepared.outline().toAabbs().size()==1,"Selection remains one cuboid");var result=prepared.boxes();var expected=row.getAsJsonArray("boxes");need(expected.size()==result.size(),"Source physical count "+context+" expected="+expected.size()+" actual="+result.size());for(int i=0;i<result.size();i++){box(expected.get(i).getAsJsonArray(),result.get(i),context+"/"+i);boxes++;}box(row.getAsJsonArray("selection"),prepared.bounds(),context+"/selection");
            for(var raw:row.getAsJsonArray("rays")){var ray=raw.getAsJsonObject();var actual=prepared.trace(point(ray.getAsJsonArray("start")),point(ray.getAsJsonArray("end")));var wanted=ray.get("hit");need(wanted==null||wanted.isJsonNull()?actual==null:actual!=null,"Source ray existence "+context+" ray="+rays);if(actual!=null){var hit=wanted.getAsJsonObject();var p=hit.getAsJsonArray("point");need(p.get(0).getAsLong()==Double.doubleToRawLongBits(actual.x)&&p.get(1).getAsLong()==Double.doubleToRawLongBits(actual.y)&&p.get(2).getAsLong()==Double.doubleToRawLongBits(actual.z),"Source exact ray point "+context);need(hit.get("face").getAsInt()==actual.face,"Source exact ray face "+context);}rays++;}if(profiles==0)cost(prepared);profiles++;
        }}cache.clear();need(cache.retainedBytes()==0&&cache.size()==0,"World retirement");need(profiles==176&&boxes==1191320&&rays==5104,"Source physical corpus coverage");System.out.println("[vandorlabs][canopy-physics] PASS SourceProfiles="+profiles+" boxes="+boxes+" rays="+rays+" exactFields="+checks);
    }
}
