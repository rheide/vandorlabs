package com.vandorlabs.client;

import java.io.*;
import java.util.*;

/** Optional benchmark-only submission timings. Includes driver waits; does not measure GPU execution. */
final class VehicleRenderProfile {
    private static final boolean ENABLED=Boolean.getBoolean("vandorlabs.largeVehicleBenchmark")&&Boolean.getBoolean("vandorlabs.largeBenchmarkFamilies");
    private static boolean recording;
    private static final Map<Object,Bucket[]> families=new LinkedHashMap<>();
    private static final class Bucket {long nanos,calls;}
    static void begin(){if(ENABLED){families.clear();recording=true;}}
    static long start(){return recording?System.nanoTime():0;}
    static void end(Object family,boolean shadow,long started){
        if(started==0)return;long elapsed=System.nanoTime()-started;
        Bucket[] pair=families.get(family);if(pair==null){if(families.size()>=128)return;pair=new Bucket[]{new Bucket(),new Bucket()};families.put(family,pair);}
        Bucket bucket=pair[shadow?1:0];bucket.nanos+=elapsed;bucket.calls++;
    }
    static void write(File directory,String scene,int frames)throws IOException{
        if(!ENABLED)return;recording=false;File file=new File(directory,"vehicle-render-families.csv");boolean header=!file.exists();
        try(PrintWriter out=new PrintWriter(new FileWriter(file,true))){
            if(header)out.println("scene,pass,family,frames,calls,calls_per_frame,submission_mean_ms_per_frame");
            for(Map.Entry<Object,Bucket[]> entry:families.entrySet())for(int pass=0;pass<2;pass++){
                Bucket b=entry.getValue()[pass];if(b.calls==0)continue;Object key=entry.getKey();String label=key instanceof Class?((Class<?>)key).getName():key.toString();
                out.printf(Locale.ROOT,"%s,%s,%s,%d,%d,%.3f,%.6f%n",scene,pass==0?"main":"shadow",label,frames,b.calls,b.calls/(double)frames,b.nanos/1E6/frames);
            }
        }
        families.clear();
    }
    private VehicleRenderProfile(){}
}
