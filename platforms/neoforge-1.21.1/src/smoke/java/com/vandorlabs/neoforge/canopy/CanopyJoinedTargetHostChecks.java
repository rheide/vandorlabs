package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.neoforge.door.NativeDoorTargets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Engine authored callbacks and cached helper outlines replay released component-target records. */
final class CanopyJoinedTargetHostChecks {
    private static long checks,rays,members;private static double maxOutlineRoundTrip;private static volatile Object sink;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy joined targets "+why);}
    private static BlockPos pos(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    private static Vec3 point(JsonArray p){return new Vec3(Double.longBitsToDouble(p.get(0).getAsLong()),Double.longBitsToDouble(p.get(1).getAsLong()),Double.longBitsToDouble(p.get(2).getAsLong()));}
    private static void hit(JsonElement expected,BlockHitResult actual,String context){need(expected==null?actual==null:actual!=null,"existence "+context);if(actual!=null){var wanted=expected.getAsJsonObject();need(actual.getLocation().equals(point(wanted.getAsJsonArray("point"))),"exact point "+context);need(actual.getDirection().get3DDataValue()==wanted.get("face").getAsInt(),"exact face "+context);need(actual.getBlockPos().equals(pos(wanted.getAsJsonArray("pos"))),"selected constituent "+context+" actual="+actual.getBlockPos()+" expected="+wanted.get("pos"));}}
    static void run(ServerLevel world)throws Exception{
        var installed=new ArrayList<NativeCanopyEntity>();var viewer=new net.minecraft.world.entity.decoration.ArmorStand(world,0,0,0);int profiles=0;
        try(var reader=Files.newBufferedReader(Path.of(System.getenv("VANDOR_CANOPY_JOINED_RAY_REFERENCE")))){for(String line;(line=reader.readLine())!=null;){for(var old:installed)old.removing(true);for(var old:installed)world.removeBlock(old.getBlockPos(),false);installed.clear();var row=JsonParser.parseString(line).getAsJsonObject();var facing=Direction.from3DDataValue(row.get("facing").getAsInt());String kind=row.get("id").getAsString();
            for(var raw:row.getAsJsonArray("members")){var member=raw.getAsJsonObject();var host=CanopyHostChecks.put(world,pos(member.getAsJsonArray("pos")),kind,facing,CanopySettingsChecks.tags(member.getAsJsonObject("saved")));host.onLoad();installed.add(host);}for(var host:installed)NativeCanopyLifecycle.prepare(host);
            int at=0;for(var raw:row.getAsJsonArray("members")){var member=raw.getAsJsonObject();var host=installed.get(at++);var local=host.getBlockState().getShape(world,host.getBlockPos()).bounds();var box=local.move(host.getBlockPos());var wanted=member.getAsJsonArray("selection");double[] values={box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ},locals={local.minX,local.minY,local.minZ,local.maxX,local.maxY,local.maxZ};for(int axis=0;axis<6;axis++){double expected=Double.longBitsToDouble(Long.parseLong(wanted.get(axis).getAsString()));int origin=axis%3==0?host.getBlockPos().getX():axis%3==1?host.getBlockPos().getY():host.getBlockPos().getZ();need(Double.doubleToRawLongBits(locals[axis])==Double.doubleToRawLongBits(expected-origin),"Source outline projected to engine-local origin profile="+profiles+" member="+host.getBlockPos()+" axis="+axis);maxOutlineRoundTrip=Math.max(maxOutlineRoundTrip,Math.abs(values[axis]-expected));}members++;}
            var caller=installed.get(installed.size()-1);if(profiles==0||profiles==12){var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<100000;i++)sink=caller.getBlockState().getShape(world,caller.getBlockPos());long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)sink=caller.getBlockState().getShape(world,caller.getBlockPos());long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes==0,"warmed helper outlines allocate="+bytes);need(caller.getBlockState().getShape(world,caller.getBlockPos()).toAabbs().size()==1,"helper outline never unions dense physical faces");}
            for(var raw:row.getAsJsonArray("rays")){var ray=raw.getAsJsonObject();var start=point(ray.getAsJsonArray("start"));var end=point(ray.getAsJsonArray("end"));var expected=ray.get("hit");var target=NativeCanopyTargets.trace(world,start,end);hit(expected,target==null?null:target.hit(),"indexed profile="+profiles);var selected=NativeDoorTargets.pick(world,viewer,start,end);hit(expected,selected==null?null:selected.hit(),"nearest profile="+profiles);hit(expected,world.clipWithInteractionOverride(start,end,caller.getBlockPos(),caller.getBlockState().getShape(world,caller.getBlockPos()),caller.getBlockState()),"actual helper authored callback profile="+profiles);rays++;}profiles++;
        }}for(var old:installed)old.removing(true);for(var old:installed)world.removeBlock(old.getBlockPos(),false);need(profiles==144&&rays==1872,"Source joined coverage");System.out.println("[vandorlabs][canopy-host] joined targets PASS profiles="+profiles+" members="+members+" rays="+rays+" checks="+checks+" maxOutlineWorldRoundTrip="+maxOutlineRoundTrip);
    }
}
