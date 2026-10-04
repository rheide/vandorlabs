package com.vandorlabs.client;

import net.minecraft.util.math.*;
import java.util.*;

/** Released candidate traversal and HashSet iteration order. */
final class ReferenceTrapdoorRayCandidates {
    static List<BlockPos> positions(Vec3d start,Vec3d end) {
        Set<BlockPos> owners=new HashSet<>();
        int[] cell={MathHelper.floor(start.x),MathHelper.floor(start.y),MathHelper.floor(start.z)};
        int[] last={MathHelper.floor(end.x),MathHelper.floor(end.y),MathHelper.floor(end.z)};
        double[] origin={start.x,start.y,start.z},delta={end.x-start.x,end.y-start.y,end.z-start.z},next=new double[3],step=new double[3];
        for(int axis=0;axis<3;axis++) {
            next[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:((delta[axis]>0?cell[axis]+1:cell[axis])-origin[axis])/delta[axis];
            step[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:Math.abs(1/delta[axis]);
        }
        int limit=Math.abs(cell[0]-last[0])+Math.abs(cell[1]-last[1])+Math.abs(cell[2]-last[2])+3;
        for(int i=0;i<limit;i++) {
            // Lifted diagonal leaves can cross both horizontal cell boundaries.
            for(int dy=-1;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                owners.add(new BlockPos(cell[0]+dx,cell[1]+dy,cell[2]+dz));
            if(Arrays.equals(cell,last))break;
            int axis=next[0]<=next[1] && next[0]<=next[2]?0:next[1]<=next[2]?1:2;
            cell[axis]+=delta[axis]>0?1:-1;next[axis]+=step[axis];
        }
        return new ArrayList<>(owners);
    }
}
