package com.vandorlabs.client;

import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Exact original-neighbour-scan oracle plus warmed CPU/allocation measurements. */
public final class GlassFrameDirtyRegionChecks {
    private static final class Owners extends HashMap<BlockPos,Object> {
        long probes;
        @Override public Object get(Object key){probes++;return super.get(key);}
    }
    private static final class Fixture {
        final Owners owners=new Owners();
        final Map<BlockPos,Boolean> sections=new HashMap<>();
        void put(BlockPos pos){owners.put(pos,new Object());sections.put(section(pos),Boolean.TRUE);}
        void rebuildSections(){sections.clear();for(BlockPos pos:owners.keySet())sections.put(section(pos),Boolean.TRUE);}
    }
    private static final class Sample {
        long nanos,bytes,probes;
    }
    private static BlockPos section(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);}
    private static Set<Object> identities(){return Collections.newSetFromMap(new IdentityHashMap<>());}
    private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void original(Map<BlockPos,Object> owners,Iterable<BlockPos> changed,Set<Object> affected){
        for(BlockPos pos:changed)for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            Object owner=owners.get(pos.add(x,y,z));if(owner!=null)affected.add(owner);
        }
    }
    private static void same(Fixture fixture,Iterable<BlockPos> changed,String label){
        Set<Object> expected=identities(),actual=identities();
        original(fixture.owners,changed,expected);
        GlassFrameDirtyRegions.collect(fixture.owners,fixture.sections,changed,actual);
        need(actual.equals(expected),label+" affected owner identities");
        for(BlockPos key:fixture.owners.keySet())need(!(key instanceof BlockPos.MutableBlockPos),"lookup cursor escaped into owner map");
        for(BlockPos key:fixture.sections.keySet())need(!(key instanceof BlockPos.MutableBlockPos),"lookup cursor escaped into section map");
    }
    private static void coverage(){
        Fixture fixture=new Fixture();
        int[] boundary={-33,-32,-31,-17,-16,-15,-1,0,1,15,16,17,31,32,33};
        List<BlockPos> changes=new ArrayList<>();
        for(int x:boundary)for(int y:boundary)for(int z:boundary){
            BlockPos pos=new BlockPos(x,y,z);fixture.put(pos);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)changes.add(pos.add(dx,dy,dz));
        }
        same(fixture,changes,"all positive/negative section faces, edges and corners");
        Random random=new Random(0x67a55L);List<BlockPos> scattered=new ArrayList<>();
        for(int i=0;i<4096;i++)scattered.add(new BlockPos(random.nextInt(256)-128,random.nextInt(256)-128,random.nextInt(256)-128));
        same(fixture,scattered,"scattered unrelated updates");
        BlockPos replaced=new BlockPos(-16,-16,-16);Object previous=fixture.owners.get(replaced);fixture.put(replaced);
        Set<Object> affected=identities();GlassFrameDirtyRegions.collect(fixture.owners,fixture.sections,Collections.singleton(replaced),affected);
        need(!affected.contains(previous)&&affected.contains(fixture.owners.get(replaced)),"same-position replacement uses new owner identity");
        fixture.owners.keySet().removeIf(p->p.getX()<0);fixture.rebuildSections();same(fixture,changes,"removed section membership");
        fixture.owners.clear();fixture.rebuildSections();same(fixture,changes,"world retirement");
    }
    private static Sample measure(Fixture fixture,List<BlockPos> changed,boolean optimized,Set<Object> affected){
        Sample sample=new Sample();fixture.owners.probes=0;
        long before=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
        for(int pass=0;pass<8;pass++){
            affected.clear();
            if(optimized)GlassFrameDirtyRegions.collect(fixture.owners,fixture.sections,changed,affected);
            else original(fixture.owners,changed,affected);
        }
        sample.nanos=System.nanoTime()-start;
        long after=BenchmarkAllocations.currentThreadBytes();sample.bytes=before<0||after<0?-1:after-before;
        sample.probes=fixture.owners.probes;return sample;
    }
    private static void benchmark(String label,Fixture fixture,List<BlockPos> changed){
        Set<Object> expected=identities(),actual=identities();
        for(int warm=0;warm<16;warm++){
            expected.clear();actual.clear();original(fixture.owners,changed,expected);
            GlassFrameDirtyRegions.collect(fixture.owners,fixture.sections,changed,actual);
        }
        BenchmarkAllocations.currentThreadBytes();
        Sample old=measure(fixture,changed,false,expected),revised=measure(fixture,changed,true,actual);
        need(expected.equals(actual),label+" measured output");
        need(revised.probes<=old.probes,label+" owner lookup budget");
        if(label.equals("far"))need(revised.probes==0,"unrelated section updates must not query owners");
        if(old.bytes>=0&&revised.bytes>=0)need(revised.bytes<=old.bytes+1024,label+" allocation regression");
        System.out.println("Glass frame dirty regions "+label+" updates="+changed.size()+" passes=8 oldProbes="+old.probes+" newProbes="+revised.probes+" oldBytes="+old.bytes+" newBytes="+revised.bytes+" oldNanos="+old.nanos+" newNanos="+revised.nanos);
    }
    public static void main(String[] args){
        coverage();Fixture fixture=new Fixture();List<BlockPos> near=new ArrayList<>(),far=new ArrayList<>(),mixed=new ArrayList<>();
        for(int i=0;i<4096;i++){
            BlockPos pos=new BlockPos(i&15,64+((i>>4)&15),(i>>8)&15);fixture.put(pos);
            near.add(pos);far.add(pos.add(4096,0,4096));mixed.add((i&15)==0?pos:pos.add(4096,0,4096));
        }
        benchmark("far",fixture,far);benchmark("mixed",fixture,mixed);benchmark("near",fixture,near);
        System.out.println("Glass frame dirty regions PASS exact neighbours/negative boundaries/replacement/removal/retirement/allocation");
    }
}
