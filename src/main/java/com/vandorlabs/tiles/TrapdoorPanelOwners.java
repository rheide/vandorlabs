package com.vandorlabs.tiles;

import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Loaded owner positions by chunk; moved panels never require scanning or loading distant chunks. */
public final class TrapdoorPanelOwners {
    private static final Map<World,Map<Long,Set<BlockPos>>> WORLDS=new WeakHashMap<>();
    private TrapdoorPanelOwners(){}
    public static synchronized void refresh(TileEntityProgrammableTrapdoor tile) {
        remove(tile);World world=tile.getWorld();if(world==null || !tile.hasPanelMotion())return;
        long key=ChunkPos.asLong(tile.getPos().getX()>>4,tile.getPos().getZ()>>4);
        WORLDS.computeIfAbsent(world,w->new HashMap<>()).computeIfAbsent(key,k->new HashSet<>()).add(tile.getPos().toImmutable());
    }
    public static synchronized void remove(TileEntityProgrammableTrapdoor tile) {
        Map<Long,Set<BlockPos>> chunks=WORLDS.get(tile.getWorld());if(chunks==null)return;
        long key=ChunkPos.asLong(tile.getPos().getX()>>4,tile.getPos().getZ()>>4);Set<BlockPos> positions=chunks.get(key);
        if(positions!=null){positions.remove(tile.getPos());if(positions.isEmpty())chunks.remove(key);}if(chunks.isEmpty())WORLDS.remove(tile.getWorld());
    }
    public static synchronized List<BlockPos> candidates(World world,AxisAlignedBB area) {
        List<BlockPos> result=new ArrayList<>();Map<Long,Set<BlockPos>> chunks=WORLDS.get(world);if(chunks==null)return result;
        int x0=MathHelper.floor(area.minX-9)>>4,x1=MathHelper.floor(area.maxX+9)>>4,z0=MathHelper.floor(area.minZ-9)>>4,z1=MathHelper.floor(area.maxZ+9)>>4;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){Set<BlockPos> positions=chunks.get(ChunkPos.asLong(x,z));if(positions!=null)for(BlockPos pos:positions)if(pos.getY()>=area.minY-9 && pos.getY()<=area.maxY+9)result.add(pos);}
        return result;
    }
}
