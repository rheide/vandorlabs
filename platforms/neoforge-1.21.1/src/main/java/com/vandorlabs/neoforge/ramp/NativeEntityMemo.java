package com.vandorlabs.neoforge.ramp;

import java.lang.ref.*;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Bounded weak identity entries; warmed lookups allocate no temporary weak references. */
final class NativeEntityMemo<V> {
    private static final class Key extends WeakReference<Entity> {
        final int hash;Key(Entity entity,ReferenceQueue<Entity> queue){super(entity,queue);hash=System.identityHashCode(entity);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){if(this==value)return true;return value instanceof Key key&&get()!=null&&get()==key.get();}
    }
    private static final class Lookup {Entity entity;@Override public int hashCode(){return System.identityHashCode(entity);}@Override public boolean equals(Object value){return value instanceof Key key&&entity==key.get();}}
    private final int limit;private final ReferenceQueue<Entity> queue=new ReferenceQueue<>();private final Lookup lookup=new Lookup();private Map<Object,V> rows=new LinkedHashMap<>(16,.75F,true);
    NativeEntityMemo(int limit){this.limit=limit;}
    private void drain(){for(var ref=queue.poll();ref!=null;ref=queue.poll())rows.remove(ref);}
    synchronized V get(Entity entity){drain();lookup.entity=entity;try{return rows.get(lookup);}finally{lookup.entity=null;}}
    synchronized void put(Entity entity,V value){drain();rows.put(new Key(entity,queue),value);while(rows.size()>limit){var iterator=rows.keySet().iterator();iterator.next();iterator.remove();}}
    synchronized void clearWorld(Level level){drain();rows.keySet().removeIf(key->{var entity=((Key)key).get();return entity==null||entity.level()==level;});}
    synchronized int size(){drain();return rows.size();}
}
