package com.vandorlabs.blocks;

import net.minecraft.util.math.BlockPos;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded coplanar traversal. Eligibility is never queried for an unloaded cell. */
public final class LoadedPlaneConnections {
    public static final int LIMIT = 4096;
    private LoadedPlaneConnections() { }

    public static Set<BlockPos> collect(BlockPos start, PanelPlane plane,
            Predicate<BlockPos> loaded, Predicate<BlockPos> eligible) {
        return scan(start,plane,loaded,eligible,LIMIT).members;
    }
    public static final class Scan {
        public final Set<BlockPos> members;public final boolean complete;
        private Scan(Set<BlockPos> members,boolean complete){this.members=members;this.complete=complete;}
    }
    /** Same traversal, additionally certifying the loaded boundary for single-owner layouts. */
    public static Scan scan(BlockPos start,PanelPlane plane,Predicate<BlockPos> loaded,Predicate<BlockPos> eligible,int limit){
        com.vandorlabs.render.LoadedPlaneTraversal.Scan<BlockPos> result=com.vandorlabs.render.LoadedPlaneTraversal.scan(
                start,(current,direction)->current.offset(plane.neighbor(direction)),loaded,eligible,limit);
        return new Scan(result.members,result.complete);
    }
}
