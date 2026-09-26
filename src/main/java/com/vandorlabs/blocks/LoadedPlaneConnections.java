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
        Set<BlockPos> members = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int i=0; i<4; i++) {
                BlockPos next = current.offset(plane.neighbor(i));
                if (members.contains(next) || !loaded.test(next) || !eligible.test(next)) continue;
                members.add(next);
                if (members.size() >= LIMIT) return members;
                queue.addLast(next);
            }
        }
        return members;
    }
}
