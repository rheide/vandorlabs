package com.vandorlabs.neoforge.world;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Installed identity/lifecycle and reentrant snapshot traversal, without moving-door geometry claims. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeLoadedTileIndexChecks {
    private static int checks;private static long hits;
    private static final Consumer<ProgrammableBlockEntity> COUNT=ignored->hits++;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Loaded tile index: "+message);}
    private static ProgrammableBlockEntity place(ServerLevel level,BlockPos pos){level.getChunkAt(pos);level.setBlockAndUpdate(pos,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());return (ProgrammableBlockEntity)level.getBlockEntity(pos);}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
        if(!"loaded-tile-index".equals(System.getProperty("vandorlabs.transmitterPhase")))return;var level=event.getServer().overworld();var index=new NativeLoadedTileIndex<>(ProgrammableBlockEntity.class);
        var first=place(level,new BlockPos(-1,100,-1));var second=place(level,new BlockPos(8,100,0));var third=place(level,new BlockPos(24,100,0));var area=new AABB(-2,99,-2,26,102,2);
        index.add(first);index.add(second);index.add(third);index.add(first);need(index.size()==3&&index.query(level,area).size()==3,"duplicate registration, negative and separate bucket positions");
        need(index.query(level,new AABB(0,100,-1,1,101,0)).isEmpty(),"touching owner face is excluded");
        need(index.query(level,new AABB(-1e20,-1e20,-1e20,1e20,1e20,1e20)).size()==3,"enormous finite query scans loaded members without overflow");need(index.query(level,new AABB(Double.NaN,0,0,1,1,1)).isEmpty(),"nonfinite input is refused");
        need(index.query(level,new AABB(1e20,100,0,1e20,101,1)).isEmpty(),"saturated narrow positive query terminates without integer wrap");need(index.query(level,new AABB(-1e20,100,0,-1e20,101,1)).isEmpty(),"saturated narrow negative query terminates");
        need(index.visit(level,area,tile->tile==second,COUNT)==1,"filter precedes installed identity delivery");
        boolean rejected=CompletableFuture.supplyAsync(()->{try{index.query(level,area);return false;}catch(IllegalStateException expected){return true;}}).get(5,TimeUnit.SECONDS);need(rejected,"server worker query refused before world access");
        level.removeBlock(first.getBlockPos(),false);var replacement=place(level,first.getBlockPos());first.clearRemoved();index.add(first);index.add(replacement);var current=index.query(level,area);need(current.size()==3&&current.contains(replacement)&&!current.contains(first)&&index.size()==3,"stale identity is pruned after same-position replacement");
        int delivered=index.visit(level,area,null,index::remove);need(delivered==3&&index.size()==0,"visitor removal does not invalidate bucket traversal");
        index.add(replacement);index.add(second);index.add(third);need(index.visit(level,area,tile->{index.remove(tile);return true;},COUNT)==0&&index.size()==0,"filter cannot publish an entry it removed");
        index.add(replacement);index.add(second);index.add(third);for(int i=0;i<20000;i++)index.visit(level,area,null,COUNT);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)index.visit(level,area,null,COUNT);long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes<2048,"settled traversal has no per-query snapshot/visitor allocations bytes="+bytes);
        var remote=area.move(10000,0,10000);need(!index.hasNearbyBuckets(level,remote,5)&&index.hasNearbyBuckets(level,area,5),"scalar padded bucket preflight separates distant and local owners");for(int i=0;i<20000;i++)index.hasNearbyBuckets(level,remote,5);before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)index.hasNearbyBuckets(level,remote,5);long preflight=bean.getThreadAllocatedBytes(thread)-before;need(preflight<2048,"distant collision preflight has no expanded AABB or iterator allocations bytes="+preflight);
        var unload=new LevelEvent.Unload(level);NativeFluidWorld.unload(unload);NativeLoadedTileIndex.Lifecycle.unload(unload);index.add(replacement);need(index.size()==0&&index.query(level,area).isEmpty(),"unload clears membership and prevents late registration");NativeFluidWorld.load(new LevelEvent.Load(level));
        System.out.println("[vandorlabs][loaded-tile-index] dedicated PASS checks="+checks+" settledBytesPerVisit="+(bytes/10000D)+" actual installed replacement,worker guard,finite fallback,reentry,unload; no moving geometry claim");event.getServer().halt(false);
    }
}
