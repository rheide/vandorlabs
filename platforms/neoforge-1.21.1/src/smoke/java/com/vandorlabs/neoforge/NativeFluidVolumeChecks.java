package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import java.util.*;
import net.minecraft.world.phys.AABB;

/** Independent Cartesian ownership oracle; production never builds this grid. */
public final class NativeFluidVolumeChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var empty=NativeFluidVolume.build(List.of(),NativeFluidVolume.SINGLE_SIDE);
        require(empty.cells().size()==1&&!empty.facesMaterialized(),"empty unit run/lazy");
        require(empty.faces().size()==6&&empty.faces()==empty.faces(),"unit boundary faces/cached");
        var slab=NativeFluidVolume.build(List.of(new AABB(0,0,0,1,.5,1)),NativeFluidVolume.SINGLE_SIDE);
        require(slab.cells().size()==1&&slab.cells().getFirst().box().equals(new AABB(0,.5,0,1,1,1)),"slab bounds");
        int[] axes={1,1,2,2,0,0};
        double[][] first={{0,0,0},{0,1,0},{0,1,0},{0,1,1},{0,1,0},{1,1,0}};
        for(int n=0;n<6;n++){var face=empty.faces().get(n);var v=face.vertices().getFirst();require(face.axis()==axes[n]&&face.side()==0&&v.x==first[n][0]&&v.y==first[n][1]&&v.z==first[n][2],"face order/winding origin "+n);}
        try{empty.faces().getFirst().vertices().clear();throw new AssertionError("mutable face vertices");}catch(UnsupportedOperationException expected){}
        var split=NativeFluidVolume.build(List.of(),(x,y,z)->x<.5?0:1,new double[]{.5},new double[0],new double[0]);
        require(split.sideAt(0,0,0)==0&&split.sideAt(Math.nextDown(.5),.5,.5)==0&&split.sideAt(.5,.5,.5)==1&&split.sideAt(1,.5,.5)==-1,"half-open side ownership");
        require(slab.sideAt(.5,.5,.5)==0&&slab.sideAt(.5,Math.nextDown(.5),.5)==-1,"solid gap partition");
        int cases=0,points=0;
        for(int seed=0;seed<64;seed++){
            List<AABB> boxes=new ArrayList<>();Random random=new Random(seed);
            for(int n=0;n<12;n++){int x=random.nextInt(8),y=random.nextInt(8),z=random.nextInt(8);boxes.add(new AABB(x/8D,y/8D,z/8D,(x+1)/8D,(y+1)/8D,(z+1)/8D));}
            var volume=NativeFluidVolume.build(boxes,(x,y,z)->x<.5?0:1,new double[]{.5},new double[0],new double[0]);
            require(!volume.facesMaterialized(),"physics materialized faces");
            for(int x=0;x<8;x++)for(int y=0;y<8;y++)for(int z=0;z<8;z++){
                double cx=(x+.5)/8,cy=(y+.5)/8,cz=(z+.5)/8;boolean blocked=false;
                for(AABB box:boxes)if(box.contains(cx,cy,cz)){blocked=true;break;}
                int count=0;for(var cell:volume.cells())if(cell.box().contains(cx,cy,cz)){count++;require(cell.side()==(cx<.5?0:1),"side ownership");}
                require(count==(blocked?0:1),"Cartesian occupancy seed="+seed);points++;
            }
            require(!volume.facesMaterialized(),"cell traversal materialized faces");
            try{volume.cells().clear();throw new AssertionError("mutable cells");}catch(UnsupportedOperationException expected){}
            cases++;
        }
        List<AABB> complex=new ArrayList<>();for(int n=0;n<100;n++)complex.add(new AABB(n/100D,n/100D,n/100D,(n+.2)/100,(n+.2)/100,(n+.2)/100));
        var large=NativeFluidVolume.build(complex,NativeFluidVolume.SINGLE_SIDE);require(!large.facesMaterialized(),"large eager face graph");
        require(large.cells().size()<200_000,"uncoalesced Cartesian cells");
        double[] fine=new double[127];for(int n=0;n<fine.length;n++)fine[n]=(n+1)/128D;var manyFaces=NativeFluidVolume.build(List.of(),NativeFluidVolume.SINGLE_SIDE,fine,fine,new double[0]);IllegalStateException retainedFailure=null;try{manyFaces.faces();throw new AssertionError("unbounded retained face graph");}catch(IllegalStateException expected){require(expected.getMessage().contains("retained budget"),"retained face bound diagnostic");retainedFailure=expected;}require(!manyFaces.facesMaterialized(),"failed graph never publishes partial faces");try{manyFaces.faces();throw new AssertionError("uncached face budget failure");}catch(IllegalStateException expected){require(expected==retainedFailure,"immutable failed graph does not retry expensive work");}
        double[] deep=new double[2047];for(int n=0;n<deep.length;n++)deep[n]=(n+1)/2048D;var scratch=NativeFluidVolume.build(List.of(),NativeFluidVolume.SINGLE_SIDE,new double[0],deep,deep);try{scratch.faces();throw new AssertionError("unbounded sweep scratch");}catch(IllegalStateException expected){require(expected.getMessage().contains("work/scratch budget"),"scratch bound diagnostic");}
        double[] wide=new double[4097];for(int n=0;n<wide.length;n++)wide[n]=(n+1)/4098D;var work=NativeFluidVolume.build(List.of(),NativeFluidVolume.SINGLE_SIDE,wide,wide,new double[0]);try{work.faces();throw new AssertionError("unbounded sweep work");}catch(IllegalStateException expected){require(expected.getMessage().contains("work/scratch budget"),"work bound diagnostic");}
        var stable=slab.faces();for(int n=0;n<50000;n++)require(slab.faces()==stable,"warm face cache identity");
        var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        // Warm the counter itself before attributing its first-use bookkeeping to cache reads.
        for(int n=0;n<1000;n++)meter.getThreadAllocatedBytes(thread);
        long[] samples=new long[3];long minimum=Long.MAX_VALUE;
        for(int sample=0;sample<samples.length;sample++){
            long before=meter.getThreadAllocatedBytes(thread);int consumed=0;
            for(int n=0;n<100000;n++)consumed+=slab.faces().size();
            samples[sample]=meter.getThreadAllocatedBytes(thread)-before;minimum=Math.min(minimum,samples[sample]);
            require(consumed==600000,"consumed cached faces");
        }
        require(minimum==0,"steady cached face reads allocated="+Arrays.toString(samples));
        System.out.println("Native fluid face cache PASS reads=100000 allocationSamples="+Arrays.toString(samples));
        System.out.println("Native fluid volume PASS cases="+cases+" Cartesian-points="+points+" large-runs="+large.cells().size()+" immutable/lazy/native boxes/retained+scratch+work bounds/cached failure");
    }
}
