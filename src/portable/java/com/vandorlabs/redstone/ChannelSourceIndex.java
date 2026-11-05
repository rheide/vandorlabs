package com.vandorlabs.redstone;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Exact source-strength/spatial lookup. The server-thread host owns immutable publication and liveness. */
public class ChannelSourceIndex<M,S extends ChannelSourceIndex.SourceSnapshot> {
    /** Adapters retain their original snapshot object; the index does not copy or wrap it. */
    public interface SourceSnapshot {
        int x();int y();int z();int range();int level();
    }
    /** One context per network, shared by every channel index, including reentrant queries. */
    public abstract static class QueryContext<M> {
        public long sourceBuckets,sourceCandidates;
        private final ArrayDeque<Bucket<M>[]> queryBuffers=new ArrayDeque<>();
        protected abstract boolean active(M member);
    }
    private static final class Bucket<M> {
        @SuppressWarnings("unchecked")
        final Set<M>[] levels=(Set<M>[])new Set<?>[16];
        int maximum;
        void add(M member,int level){if(levels[level]==null)levels[level]=identitySet();levels[level].add(member);maximum=Math.max(maximum,level);}
        void remove(M member,int level){Set<M> set=levels[level];if(set!=null){set.remove(member);if(set.isEmpty())levels[level]=null;}while(maximum>0&&levels[maximum]==null)maximum--;}
    }
    private final QueryContext<M> stats;
    private final Map<M,S> all=new IdentityHashMap<>();
    private final int[] strengths=new int[16];
    private final Map<Long,Bucket<M>>[] grids=grids();
    private final Bucket<M> infinite=new Bucket<>();

    public ChannelSourceIndex(QueryContext<M> stats){this.stats=stats;}
    @SuppressWarnings("unchecked")
    private static <M> Map<Long,Bucket<M>>[] grids(){return (Map<Long,Bucket<M>>[])new Map<?,?>[]{new HashMap<Long,Bucket<M>>(),new HashMap<Long,Bucket<M>>(),new HashMap<Long,Bucket<M>>()};}
    private static <M> Set<M> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<M,Boolean>());}
    private static long key(SourceSnapshot source,int size){return ChannelSpatialPolicy.key(source.x(),source.y(),source.z(),size);}
    private static boolean inside(SourceSnapshot source,int x,int y,int z){int range=source.range();return range==0||ChannelSpatialPolicy.finiteContains(source.x(),source.y(),source.z(),x,y,z,range);}
    public final S source(M member){return all.get(member);}
    public final boolean isEmpty(){return all.isEmpty();}
    /**
     * Server-thread publication. The host supplies a nonnull member and immutable snapshot,
     * a supported nonnegative range (zero is unbounded), and strength 1..15.
     * These are inherited host preconditions, deliberately not additional validation.
     */
    public final void put(M member,S source){
        remove(member);all.put(member,source);int level=source.level(),range=source.range();strengths[level]++;
        if(range==0)infinite.add(member,level);
        else {int tier=ChannelSpatialPolicy.tier(range);grids[tier].computeIfAbsent(key(source,ChannelSpatialPolicy.cellSize(tier)),unused->new Bucket<>()).add(member,level);}
    }
    public final void remove(M member){
        S source=all.remove(member);if(source==null)return;int level=source.level(),range=source.range();strengths[level]--;
        if(range==0)infinite.remove(member,level);
        else {int tier=ChannelSpatialPolicy.tier(range);long key=key(source,ChannelSpatialPolicy.cellSize(tier));Bucket<M> bucket=grids[tier].get(key);if(bucket!=null){bucket.remove(member,level);if(bucket.maximum==0)grids[tier].remove(key);}}
    }
    public final int at(int queryX,int queryY,int queryZ){
        Bucket<M>[] candidates=stats.queryBuffers.pollFirst();
        if(candidates==null){@SuppressWarnings("unchecked") Bucket<M>[] created=(Bucket<M>[])new Bucket<?>[82];candidates=created;}
        int count=0,ceiling=0;
        try{
            if(infinite.maximum>0){candidates[count++]=infinite;ceiling=infinite.maximum;}
            for(int tier=0;tier<3;tier++){
                int size=ChannelSpatialPolicy.cellSize(tier),x=Math.floorDiv(queryX,size),y=Math.floorDiv(queryY,size),z=Math.floorDiv(queryZ,size);
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                    stats.sourceBuckets++;Bucket<M> bucket=grids[tier].get(ChannelSpatialPolicy.packedCell(x+dx,y+dy,z+dz));
                    if(bucket!=null){candidates[count++]=bucket;ceiling=Math.max(ceiling,bucket.maximum);}
                }
            }
            for(int level=ceiling;level>0;level--)for(int i=0;i<count;i++){
                Bucket<M> bucket=candidates[i];if(bucket.maximum<level)continue;Set<M> sources=bucket.levels[level];if(sources==null)continue;
                for(M member:sources){
                    stats.sourceCandidates++;S source=all.get(member);
                    // The activity hook may change membership: retain this captured snapshot exactly as before.
                    if(source!=null&&stats.active(member)&&inside(source,queryX,queryY,queryZ))return level;
                }
            }
            return 0;
        }finally{Arrays.fill(candidates,null);if(stats.queryBuffers.size()<8)stats.queryBuffers.addFirst(candidates);}
    }
    public final int maximum(){for(int level=15;level>0;level--)if(strengths[level]>0)return level;return 0;}
}
