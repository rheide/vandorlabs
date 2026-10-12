package com.vandorlabs.vehicle;

import java.util.Arrays;

/** Opt-in, bounded controller/reconciliation measurements; never logs per collision box. */
public final class VehicleDiagnostics {
    public static final boolean ENABLED=Boolean.getBoolean("vandorlabs.vehicleDiagnostics") || Boolean.getBoolean("vandorlabs.vehicleNetworkLab");
    private final long[] nanos=ENABLED?new long[256]:null;
    private int count,queries,maxPending,replaySteps;
    private float maxYawError,maxTurn;
    public void step(long elapsed,int terrainQueries,float turn){if(!ENABLED)return;nanos[count++%nanos.length]=elapsed;queries+=terrainQueries;maxTurn=Math.max(maxTurn,Math.abs(turn));}
    public void correction(float yawError,int pending){if(!ENABLED)return;maxYawError=Math.max(maxYawError,Math.abs(yawError));maxPending=Math.max(maxPending,pending);replaySteps+=pending;}
    public String summary(){
        if(!ENABLED || count==0)return "disabled";
        long[] sorted=Arrays.copyOf(nanos,Math.min(count,nanos.length));Arrays.sort(sorted);
        return "steps="+count+" p50-us="+sorted[sorted.length/2]/1000+" p95-us="+sorted[(sorted.length-1)*95/100]/1000+" p99-us="+sorted[(sorted.length-1)*99/100]/1000+" queries="+queries+" max-turn="+maxTurn+" max-yaw-error="+maxYawError+" max-pending="+maxPending+" replay-steps="+replaySteps;
    }
}
