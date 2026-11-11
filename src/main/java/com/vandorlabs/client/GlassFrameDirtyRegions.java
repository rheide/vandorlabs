package com.vandorlabs.client;

import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

/** Finds loaded frame owners affected by a batch of neighbour updates. */
final class GlassFrameDirtyRegions {
    static <T> void collect(Map<BlockPos,T> owners,Map<BlockPos,?> sections,
                            Iterable<BlockPos> changed,Set<T> affected) {
        if(owners.isEmpty())return;
        // Lookup only: this mutable position must never become a retained map key.
        BlockPos.MutableBlockPos lookup=new BlockPos.MutableBlockPos();
        for(BlockPos pos:changed){
            if(!nearSection(sections,pos,lookup))continue;
            int px=pos.getX(),py=pos.getY(),pz=pos.getZ();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                T owner=owners.get(lookup.setPos(px+x,py+y,pz+z));
                if(owner!=null)affected.add(owner);
            }
        }
    }

    private static boolean nearSection(Map<BlockPos,?> sections,BlockPos pos,BlockPos.MutableBlockPos lookup){
        int x0=(pos.getX()-1)&~15,x1=(pos.getX()+1)&~15;
        int y0=(pos.getY()-1)&~15,y1=(pos.getY()+1)&~15;
        int z0=(pos.getZ()-1)&~15,z1=(pos.getZ()+1)&~15;
        // At a section corner the 3x3x3 neighbourhood can touch eight sections.
        for(int x=0;x<(x0==x1?1:2);x++)for(int y=0;y<(y0==y1?1:2);y++)for(int z=0;z<(z0==z1?1:2);z++)
            if(sections.containsKey(lookup.setPos(x==0?x0:x1,y==0?y0:y1,z==0?z0:z1)))return true;
        return false;
    }

    private GlassFrameDirtyRegions() { }
}
