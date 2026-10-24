package com.vandorlabs.blocks;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
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
        Set<BlockPos> members = new LinkedHashSet<>();
        boolean complete=true;
        Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int i=0; i<4; i++) {
                BlockPos next = current.offset(plane.neighbor(i));
                if(members.contains(next))continue;
                if(!loaded.test(next)){complete=false;continue;}
                if(!eligible.test(next))continue;
                members.add(next);
                if (members.size() >= limit) return new Scan(members,false);
                queue.addLast(next);
            }
        }
        return new Scan(members,complete);
    }
}
