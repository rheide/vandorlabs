package com.vandorlabs.redstone;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Server-thread recipient lookup; the host retains lifecycle and delivery decisions. */
public abstract class ChannelRecipientIndex<M,P> {
    /** Implemented by the existing network counter owner, without a separate adapter object. */
    public interface Counters {void bucketVisited();void candidateVisited();}
    private final Counters stats;
    private final Map<M,P> positions=new IdentityHashMap<>();
    private final Map<Long,Set<M>>[] grids=grids();
    protected ChannelRecipientIndex(Counters stats){this.stats=stats;}
    /** Retain immutable input directly, or capture mutable input once; never wrap a retained snapshot. */
    protected abstract P snapshot(P position);
    protected abstract int x(P position);
    protected abstract int y(P position);
    protected abstract int z(P position);
    @SuppressWarnings("unchecked")
    private static <M> Map<Long,Set<M>>[] grids(){return (Map<Long,Set<M>>[])new Map<?,?>[]{new HashMap<Long,Set<M>>(),new HashMap<Long,Set<M>>(),new HashMap<Long,Set<M>>()};}
    private static <M> Set<M> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<M,Boolean>());}
    private long key(P position,int size){return ChannelSpatialPolicy.key(x(position),y(position),z(position),size);}
    public final P position(M member){return positions.get(member);}
    public final boolean isEmpty(){return positions.isEmpty();}
    /** Host supplies a nonnull member/position on its server thread. */
    public final void put(M member,P position){
        remove(member);positions.put(member,snapshot(position));
        for(int tier=0;tier<3;tier++)grids[tier].computeIfAbsent(key(position,ChannelSpatialPolicy.cellSize(tier)),unused->identitySet()).add(member);
    }
    public final void remove(M member){
        P position=positions.remove(member);if(position==null)return;
        for(int tier=0;tier<3;tier++){
            long key=key(position,ChannelSpatialPolicy.cellSize(tier));Set<M> set=grids[tier].get(key);
            if(set!=null){set.remove(member);if(set.isEmpty())grids[tier].remove(key);}
        }
    }
    /** Zero selects all identities; negative selects none. Both accept null without reading coordinates. */
    public final Set<M> find(P position,int range){
        Set<M> result=identitySet();if(range==0){result.addAll(positions.keySet());return result;}if(range<0)return result;
        int tier=ChannelSpatialPolicy.tier(range),size=ChannelSpatialPolicy.cellSize(tier),cellX=Math.floorDiv(x(position),size),cellY=Math.floorDiv(y(position),size),cellZ=Math.floorDiv(z(position),size);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            stats.bucketVisited();Set<M> set=grids[tier].get(ChannelSpatialPolicy.packedCell(cellX+dx,cellY+dy,cellZ+dz));
            if(set!=null)for(M member:set){
                stats.candidateVisited();P target=positions.get(member);
                // Read the original query for each sphere test, preserving the legacy callback order.
                if(ChannelSpatialPolicy.finiteContains(x(position),y(position),z(position),x(target),y(target),z(target),range))result.add(member);
            }
        }
        return result;
    }
}
