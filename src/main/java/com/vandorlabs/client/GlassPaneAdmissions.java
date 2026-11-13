package com.vandorlabs.client;

import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.tileentity.TileEntity;
import java.util.*;
import java.util.function.Predicate;

/** Own removals from one exact uploaded chunk's tile list, never from its replacement. */
final class GlassPaneAdmissions {
    private CompiledChunk source;
    private final Set<TileEntity> removed=Collections.newSetFromMap(new IdentityHashMap<>());

    void prune(CompiledChunk current,Predicate<TileEntity> suppress,Predicate<TileEntity> installed){
        if(current!=source){restore(installed);source=current;}
        if(current==null||current==CompiledChunk.DUMMY)return;
        List<TileEntity> tiles=current.getTileEntities();
        // A frame publication can become unavailable without changing pane geometry.
        // Restore those fallbacks before considering new removals.
        Iterator<TileEntity> held=removed.iterator();
        while(held.hasNext()){
            TileEntity tile=held.next();
            if(!installed.test(tile)){held.remove();continue;}
            if(!suppress.test(tile)){if(!tiles.contains(tile))tiles.add(tile);held.remove();}
        }
        Iterator<TileEntity> it=tiles.iterator();
        while(it.hasNext()){
            TileEntity tile=it.next();
            if(suppress.test(tile)){removed.add(tile);it.remove();}
        }
    }

    void restore(Predicate<TileEntity> installed){
        if(source!=null&&source!=CompiledChunk.DUMMY){
            List<TileEntity> tiles=source.getTileEntities();
            for(TileEntity tile:removed)if(installed.test(tile)&&!tiles.contains(tile))tiles.add(tile);
        }
        removed.clear();source=null;
    }

    int removedCount(){return removed.size();}
}
