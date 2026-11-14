package com.vandorlabs.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.tileentity.TileEntity;

/** Own only the tile-list removals made for this exact uploaded section. */
final class GlassPaneAdmissions {
    private final Set<TileEntity> removed=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());
    private CompiledChunk chunk;

    void prune(CompiledChunk current,Predicate<TileEntity> suppress,Predicate<TileEntity> installed){
        if(chunk!=current){restore(installed);chunk=current;}
        if(current==null||current==CompiledChunk.DUMMY)return;
        Iterator<TileEntity> prior=removed.iterator();
        while(prior.hasNext()){
            TileEntity tile=prior.next();
            if(!installed.test(tile)){prior.remove();continue;}
            if(!suppress.test(tile)){
                if(!current.getTileEntities().contains(tile))current.getTileEntities().add(tile);
                prior.remove();
            }
        }
        Iterator<TileEntity> visible=current.getTileEntities().iterator();
        while(visible.hasNext()){
            TileEntity tile=visible.next();
            if(installed.test(tile)&&suppress.test(tile)){removed.add(tile);visible.remove();}
        }
    }

    void restore(Predicate<TileEntity> installed){
        if(chunk!=null&&chunk!=CompiledChunk.DUMMY)for(TileEntity tile:removed)
            if(installed.test(tile)&&!chunk.getTileEntities().contains(tile))chunk.getTileEntities().add(tile);
        removed.clear();chunk=null;
    }
    int removedCount(){return removed.size();}
}
