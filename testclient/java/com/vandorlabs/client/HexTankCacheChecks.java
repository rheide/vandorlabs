package com.vandorlabs.client;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Exact pre-cache geometry parity, bounded reuse, and CPU/allocation evidence without a GL context. */
public final class HexTankCacheChecks {
    private static final int CAPACITY=16000;
    private static volatile double consumed;
    private static final class Trace implements HexTankGeometryCache.Sink {
        final List<Long> data=new ArrayList<>();
        public void beginQuad(int material,int color,boolean diffuse,float nx,float ny,float nz){data.add((long)material);data.add((long)color);data.add(diffuse?1L:0L);data.add((long)Float.floatToRawIntBits(nx));data.add((long)Float.floatToRawIntBits(ny));data.add((long)Float.floatToRawIntBits(nz));}
        public void vertex(double x,double y,double z,double u,double v){for(double value:new double[]{x,y,z,u,v})data.add(Double.doubleToRawLongBits(value));}
    }
    private static final class CostSink implements HexTankGeometryCache.Sink {
        double value;
        public void beginQuad(int material,int color,boolean diffuse,float nx,float ny,float nz){value+=material+color+nx+ny+nz+(diffuse?1:0);}
        public void vertex(double x,double y,double z,double u,double v){value+=x+y+z+u+v;}
    }
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void sameOutline(double[][] expected,HexTankGeometryCache.Outline actual,String key){
        need(expected.length==actual.size(),"outline vertex count "+key);
        for(int i=0;i<expected.length;i++)need(Double.doubleToRawLongBits(expected[i][0])==Double.doubleToRawLongBits(actual.x(i))&&Double.doubleToRawLongBits(expected[i][1])==Double.doubleToRawLongBits(actual.z(i)),"outline coordinate "+key+" vertex="+i);
    }
    private static void outlines(){
        HexTankGeometryCache cache=new HexTankGeometryCache(CAPACITY);int cases=0;
        for(boolean block:new boolean[]{false,true}){
            for(int amount=0;amount<=CAPACITY;amount++){
                HexTankGeometryCache.Outline actual=cache.fluidOutline(block,amount);
                sameOutline(ReferenceHexTankGeometry.fluidOutline(block,amount),actual,block+"/"+amount);
                need(cache.fluidOutline(block,amount)==actual,"warm outline missed exact amount key");
                need(cache.outlineBytes()<=HexTankGeometryCache.MAX_OUTLINE_BYTES,"outline byte bound");cases++;
            }
            for(int amount:new int[]{Integer.MIN_VALUE,-1,CAPACITY+1,Integer.MAX_VALUE})sameOutline(ReferenceHexTankGeometry.fluidOutline(block,amount),cache.fluidOutline(block,amount),"clamp/"+amount);
        }
        HexTankGeometryCache.Outline retained=cache.fluidOutline(false,7319);double x=retained.x(0),z=retained.z(0);
        double[][] copy=retained.copy();copy[0][0]=123;copy[0]=new double[]{456,789};
        need(retained.x(0)==x&&retained.z(0)==z,"copy exposed cached outline storage");
        HexTankGeometryCache.Outline full=HexTankGeometryCache.fullOutline(false);copy=full.copy();copy[0][0]=123;
        sameOutline(ReferenceHexTankGeometry.fluidOutline(false,CAPACITY),full,"full immutable profile");
        for(int amount=1;amount<1000;amount++)cache.fluidOutline(false,amount);
        need(cache.fluidOutline(false,7319)!=retained,"old outline was not evicted under byte pressure");
        need(retained.x(0)==x&&retained.z(0)==z,"eviction changed an in-use immutable outline");
        cache.clear();need(cache.outlineBytes()==0&&cache.staticBytes()==0,"clear retained geometry bytes");
        sameOutline(ReferenceHexTankGeometry.fluidOutline(false,7319),cache.fluidOutline(false,7319),"after clear");
        System.out.println("hex-tank-cache outlines PASS exact pre-change doubles for "+cases+" shape/amount pairs, clamping, defensive copies, LRU eviction, clear; retainedBytes="+cache.outlineBytes());
    }
    private static void templates(){
        HexTankGeometryCache cache=new HexTankGeometryCache(CAPACITY);int cases=0,vertices=0;
        // DOWN, UP, NORTH, SOUTH, WEST, EAST retain the original axis-only orientation.
        int[] axes={HexTankGeometryCache.Y,HexTankGeometryCache.Y,HexTankGeometryCache.Z,HexTankGeometryCache.Z,HexTankGeometryCache.X,HexTankGeometryCache.X};
        for(boolean block:new boolean[]{false,true})for(int face=0;face<axes.length;face++)for(boolean tiled:new boolean[]{false,true})for(int size=1;size<=3;size++){
            int axis=axes[face];HexTankGeometryCache.Template actual=cache.template(block,axis,tiled,size);
            need(cache.template(block,axis,tiled,size)==actual,"warm geometry missed complete key");
            for(boolean glass:new boolean[]{false,true}){
                Trace expected=new Trace(),found=new Trace();ReferenceHexTankGeometry.emit(block,axis,tiled,size,glass,expected);
                HexTankGeometryCache.Mesh mesh=glass?actual.windows:actual.shell;mesh.emit(found);
                need(expected.data.equals(found.data),"pre-change vertex/UV/material/color/normal/diffuse order changed: "+block+"/"+face+"/"+tiled+"/"+size+"/"+glass);
                vertices+=mesh.vertexCount();cases++;
            }
        }
        need(cache.templateBuilds()==36,"finite static key space changed");
        need(cache.staticBytes()<=HexTankGeometryCache.MAX_STATIC_BYTES,"static retained byte bound");
        long bytes=cache.staticBytes(),builds=cache.templateBuilds();HexTankGeometryCache.Template held=cache.template(false,HexTankGeometryCache.Y,true,3);
        cache.clear();need(cache.staticBytes()==0,"clear retained static geometry");HexTankGeometryCache.Template fresh=cache.template(false,HexTankGeometryCache.Y,true,3);
        need(held!=fresh&&cache.templateBuilds()==builds+1,"reload clear did not rebuild template");Trace before=new Trace(),after=new Trace();held.shell.emit(before);fresh.shell.emit(after);need(before.data.equals(after.data),"reload changed local geometry");
        System.out.println("hex-tank-cache geometry PASS cases="+cases+" vertices="+vertices+" six facings, both shapes/layouts, joined sizes 1..3, exact opaque/window order, reload; fullCacheBytes="+bytes);
    }
    private static long allocated(){
        try{
            Class<?> type=Class.forName("com.sun.management.ThreadMXBean");Object bean=ManagementFactory.getThreadMXBean();
            if(!(Boolean)type.getMethod("isThreadAllocatedMemorySupported").invoke(bean))return -1;
            if(!(Boolean)type.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean))type.getMethod("setThreadAllocatedMemoryEnabled",boolean.class).invoke(bean,true);
            return (Long)type.getMethod("getThreadAllocatedBytes",long.class).invoke(bean,Thread.currentThread().getId());
        }catch(ReflectiveOperationException|SecurityException unavailable){return -1;}
    }
    private static long[] measure(HexTankGeometryCache cache,boolean cached,int count){
        CostSink sink=new CostSink();long memory=allocated(),start=System.nanoTime();
        for(int i=0;i<count;i++){
            if(cached){HexTankGeometryCache.Outline outline=cache.fluidOutline(false,7319);sink.value+=outline.x(0)+outline.z(0);HexTankGeometryCache.Template template=cache.template(false,HexTankGeometryCache.X,true,3);template.shell.emit(sink);template.windows.emit(sink);}
            else {double[][] outline=ReferenceHexTankGeometry.fluidOutline(false,7319);sink.value+=outline[0][0]+outline[0][1];ReferenceHexTankGeometry.emit(false,HexTankGeometryCache.X,true,3,false,sink);ReferenceHexTankGeometry.emit(false,HexTankGeometryCache.X,true,3,true,sink);}
        }
        long nanos=System.nanoTime()-start,after=allocated();consumed=sink.value;return new long[]{nanos,memory<0||after<0?-1:after-memory};
    }
    private static void cost(){
        HexTankGeometryCache cache=new HexTankGeometryCache(CAPACITY);cache.fluidOutline(false,7319);cache.template(false,HexTankGeometryCache.X,true,3);
        long outlines=cache.outlineBuilds(),templates=cache.templateBuilds();
        for(int i=0;i<3;i++){measure(cache,false,500);measure(cache,true,500);}
        int count=1000;long[] oldTime=new long[5],newTime=new long[5],oldBytes=new long[5],newBytes=new long[5];
        for(int i=0;i<5;i++){
            long[] old,current;if((i&1)==0){old=measure(cache,false,count);current=measure(cache,true,count);}else{current=measure(cache,true,count);old=measure(cache,false,count);}
            oldTime[i]=old[0];newTime[i]=current[0];oldBytes[i]=old[1];newBytes[i]=current[1];
        }
        Arrays.sort(oldTime);Arrays.sort(newTime);Arrays.sort(oldBytes);Arrays.sort(newBytes);
        need(cache.outlineBuilds()==outlines&&cache.templateBuilds()==templates,"warm draws recomputed fill or static geometry");
        if(oldBytes[2]>=0&&newBytes[2]>=0)need(newBytes[2]<oldBytes[2]/4,"warm allocation reduction was lost");
        System.out.printf(Locale.ROOT,"hex-tank-cache cost PASS warmed 3x3 horizontal hex geometry: old=%.3f us/draw, cached=%.3f us/draw; allocated old=%.1f B/draw, cached=%.1f B/draw; additional outline solves=0, static builds=0; CPU-only, not GPU/FPS evidence%n",oldTime[2]/(count*1000D),newTime[2]/(count*1000D),oldBytes[2]<0?-1:oldBytes[2]/(double)count,newBytes[2]<0?-1:newBytes[2]/(double)count);
    }
    public static void main(String[] args){outlines();templates();cost();}
}
