package com.vandorlabs.vehicle;

import java.util.*;

/** Opt-in, bounded sampling of the real animation/tick pipeline in a disposable repro world. */
public final class VehicleAnimationDiagnostics {
    public static final boolean ENABLED=Boolean.getBoolean("vandorlabs.checkVehicleAnimation");
    public static volatile UUID selected;
    public static volatile String phase="setup";
    private static final Map<String,List<Long>> samples=new TreeMap<>();
    private VehicleAnimationDiagnostics(){}
    public static long start(EntityGroundVehicle craft){return ENABLED&&craft!=null&&craft.getUniqueID().equals(selected)?System.nanoTime():0;}
    public static void elapsed(EntityGroundVehicle craft,String name,long start){if(start!=0)value((craft.world.isRemote?"client/":"server/")+name,System.nanoTime()-start);}
    public static void value(String name,long value){valueAt(phase,name,value);}
    public static synchronized void valueAt(String at,String name,long value){if(!ENABLED)return;List<Long> list=samples.computeIfAbsent(at+"/"+name,key->new ArrayList<>());if(list.size()<4096)list.add(value);}
    public static synchronized void dump(){for(Map.Entry<String,List<Long>> entry:samples.entrySet()){List<Long> a=new ArrayList<>(entry.getValue());Collections.sort(a);long sum=0;for(long v:a)sum+=v;System.out.println("[vandorlabs][reprolab] vehicle-animation "+entry.getKey()+" n="+a.size()+" mean="+sum/a.size()+" p50="+a.get(a.size()/2)+" p95="+a.get((a.size()-1)*95/100)+" max="+a.get(a.size()-1)+" units=nanoseconds-unless-count");}}
}
