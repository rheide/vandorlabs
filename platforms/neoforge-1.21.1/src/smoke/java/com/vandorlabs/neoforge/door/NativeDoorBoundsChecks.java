package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorControlPanel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Exact common native boxes/voxel bounds against actual Forge half-state and pad records. */
public final class NativeDoorBoundsChecks {
    private static long checks;private static volatile Object blackhole;
    private record Query(NativeDoorSettings settings,Direction face,boolean open,boolean left,boolean paired,boolean orphan,AABB expected){}
    private static boolean bool(JsonObject r,String key){return r.has(key)&&r.get(key).getAsBoolean();}
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void box(JsonArray expected,AABB actual,String label){double[] values={actual.minX,actual.minY,actual.minZ,actual.maxX,actual.maxY,actual.maxZ};for(int i=0;i<6;i++)need(Double.doubleToRawLongBits(values[i])==Double.doubleToRawLongBits(expected.get(i).getAsDouble()),label+" raw coordinate "+i+" expected="+expected.get(i)+" actual="+values[i]);}
    private static NativeDoorSettings settings(JsonObject r){boolean sliding=bool(r,"sliding");int depth=r.get("depth").getAsInt();var base=NativeDoorSettings.fresh(false,sliding);return new NativeDoorSettings(new SpaceDoorData(2,1,bool(r,"framed"),0,depth==0,sliding,bool(r,"hinges"),0,true),-1,-1,false,depth,1,bool(r,"security")?1:0,127,base.frames(),base.glass());}
    private static AABB outline(Query q){return q.orphan?NativeDoorGeometry.orphanOutline(q.settings.door().sliding,q.face,q.open,q.left,q.paired):NativeDoorGeometry.outline(q.settings,q.face,q.open,q.left,q.paired);}
    public static void main(String[] args)throws Exception{
        NativeDoorGeometry.clear();List<Query> queries=new ArrayList<>();int leaves=0,panels=0,orphans=0,partials=0;
        try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();var cfg=settings(r);Direction face=Direction.from3DDataValue(r.get("face").getAsInt());String type=r.get("type").getAsString();
            if(type.equals("panel")){AABB actual=NativeDoorGeometry.panel(cfg,face,SpaceDoorControlPanel.Side.valueOf(r.get("side").getAsString()),bool(r,"upper"),bool(r,"mirror"));box(r.getAsJsonArray("bounds"),actual,"panel"+panels);panels++;continue;}
            boolean orphan=type.equals("orphan"),open=bool(r,"open"),left=bool(r,"left"),paired=bool(r,"paired");AABB actual=orphan?NativeDoorGeometry.orphanOutline(cfg.door().sliding,face,open,left,paired):NativeDoorGeometry.outline(cfg,face,open,left,paired);box(r.getAsJsonArray("outline"),actual,type+leaves);
            var selection=orphan?NativeDoorGeometry.orphanShape(cfg.door().sliding,face,open,left,paired,false):NativeDoorGeometry.shape(cfg,face,open,left,paired,false);box(r.getAsJsonArray("outline"),selection.bounds(),type+" voxel");need(selection.toAabbs().size()==1,"one actual leaf AABB without epsilon snapping");
            var collision=orphan?NativeDoorGeometry.orphanShape(cfg.door().sliding,face,open,left,paired,true):NativeDoorGeometry.shape(cfg,face,open,left,paired,true);if(r.get("collision").isJsonNull())need(collision.isEmpty(),"open sliding collision clears");else{need(!collision.isEmpty(),"retained leaf collision");box(r.getAsJsonArray("collision"),collision.bounds(),"collision");need(collision==selection,"collision reuses exact selection geometry");}
            queries.add(new Query(cfg,face,open,left,paired,orphan,actual));if(orphan)orphans++;else if(type.equals("partial"))partials++;else leaves++;
        }}need(leaves==1536&&panels==1536&&orphans==128&&partials==64,"complete current source matrix "+leaves+"/"+panels+"/"+orphans+"/"+partials);
        ExecutorService workers=Executors.newFixedThreadPool(4);try{List<Future<?>> results=new ArrayList<>();for(int thread=0;thread<4;thread++)results.add(workers.submit(()->{for(Query q:queries)if(outline(q)!=q.expected)throw new AssertionError("Concurrent cache identity changed");}));for(Future<?> result:results)result.get();}finally{workers.shutdownNow();}
        Query q=queries.getFirst();var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int i=0;i<20000;i++){blackhole=outline(q);blackhole=NativeDoorGeometry.shape(q.settings,q.face,q.open,q.left,q.paired,false);blackhole=NativeDoorGeometry.panel(q.settings,q.face,SpaceDoorControlPanel.Side.LEFT,false,false);}
        long bytes=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++){blackhole=outline(q);blackhole=NativeDoorGeometry.shape(q.settings,q.face,q.open,q.left,q.paired,false);blackhole=NativeDoorGeometry.panel(q.settings,q.face,SpaceDoorControlPanel.Side.LEFT,false,false);}double allocation=(bean.getThreadAllocatedBytes(thread)-bytes)/300000D;need(allocation<.1,"warmed geometry adds no query wrappers "+allocation);need(NativeDoorGeometry.retainedBytes()<=4*1024*1024,"retained geometry byte budget");
        System.out.println("[vandorlabs][door-bounds] CPU PASS leaves="+leaves+" panels="+panels+" orphan="+orphans+" partial="+partials+" checks="+checks+" entries="+NativeDoorGeometry.entries()+" retainedBytes="+NativeDoorGeometry.retainedBytes()+" B/warmQuery="+allocation+" raw Forge AABB bits/native voxel bounds; no registry/world activation");
    }
}
