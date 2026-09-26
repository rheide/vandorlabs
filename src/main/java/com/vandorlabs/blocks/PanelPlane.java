package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Face-local axes shared by connected lights, portholes and propulsion fixtures. */
public final class PanelPlane {
    private static final PanelPlane[] PLANES = new PanelPlane[6];
    static {
        for (EnumFacing facing : EnumFacing.values()) PLANES[facing.getIndex()] = new PanelPlane(facing);
    }
    public final EnumFacing right, up;
    private final EnumFacing[] neighbors;

    private PanelPlane(EnumFacing facing) {
        right = facing.getAxis().isHorizontal() ? facing.rotateY() : EnumFacing.EAST;
        up = facing == EnumFacing.UP ? EnumFacing.SOUTH
                : facing == EnumFacing.DOWN ? EnumFacing.NORTH : EnumFacing.UP;
        neighbors = new EnumFacing[]{right, right.getOpposite(), up, up.getOpposite()};
    }
    public static PanelPlane of(EnumFacing facing) { return PLANES[facing.getIndex()]; }
    EnumFacing neighbor(int index) { return neighbors[index]; }
    public static int axis(BlockPos pos, EnumFacing direction) {
        return pos.getX()*direction.getFrontOffsetX() + pos.getY()*direction.getFrontOffsetY()
                + pos.getZ()*direction.getFrontOffsetZ();
    }
}
