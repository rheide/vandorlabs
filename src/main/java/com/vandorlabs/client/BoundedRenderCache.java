package com.vandorlabs.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Access-ordered render data with both an entry limit and a retained-byte limit. */
final class BoundedRenderCache<K,V> {
    private final int entryLimit;
    private final long byteLimit;
    private final Map<K,Entry<V>> entries = new LinkedHashMap<>(16,.75F,true);
    private long bytes;
    BoundedRenderCache(int entryLimit,long byteLimit) {
        if(entryLimit<1 || byteLimit<1)throw new IllegalArgumentException("Invalid render cache budget");
        this.entryLimit=entryLimit;this.byteLimit=byteLimit;
    }
    synchronized V get(K key) {Entry<V> entry=entries.get(key);return entry==null?null:entry.value;}
    synchronized void put(K key,V value,long retainedBytes) {
        if(retainedBytes<0)throw new IllegalArgumentException("Negative retained size");
        Entry<V> previous=entries.remove(key);
        if(previous!=null)bytes-=previous.bytes;
        // Oversized variants are still usable, but must not evict the useful working set.
        if(retainedBytes>byteLimit)return;
        while(entries.size()>=entryLimit || bytes>byteLimit-retainedBytes) {
            Iterator<Entry<V>> oldest=entries.values().iterator();
            bytes-=oldest.next().bytes;oldest.remove();
        }
        entries.put(key,new Entry<>(value,retainedBytes));bytes+=retainedBytes;
    }
    synchronized void clear(){entries.clear();bytes=0;}
    synchronized long retainedBytes(){return bytes;}
    synchronized int size(){return entries.size();}
    private static final class Entry<V> {
        final V value;final long bytes;
        Entry(V value,long bytes){this.value=value;this.bytes=bytes;}
    }
}
