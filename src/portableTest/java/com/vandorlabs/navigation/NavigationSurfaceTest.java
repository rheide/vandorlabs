package com.vandorlabs.navigation;

import java.nio.ByteBuffer;

public final class NavigationSurfaceTest {
    public static void main(String[] args){
        for(int radius:new int[]{128,256})checkSweep(radius);
        int[] areas={-32,48,128};
        need(NavigationSurface.mergeAreas(areas,areas)==areas,"duplicate module recording has no write");
        int[] containing=NavigationSurface.mergeAreas(areas,new int[]{32,48,256});
        need(containing.length==3&&containing[0]==32&&containing[2]==256,"contained module areas share the larger footprint");
        NavigationSurface.Atlas atlas=new NavigationSurface.Atlas();
        need(atlas.put(-2,16,33)&&atlas.put(0,16,55),"changed surface pixels stored");
        need(!atlas.put(-2,16,33)&&!atlas.put(0,16,0),"identical or unloaded pixels preserve existing data");
        byte[] data=atlas.serialize();
        need(data.length==144,"two tiles retained once");
        NavigationSurface.Atlas cold=new NavigationSurface.Atlas();
        cold.restore(data);cold.restore(data);
        need(cold.get(-1,17)==33&&cold.get(0,16)==55&&cold.tileCount()==2,"packed atlas restores world alignment and deduplicates");
        cold.restore(new byte[71]);
        cold.restore(ByteBuffer.allocate(72).putLong(Long.MAX_VALUE).array());
        need(cold.tileCount()==2,"malformed atlas records refused");
        NavigationSurface.Snapshot snapshot=NavigationSurface.capture(cold,new int[]{0,0,128},0,0);
        need(snapshot!=null&&snapshot.pixels().length==16384,"portable chip contains real sampled pixels");
        cold.put(-2,16,55);
        need(snapshot.get(-2,16)==33&&snapshot.get(200,200)==0,"saved chip is independent of subsequent world changes");
        NavigationSurface.View local=new NavigationSurface.View(cold,new int[]{0,0,128},null);
        need(local.color(-2,16)==55&&local.color(0,200)==0,"module access is clipped to recorded circles");
        NavigationSurface.View offline=new NavigationSurface.View(null,new int[0],snapshot);
        need(offline.color(-2,16)==33,"chip playback needs no observer or atlas");
        System.out.println("NavigationSurfaceTest PASS sector timing, tick cap, tile deduplication, cold atlas and offline chip");
    }
    private static void checkSweep(int radius){
        NavigationSurface.Atlas atlas=new NavigationSurface.Atlas();
        NavigationSurface.Scanner scanner=new NavigationSurface.Scanner();
        int period=NavigationSurface.period(radius),previous=0;
        for(int t=0;t<period;t++){
            final int phase=t;
            int[] calls={0};
            scanner.request(0,0,radius,t,()->true);
            scanner.request(0,0,radius,t,()->true);
            int work=scanner.tick(atlas,t,64,Long.MAX_VALUE,(x,z)->{
                double angle=Math.atan2(x,-z);
                int sector=Math.min(period-1,(int)((angle<0?angle+2*Math.PI:angle)*period/(2*Math.PI)));
                need(sector==phase&&x*x+z*z<radius*radius,"scanning stays within current angular sector and radius");
                calls[0]++;
                return 6;
            });
            need(work<=64&&work==calls[0],"single budget and deduplication per sweep");
            previous+=work;
        }
        need(previous>0,"actual scan work");
        int before=atlas.tileCount();
        long revision=atlas.revision();
        scanner.request(0,0,radius,0,()->true);
        scanner.tick(atlas,0,64,Long.MAX_VALUE,(x,z)->6);
        need(atlas.tileCount()==before&&atlas.revision()==revision,"repeating the first sector stores no extra data");
        scanner.request(0,0,radius,period,()->false);
        need(scanner.tick(atlas,period,64,Long.MAX_VALUE,(x,z)->7)==0,"inactive source stops immediately");
    }
    private static void need(boolean value,String label){if(!value)throw new AssertionError(label);}
}
