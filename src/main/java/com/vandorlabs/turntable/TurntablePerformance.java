package com.vandorlabs.turntable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in CPU timings; no allocations or clock reads on the normal render/tick path. */
public final class TurntablePerformance {
    public static final boolean ENABLED=Boolean.getBoolean("vandorlabs.profileTurntables");
    private static final ThreadLocal<Map<String,Samples>> SAMPLES=ThreadLocal.withInitial(LinkedHashMap::new);
    private static final class Samples {
        final long[] values=new long[512];
        int count;long total,max,started=System.nanoTime();
    }
    private TurntablePerformance(){}
    public static long start(){return ENABLED?System.nanoTime():0;}
    public static void end(String section,long start){
        if(!ENABLED)return;
        long now=System.nanoTime(),elapsed=now-start;
        Samples s=SAMPLES.get().computeIfAbsent(section,key->new Samples());
        s.values[s.count%s.values.length]=elapsed;s.count++;s.total+=elapsed;s.max=Math.max(s.max,elapsed);
        if(now-s.started<10_000_000_000L)return;
        long[] sorted=Arrays.copyOf(s.values,Math.min(s.count,s.values.length));Arrays.sort(sorted);
        com.vandorlabs.VandorLabs.logger.info("Turntable CPU {} thread={} calls={} mean-us={} recent-p95-us={} max-us={}",section,Thread.currentThread().getName(),s.count,s.total/s.count/1000,sorted[(sorted.length-1)*95/100]/1000,s.max/1000);
        s.count=0;s.total=s.max=0;s.started=now;
    }
}
