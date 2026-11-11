package com.vandorlabs.client;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pure CPU comparison against the original battery shell/cap emitter; charge is deliberately uncached. */
public final class BatteryCacheChecks {
    private static volatile double consumed;
    private static final class Trace implements BatteryGeometryCache.Sink {
        final List<Long> data=new ArrayList<>();
        public void beginQuad(int material,float nx,float ny,float nz){data.add((long)material);data.add((long)Float.floatToRawIntBits(nx));data.add((long)Float.floatToRawIntBits(ny));data.add((long)Float.floatToRawIntBits(nz));}
        public void vertex(double x,double y,double z,double u,double v){for(double value:new double[]{x,y,z,u,v})data.add(Double.doubleToRawLongBits(value));}
    }
    private static final class CostSink implements BatteryGeometryCache.Sink {
        double value;
        public void beginQuad(int material,float nx,float ny,float nz){value+=material+nx+ny+nz;}
        public void vertex(double x,double y,double z,double u,double v){value+=x+y+z+u+v;}
    }
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void parity(){
        BatteryGeometryCache cache=new BatteryGeometryCache();int vertices=0;
        for(boolean block:new boolean[]{false,true})for(int axis=0;axis<3;axis++)for(boolean positive:new boolean[]{false,true}){
            BatteryGeometryCache.Mesh mesh=cache.mesh(block,axis,positive);Trace reference=new Trace(),actual=new Trace();ReferenceBatteryGeometry.emit(block,axis,positive,reference);mesh.emit(actual);
            need(reference.data.equals(actual.data),"pre-change battery vertex/UV/material/normal order: "+block+"/"+axis+"/"+positive);
            need(cache.mesh(block,axis,positive)==mesh,"same shape/direction did not reuse immutable geometry");vertices+=mesh.vertexCount();
        }
        need(cache.builds()==12,"complete battery key space");long bytes=cache.retainedBytes();need(bytes<=BatteryGeometryCache.MAX_CACHED_BYTES,"battery cache byte bound");
        BatteryGeometryCache.Mesh held=cache.mesh(false,0,false);Trace before=new Trace();held.emit(before);cache.clear();need(cache.retainedBytes()==0,"clear retained shell/cap geometry");BatteryGeometryCache.Mesh fresh=cache.mesh(false,0,false);Trace after=new Trace();fresh.emit(after);
        need(held!=fresh&&before.data.equals(after.data),"reload rebuild changed geometry");Trace retained=new Trace();held.emit(retained);need(before.data.equals(retained.data),"clearing cache changed in-use immutable geometry");
        System.out.println("battery-cache geometry PASS both shapes, six input directions, exact port UV orientation/material selection, reload; vertices="+vertices+" fullCacheBytes="+bytes);
    }
    private static long allocated(){
        try{Class<?> type=Class.forName("com.sun.management.ThreadMXBean");Object bean=ManagementFactory.getThreadMXBean();if(!(Boolean)type.getMethod("isThreadAllocatedMemorySupported").invoke(bean))return -1;if(!(Boolean)type.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean))type.getMethod("setThreadAllocatedMemoryEnabled",boolean.class).invoke(bean,true);return (Long)type.getMethod("getThreadAllocatedBytes",long.class).invoke(bean,Thread.currentThread().getId());}
        catch(ReflectiveOperationException|SecurityException unavailable){return -1;}
    }
    private static long[] measure(BatteryGeometryCache cache,boolean cached,int count){
        CostSink sink=new CostSink();long bytes=allocated(),start=System.nanoTime();for(int i=0;i<count;i++){if(cached)cache.mesh(false,0,true).emit(sink);else ReferenceBatteryGeometry.emit(false,0,true,sink);}long elapsed=System.nanoTime()-start,after=allocated();consumed=sink.value;return new long[]{elapsed,bytes<0||after<0?-1:after-bytes};
    }
    private static void cost(){
        BatteryGeometryCache cache=new BatteryGeometryCache();cache.mesh(false,0,true);long builds=cache.builds();
        for(int i=0;i<3;i++){measure(cache,false,1000);measure(cache,true,1000);}
        int count=2000;long[] oldTime=new long[5],newTime=new long[5],oldBytes=new long[5],newBytes=new long[5];
        for(int i=0;i<5;i++){long[] old,now;if((i&1)==0){old=measure(cache,false,count);now=measure(cache,true,count);}else{now=measure(cache,true,count);old=measure(cache,false,count);}oldTime[i]=old[0];newTime[i]=now[0];oldBytes[i]=old[1];newBytes[i]=now[1];}
        Arrays.sort(oldTime);Arrays.sort(newTime);Arrays.sort(oldBytes);Arrays.sort(newBytes);need(cache.builds()==builds,"warm battery draw rebuilt geometry");if(oldBytes[2]>=0&&newBytes[2]>=0)need(newBytes[2]<oldBytes[2]/4,"warm battery allocation reduction was lost");
        System.out.printf(Locale.ROOT,"battery-cache cost PASS warmed horizontal hex shell/caps: old=%.3f us/draw, cached=%.3f us/draw; allocated old=%.1f B/draw, cached=%.1f B/draw; additional builds=0; CPU-only, charge indicators excluded, not GPU/FPS evidence%n",oldTime[2]/(count*1000D),newTime[2]/(count*1000D),oldBytes[2]<0?-1:oldBytes[2]/(double)count,newBytes[2]<0?-1:newBytes[2]/(double)count);
    }
    public static void main(String[] args){parity();cost();}
}
