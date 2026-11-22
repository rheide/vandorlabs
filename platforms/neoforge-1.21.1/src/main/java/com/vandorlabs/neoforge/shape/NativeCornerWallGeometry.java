package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidRegions;
import com.vandorlabs.render.CornerGlassGeometry;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Corner Wall publication: the shared corner bevel and which of its two ends continue into a
 * neighbouring wall ({@code joined} bit 0 the first side, bit 1 the second). Instances come from
 * the block's fixed table and compare by identity.
 */
public record NativeCornerWallGeometry(CornerGlassGeometry corner,int joined,List<AABB> boxes,AABB bounds) implements NativeWallGeometry {
    public NativeCornerWallGeometry {boxes=List.copyOf(boxes);}
    @Override public Direction facing(){return Direction.from2DDataValue(corner.turn());}
    @Override public int depth(){return 0;}
    @Override public NativePanelLayout layout(){return NativePanelLayout.UNIT;}
    @Override public NativeWallGeometry withLayout(NativePanelLayout layout){return this;}
    @Override public int side(double x,double y,double z){return corner.side(x,y,z);}
    @Override public NativeFluidRegions fluidRegions(boolean porthole){return NativeFluidRegions.empty();}
    @Override public boolean sameFluidGeometry(NativeWallGeometry other){return other instanceof NativeCornerWallGeometry value&&value.corner==corner;}
    // One instance per bevel and join mask (the block's table): identity keeps model-cache lookups
    // and republication checks from hashing and comparing the collision box list.
    @Override public boolean equals(Object other){return this==other;}
    @Override public int hashCode(){return System.identityHashCode(this);}
}
