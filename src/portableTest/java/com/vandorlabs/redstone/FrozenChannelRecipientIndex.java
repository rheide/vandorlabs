package com.vandorlabs.redstone;

import java.util.*;
import com.vandorlabs.redstone.ChannelRecipientIndexTest.*;

/** Frozen 8cd8b77d7 recipient algorithm; only Minecraft positions/counters use fixture equivalents. */
final class FrozenChannelRecipientIndex {
    final Stats stats;
    FrozenChannelRecipientIndex(Stats stats){this.stats=stats;}
    private static <M> Set<M> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<M,Boolean>());}
    static int tier(int range){return range<=16?0:range<=64?1:2;}
    static int size(int tier){return tier==0?16:tier==1?64:256;}
    static long packed(int x,int y,int z){return ((long)x&67108863L)<<38|((long)y&4095L)<<26|((long)z&67108863L);}
    static long key(Position position,int size){return packed(Math.floorDiv(position.getX(),size),Math.floorDiv(position.getY(),size),Math.floorDiv(position.getZ(),size));}
    static boolean inside(Position source,Position target,int range){int sx=source.getX(),sy=source.getY(),sz=source.getZ(),tx=target.getX(),ty=target.getY(),tz=target.getZ();double dx=(double)sx-tx,dy=(double)sy-ty,dz=(double)sz-tz;return range==0||dx*dx+dy*dy+dz*dz<=(double)range*range;}
            final Map<Member,Position> positions=new IdentityHashMap<>();
            @SuppressWarnings("unchecked") final Map<Long,Set<Member>>[] grids=(Map<Long,Set<Member>>[])new Map<?,?>[]{new HashMap<Long,Set<Member>>(),new HashMap<Long,Set<Member>>(),new HashMap<Long,Set<Member>>()};
            void put(Member member,Position pos){remove(member);positions.put(member,pos.toImmutable());for(int t=0;t<3;t++)grids[t].computeIfAbsent(key(pos,size(t)),k->identitySet()).add(member);}
            void remove(Member member){Position pos=positions.remove(member);if(pos==null)return;for(int t=0;t<3;t++){long key=key(pos,size(t));Set<Member> set=grids[t].get(key);if(set!=null){set.remove(member);if(set.isEmpty())grids[t].remove(key);}}}
            Set<Member> find(Position pos,int range){Set<Member> result=identitySet();if(range==0){result.addAll(positions.keySet());return result;}if(range<0)return result;int t=tier(range),size=size(t),x=Math.floorDiv(pos.getX(),size),y=Math.floorDiv(pos.getY(),size),z=Math.floorDiv(pos.getZ(),size);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){stats.bucketVisited();Set<Member> set=grids[t].get(packed(x+dx,y+dy,z+dz));if(set!=null)for(Member member:set){stats.candidateVisited();if(inside(pos,positions.get(member),range))result.add(member);}}return result;}
}
