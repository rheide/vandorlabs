package com.vandorlabs.neoforge.table;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Chunk availability changes neighbour-derived meshes even when no block-state packet changes. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TableChunkRefresh {
    private record Request(ClientLevel level,ChunkPos chunk){}
    private static final Set<Request> PENDING=new LinkedHashSet<>();
    private static boolean overflow;
    private static long invalidations;
    private TableChunkRefresh(){}
    private static synchronized void queue(ChunkEvent event){if(event.getLevel() instanceof ClientLevel level){if(PENDING.size()<4096)PENDING.add(new Request(level,event.getChunk().getPos()));else overflow=true;}}
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){queue(event);}
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event){queue(event);}
    private static synchronized Request take(){var it=PENDING.iterator();if(!it.hasNext())return null;Request next=it.next();it.remove();return next;}
    public static long invalidations(){return invalidations;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Table chunk refresh requires client thread");
        if(mc.level==null){synchronized(TableChunkRefresh.class){PENDING.clear();overflow=false;}return;}
        synchronized(TableChunkRefresh.class){if(overflow){overflow=false;PENDING.clear();mc.levelRenderer.allChanged();return;}}
        // Events can precede insertion/removal in the chunk cache; wait until the next client tick.
        // Palette checks skip sections without tables; no block/entity/world scan is needed.
        for(int budget=0;budget<16;budget++){Request request=take();if(request==null)return;if(request.level!=mc.level)continue;
            for(int[] offset:new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}}){int x=request.chunk.x+offset[0],z=request.chunk.z+offset[1];
                var chunk=mc.level.getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);if(chunk==null)continue;
                var sections=chunk.getSections();for(int i=0;i<sections.length;i++)if(sections[i].maybeHas(state->state.is(TableRegistration.BLOCK.get()))){mc.levelRenderer.setSectionDirty(x,mc.level.getSectionYFromSectionIndex(i),z);invalidations++;}
            }
        }
    }
}
