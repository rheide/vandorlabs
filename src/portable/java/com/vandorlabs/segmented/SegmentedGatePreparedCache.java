package com.vandorlabs.segmented;

import java.lang.ref.WeakReference;
import java.util.*;

/** Client-main-thread only; values must be immutable and world-free; owner/revision identities are weak. */
public final class SegmentedGatePreparedCache<K,V> {
    private static final int ENTRY_BYTES=384;
    private static class Position {
        int x,y,z;
        Position(int x,int y,int z){set(x,y,z);}void set(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public int hashCode(){int h=x*73428767^z*912931;return h*31+y;}
        public boolean equals(Object other){if(!(other instanceof Position))return false;Position p=(Position)other;return x==p.x&&y==p.y&&z==p.z;}
    }
    private static final class Entry<K,V> {
        final WeakReference<K> owner;final WeakReference<Object> revision;final V value;final int bytes;boolean valid=true;
        Entry(K owner,Object revision,V value,int bytes){this.owner=new WeakReference<>(owner);this.revision=new WeakReference<>(revision);this.value=value;this.bytes=bytes;}
    }
    private final int budget,limit,baseBytes;
    private final LinkedHashMap<Position,Entry<K,V>> entries=new LinkedHashMap<>(64,.75F,true);
    private final Position lookup=new Position(0,0,0);
    private int retained;
    public SegmentedGatePreparedCache(int budget,int limit){if(limit<1||limit>8192)throw new IllegalArgumentException("Gate prepared cache capacity");int capacity=64;while(capacity*.75<limit)capacity*=2;baseBytes=512+capacity*8;if(budget<baseBytes+ENTRY_BYTES)throw new IllegalArgumentException("Gate prepared cache byte capacity");this.budget=budget;this.limit=limit;}
    public V get(K owner,int x,int y,int z,Object revision){lookup.set(x,y,z);Entry<K,V> entry=entries.get(lookup);return entry!=null&&entry.valid&&entry.owner.get()==owner&&entry.revision.get()==revision?entry.value:null;}
    public void put(K owner,int x,int y,int z,Object revision,V value,int valueBytes){
        if(owner==null||value==null||valueBytes<0||valueBytes>budget-baseBytes-ENTRY_BYTES)throw new IllegalArgumentException("Gate prepared value budget");lookup.set(x,y,z);Entry<K,V> old=entries.remove(lookup);if(old!=null)retained-=old.bytes;int bytes=ENTRY_BYTES+valueBytes;
        while(!entries.isEmpty()&&((long)baseBytes+retained+bytes>budget||entries.size()>=limit)){Iterator<Entry<K,V>> it=entries.values().iterator();retained-=it.next().bytes;it.remove();}
        entries.put(new Position(x,y,z),new Entry<>(owner,revision,value,bytes));retained+=bytes;
    }
    /** Invalidate only possible column roots containing this cell or a direct horizontal neighbour. No world reads. */
    public void changed(int x,int y,int z){
        for(int direction=0;direction<5;direction++)for(int up=0;up<32;up++){lookup.set(x+(direction==1?1:direction==2?-1:0),y+up,z+(direction==3?1:direction==4?-1:0));Entry<K,V> entry=entries.get(lookup);if(entry!=null)entry.valid=false;}
    }
    public int retainedBytes(){return baseBytes+retained;}public int size(){return entries.size();}
    public void clear(){entries.clear();retained=0;}
}
