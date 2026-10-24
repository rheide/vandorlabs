package com.vandorlabs.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Bounded position cache invalidated by changes within one neighbouring cell. */
public abstract class NeighbourhoodCache<V> extends WorldListener {
    protected final Map<BlockPos,V> entries;
    protected NeighbourhoodCache(final int limit){
        if(limit<1)throw new IllegalArgumentException("Positive cache limit required");
        entries=new LinkedHashMap<BlockPos,V>(64,.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<BlockPos,V> entry){return size()>limit;}
        };
    }
    public final void invalidate(int x0,int y0,int z0,int x1,int y1,int z1){
        if(x1-x0<=4&&y1-y0<=4&&z1-z0<=4){
            BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
            for(int x=x0-1;x<=x1+1;x++)for(int y=y0-1;y<=y1+1;y++)for(int z=z0-1;z<=z1+1;z++)entries.remove(at.setPos(x,y,z));
        }else entries.keySet().removeIf(p->p.getX()>=x0-1&&p.getX()<=x1+1&&p.getY()>=y0-1&&p.getY()<=y1+1&&p.getZ()>=z0-1&&p.getZ()<=z1+1);
    }
    @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){invalidate(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());}
    @Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){invalidate(Math.min(x0,x1),Math.min(y0,y1),Math.min(z0,z1),Math.max(x0,x1),Math.max(y0,y1),Math.max(z0,z1));}
}
