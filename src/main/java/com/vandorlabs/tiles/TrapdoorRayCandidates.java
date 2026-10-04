package com.vandorlabs.tiles;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.util.math.*;
import java.util.*;

/** Candidate coordinates only: every actual hit test still reads the current world. */
public final class TrapdoorRayCandidates {
    private static final Cache<Path,List<BlockPos>> PATHS=CacheBuilder.newBuilder().maximumWeight(32768)
            .weigher((Path key,List<BlockPos> positions)->positions.size()).build();
    private TrapdoorRayCandidates() { }

    public static List<BlockPos> positions(Vec3d start,Vec3d end) {
        int[] cell={MathHelper.floor(start.x),MathHelper.floor(start.y),MathHelper.floor(start.z)};
        int[] last={MathHelper.floor(end.x),MathHelper.floor(end.y),MathHelper.floor(end.z)};
        double[] origin={start.x,start.y,start.z},delta={end.x-start.x,end.y-start.y,end.z-start.z},next=new double[3],step=new double[3];
        for(int axis=0;axis<3;axis++) {
            next[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:((delta[axis]>0?cell[axis]+1:cell[axis])-origin[axis])/delta[axis];
            step[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:Math.abs(1/delta[axis]);
        }
        int limit=Math.abs(cell[0]-last[0])+Math.abs(cell[1]-last[1])+Math.abs(cell[2]-last[2])+3;
        if(limit<=0)return Collections.emptyList();
        int[] cells=new int[limit*3];int count=0;
        for(int i=0;i<limit;i++) {
            cells[count++]=cell[0];cells[count++]=cell[1];cells[count++]=cell[2];
            if(Arrays.equals(cell,last))break;
            int axis=next[0]<=next[1] && next[0]<=next[2]?0:next[1]<=next[2]?1:2;
            cell[axis]+=delta[axis]>0?1:-1;next[axis]+=step[axis];
        }
        Path path=new Path(cells,count);List<BlockPos> positions=PATHS.getIfPresent(path);
        if(positions==null) {
            Set<BlockPos> owners=new HashSet<>();
            for(int i=0;i<count;i+=3)
                for(int dy=-1;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                    owners.add(new BlockPos(cells[i]+dx,cells[i+1]+dy,cells[i+2]+dz));
            // Preserve the old iteration order, including equal-distance hit precedence.
            positions=Collections.unmodifiableList(new ArrayList<>(owners));
            List<BlockPos> raced=PATHS.asMap().putIfAbsent(path,positions);
            if(raced!=null)positions=raced;
        }
        return positions;
    }
    private static final class Path {
        final int[] cells;
        final int count,hash;
        Path(int[] cells,int count){this.cells=cells;this.count=count;hash=Arrays.hashCode(cells)*31+count;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            if(!(other instanceof Path))return false;
            Path path=(Path)other;return count==path.count && Arrays.equals(cells,path.cells);
        }
    }
}
