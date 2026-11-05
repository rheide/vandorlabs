package com.vandorlabs.redstone;

import java.util.*;
import com.vandorlabs.redstone.ChannelSourceIndexTest.Member;
import com.vandorlabs.redstone.ChannelSourceIndexTest.Snapshot;

/** Frozen pre-extraction source index (c74a85221), only platform positions replaced by fixture scalar fields. */
final class FrozenChannelSourceIndex {
    static final class Stats {
        long sourceBuckets,sourceCandidates;
        final ArrayDeque<Bucket[]> queryBuffers=new ArrayDeque<>();
    }
    static final class Bucket {
        @SuppressWarnings("unchecked") final Set<Member>[] levels=(Set<Member>[])new Set<?>[16];int maximum;
        void add(Member member,int level){if(levels[level]==null)levels[level]=identitySet();levels[level].add(member);maximum=Math.max(maximum,level);}
        void remove(Member member,int level){Set<Member> set=levels[level];if(set!=null){set.remove(member);if(set.isEmpty())levels[level]=null;}while(maximum>0&&levels[maximum]==null)maximum--;}
    }
    final Stats stats;
    final Map<Member,Snapshot> all=new IdentityHashMap<>();final int[] strengths=new int[16];
    @SuppressWarnings("unchecked") final Map<Long,Bucket>[] grids=(Map<Long,Bucket>[])new Map<?,?>[]{new HashMap<Long,Bucket>(),new HashMap<Long,Bucket>(),new HashMap<Long,Bucket>()};
    final Bucket infinite=new Bucket();
    FrozenChannelSourceIndex(Stats stats){this.stats=stats;}
    private static Set<Member> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<Member,Boolean>());}
    static int tier(int range){return range<=16?0:range<=64?1:2;}
    static int size(int tier){return tier==0?16:tier==1?64:256;}
    static long packed(int x,int y,int z){return ((long)x&67108863L)<<38|((long)y&4095L)<<26|((long)z&67108863L);}
    static long key(Snapshot source,int size){return packed(Math.floorDiv(source.x,size),Math.floorDiv(source.y,size),Math.floorDiv(source.z,size));}
    static boolean inside(Snapshot source,int x,int y,int z){double dx=(double)source.x-x,dy=(double)source.y-y,dz=(double)source.z-z;return source.range==0||dx*dx+dy*dy+dz*dz<=(double)source.range*source.range;}
    void put(Member member,Snapshot source){remove(member);all.put(member,source);strengths[source.level]++;if(source.range==0)infinite.add(member,source.level);else {int t=tier(source.range);grids[t].computeIfAbsent(key(source,size(t)),unused->new Bucket()).add(member,source.level);}}
    void remove(Member member){Snapshot source=all.remove(member);if(source==null)return;strengths[source.level]--;if(source.range==0)infinite.remove(member,source.level);else {int t=tier(source.range);long key=key(source,size(t));Bucket bucket=grids[t].get(key);if(bucket!=null){bucket.remove(member,source.level);if(bucket.maximum==0)grids[t].remove(key);}}}
    int at(int queryX,int queryY,int queryZ){
        Bucket[] candidates=stats.queryBuffers.pollFirst();if(candidates==null)candidates=new Bucket[82];int count=0,ceiling=0;
        try{
            if(infinite.maximum>0){candidates[count++]=infinite;ceiling=infinite.maximum;}
            for(int t=0;t<3;t++){int size=size(t),x=Math.floorDiv(queryX,size),y=Math.floorDiv(queryY,size),z=Math.floorDiv(queryZ,size);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){stats.sourceBuckets++;Bucket bucket=grids[t].get(packed(x+dx,y+dy,z+dz));if(bucket!=null){candidates[count++]=bucket;ceiling=Math.max(ceiling,bucket.maximum);}}}
            for(int level=ceiling;level>0;level--)for(int i=0;i<count;i++){Bucket bucket=candidates[i];if(bucket.maximum<level)continue;Set<Member> sources=bucket.levels[level];if(sources==null)continue;for(Member member:sources){stats.sourceCandidates++;Snapshot source=all.get(member);if(source!=null&&member.active()&&inside(source,queryX,queryY,queryZ))return level;}}
            return 0;
        }finally{Arrays.fill(candidates,null);if(stats.queryBuffers.size()<8)stats.queryBuffers.addFirst(candidates);}
    }
    int maximum(){for(int level=15;level>0;level--)if(strengths[level]>0)return level;return 0;}
}
