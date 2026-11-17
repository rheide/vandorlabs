package com.vandorlabs.navigation;

import java.util.*;

/** Immutable seed colors in globally aligned 32x32 squares. No time-based expiry. */
public final class NavigationTerrainCache {
    public interface Sampler {int sample(int x,int z);}
    public static final int SIDE=32, TILE_BYTES=4608;
    private static final class Context {final Map<Integer,Level> levels=new HashMap<>();}
    private static final class Level {final Map<Long,Tile> tiles=new HashMap<>();Tile hot;}
    private static final class Tile {
        final Level level;final long key;final int x,z;final int[] colors=new int[SIDE*SIDE];final long[] known=new long[SIDE*SIDE/64];boolean retained=true;
        Tile(Level level,long key,int x,int z){this.level=level;this.key=key;this.x=x;this.z=z;}
    }
    private final int budget;private int bytes;
    private final IdentityHashMap<Object,Context> contexts=new IdentityHashMap<>();
    private final LinkedHashMap<Tile,Boolean> lru=new LinkedHashMap<Tile,Boolean>(64,.75f,true);
    public long samples,hits,evictions;
    public NavigationTerrainCache(int budget){if(budget<TILE_BYTES)throw new IllegalArgumentException("Terrain cache budget");this.budget=budget;}
    public int retainedBytes(){return bytes;}
    public int sample(Object generator,int x,int z,int step,Sampler sampler){
        if(step<1||step>64||(step&(step-1))!=0)throw new IllegalArgumentException("Terrain sample step");
        Context context=contexts.get(generator);if(context==null){context=new Context();contexts.put(generator,context);}
        Level level=context.levels.get(step);if(level==null){level=new Level();context.levels.put(step,level);}
        int qx=Math.floorDiv(x,step),qz=Math.floorDiv(z,step),tx=qx>>5,tz=qz>>5;Tile tile=level.hot;
        if(tile==null||!tile.retained||tile.x!=tx||tile.z!=tz){long key=((long)tx<<32)^(tz&0xffffffffL);tile=level.tiles.get(key);if(tile==null){tile=new Tile(level,key,tx,tz);level.tiles.put(key,tile);lru.put(tile,Boolean.TRUE);bytes+=TILE_BYTES;trim();}else lru.get(tile);level.hot=tile;}
        int index=(qz&31)*SIDE+(qx&31),word=index>>>6;long bit=1L<<(index&63);
        if((tile.known[word]&bit)!=0){hits++;return tile.colors[index];}
        samples++;int color=sampler.sample(qx*step,qz*step);if(color!=NavigationMap.ERROR){tile.colors[index]=color;tile.known[word]|=bit;}return color;
    }
    private void trim(){Iterator<Tile> it=lru.keySet().iterator();while(bytes>budget&&it.hasNext()){Tile t=it.next();it.remove();t.retained=false;t.level.tiles.remove(t.key);if(t.level.hot==t)t.level.hot=null;bytes-=TILE_BYTES;evictions++;}}
    public void invalidate(Object generator){Context c=contexts.remove(generator);if(c==null)return;for(Level level:c.levels.values()){for(Tile tile:level.tiles.values()){if(lru.remove(tile)!=null)bytes-=TILE_BYTES;tile.retained=false;}level.tiles.clear();level.hot=null;}}
    public void clear(){lru.clear();contexts.clear();bytes=0;}
}
