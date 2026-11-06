package com.vandorlabs.neoforge.shape;

import com.vandorlabs.surface.JoinedRectangles;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** One bounded plane traversal for artwork and aperture queries. No window slices are constructed. */
final class NativePanelGroups {
    static final int LIMIT = 4096;
    enum Mode {
        MATERIAL, APERTURE, GLASS;
        boolean sameSettings(int mainA, int layoutA, boolean joinA, int shapeA,
                             int mainB, int layoutB, boolean joinB, int shapeB) {
            return this == MATERIAL ? mainA == mainB && layoutA == layoutB
                    : joinA && joinB && shapeA == shapeB;
        }
    }

    /** Members are temporary lookup results; only layouts escape into a host-owned group. */
    record Capture<T>(Map<BlockPos,T> members, Map<Long,NativePanelLayout> layouts, boolean capped) {}

    static <T> Capture<T> capture(BlockPos origin, Direction right, T root,
                                  Function<BlockPos,T> compatible) {
        Map<BlockPos,T> members = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.put(origin, root);
        queue.add(origin);
        Direction[] steps = {right, right.getOpposite(), Direction.UP, Direction.DOWN};
        while (!queue.isEmpty() && members.size() < LIMIT) {
            BlockPos pos = queue.remove();
            for (Direction direction : steps) {
                BlockPos next = pos.relative(direction);
                if (members.containsKey(next)) continue;
                T other = compatible.apply(next);
                if (other == null) continue;
                members.put(next, other);
                queue.add(next);
                if (members.size() >= LIMIT) break;
            }
        }
        boolean capped = members.size() >= LIMIT;
        return new Capture<>(members, partition(members.keySet(),right,Direction.UP,capped,pos->false,pos->false), capped);
    }
    static Map<Long,NativePanelLayout> partition(Set<BlockPos> members,Direction right,Direction up,boolean capped,
                                                Predicate<BlockPos> flipU,Predicate<BlockPos> flipV) {
        Map<Long,NativePanelLayout> layouts=new HashMap<>();
        if(capped){for(BlockPos pos:members)layouts.put(pos.asLong(),NativePanelLayout.UNIT);}
        else {
            Map<Long,BlockPos> cells=new HashMap<>();
            for(BlockPos pos:members)cells.put(JoinedRectangles.cell(axis(pos,right),axis(pos,up)),pos);
            for(var rect:JoinedRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++){
                BlockPos pos=cells.get(JoinedRectangles.cell(rect.x+x,rect.y+y));
                layouts.put(pos.asLong(),new NativePanelLayout(x,y,rect.width,rect.height,flipU.test(pos),flipV.test(pos)));
            }
        }
        return Map.copyOf(layouts);
    }
    private static int axis(BlockPos pos,Direction direction){return pos.getX()*direction.getStepX()+pos.getY()*direction.getStepY()+pos.getZ()*direction.getStepZ();}
    static int neighborMask(BlockPos pos, Direction right, Predicate<BlockPos> compatible) {
        int mask=0;
        if(compatible.test(pos.relative(right.getOpposite())))mask|=1;
        if(compatible.test(pos.relative(right)))mask|=2;
        if(compatible.test(pos.below()))mask|=4;
        if(compatible.test(pos.above()))mask|=8;
        return mask;
    }
    private NativePanelGroups() {}
}
