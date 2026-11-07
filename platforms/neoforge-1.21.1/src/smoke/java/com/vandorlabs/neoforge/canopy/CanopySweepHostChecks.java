package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.ramp.NativeRampReservations;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Real asynchronous CPU preparation over installed hosts, driven through the production post-tick drain. */
final class CanopySweepHostChecks {
    private static int checks;private static volatile boolean sink;
    private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Canopy async sweep "+text);}
    private static CompoundTag tag(BlockPos pos,int depth){var tag=new CompoundTag();tag.putLong("AnchorOffset",0);tag.putLong("PairOffset",0);tag.putInt("CanopyWidth",1);tag.putInt("CanopyDepth",depth);tag.putInt("CanopyScale",100);tag.putBoolean("CanopyOpenable",true);return tag;}
    private static void await(ServerLevel world,List<NativeRampReservations.SweepQuery> queries)throws Exception{
        long deadline=System.nanoTime()+15_000_000_000L;
        while(true){NativeCanopySweeps.tick(new LevelTickEvent.Post(()->true,world));boolean all=true;for(var query:queries){var status=query.readiness();need(status!=NativeRampReservations.Status.REFUSED,"worker preparation not refused");all&=status==NativeRampReservations.Status.READY;}if(all)return;if(System.nanoTime()>deadline)throw new AssertionError("Canopy asynchronous preparation timeout");Thread.sleep(1);}
    }
    static void run(ServerLevel world)throws Exception{
        var hosts=new ArrayList<NativeCanopyEntity>();var queries=new ArrayList<NativeRampReservations.SweepQuery>();
        for(int i=0;i<12;i++){var pos=new BlockPos(64+i*16,100,64);var host=CanopyHostChecks.put(world,pos,"front_canopy_glass",Direction.NORTH,tag(pos,80));hosts.add(host);need(host.available()&&host.publication().draws(),"exact installed drawing owner");var query=NativeCanopySweeps.query(host);queries.add(query);need(query.readiness()==NativeRampReservations.Status.PENDING,"first request waits for actual worker completion");for(int repeat=0;repeat<10;repeat++)need(query.readiness()==NativeRampReservations.Status.PENDING,"repeat pending does not publish early");}
        await(world,queries);
        for(int i=0;i<hosts.size();i++){var host=hosts.get(i);var query=queries.get(i);need(query.intersects(new AABB(host.getBlockPos()).inflate(32)),"prepared actual geometry intersects owner area");need(!query.intersects(new AABB(host.getBlockPos().offset(0,64,0))),"distant query clear");for(int repeat=0;repeat<100;repeat++)need(query.readiness()==NativeRampReservations.Status.READY,"warmed repeated journal readiness reuses index");}
        var host=hosts.get(0);var query=queries.get(0);var old=host.settings();host.loadWithComponents(tag(host.getBlockPos(),17),world.registryAccess());need(query.readiness()==NativeRampReservations.Status.PENDING,"settings change retires previous input");host.loadWithComponents(tag(host.getBlockPos(),42),world.registryAccess());need(query.readiness()==NativeRampReservations.Status.PENDING,"late old request cannot replace latest input");await(world,List.of(query));need(host.settings().data().depth==42,"latest installed settings retained");
        var probe=new AABB(host.getBlockPos()).inflate(32);var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<100000;i++)sink=query.intersects(probe);long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)sink=query.intersects(probe);long allocated=bean.getThreadAllocatedBytes(thread)-before;need(allocated==0,"warmed actual sweep queries allocated="+allocated);
        host.loadWithComponents(tag(host.getBlockPos(),61),world.registryAccess());query.readiness();var oldQuery=query;var pos=host.getBlockPos();var blockState=host.getBlockState();world.removeBlockEntity(pos);var replacement=new NativeCanopyEntity(host.getType(),pos,blockState,host.kind());replacement.loadWithComponents(tag(pos,57),world.registryAccess());world.setBlockEntity(replacement);host=replacement;hosts.set(0,replacement);query=NativeCanopySweeps.query(replacement);need(oldQuery.readiness()==NativeRampReservations.Status.REFUSED,"old exact owner cannot publish through same-block replacement");need(query.readiness()==NativeRampReservations.Status.PENDING,"replacement gets matching input generation");await(world,List.of(query));
        var workerQuery=query;need(java.util.concurrent.CompletableFuture.supplyAsync(workerQuery::readiness).get()==NativeRampReservations.Status.REFUSED,"worker cannot discover/request installed world");
        // Retiring a world cancels queued/running tasks and evicts even an already complete publication.
        NativeCanopySweeps.unload(new LevelEvent.Unload(world));need(query.readiness()==NativeRampReservations.Status.PENDING,"world unload clears completed index");host.loadWithComponents(tag(host.getBlockPos(),80),world.registryAccess());query.readiness();NativeCanopySweeps.unload(new LevelEvent.Unload(world));need(query.readiness()==NativeRampReservations.Status.PENDING,"late cancelled task cannot resurrect old world state");await(world,List.of(query));
        NativeCanopySweeps.unload(new LevelEvent.Unload(world));for(var member:hosts){member.removing(true);world.removeBlock(member.getBlockPos(),false);}
        System.out.println("[vandorlabs][canopy-host] actual async sweeps PASS checks="+checks+" queuedHosts=12 settingsRevisions=3 unloadRetirements=3");
    }
}
