package com.vandorlabs.blocks;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Door whose stationary frame and independently moving leaves come from the
 * detailed Blockbench import. */
public class BlockDetailedDoor extends BlockVandorDoor {

    // Sliding leaves stay on the pack's centre track. Rotating leaves are
    // generated against the facing edge, like the legacy/glass hinged doors.
    private static final AxisAlignedBB CLOSED_NS =
            new AxisAlignedBB(0.0D, 0.0D, 0.35D, 1.0D, 1.0D, 0.70D);
    private static final AxisAlignedBB CLOSED_EW =
            new AxisAlignedBB(0.30D, 0.0D, 0.0D, 0.65D, 1.0D, 1.0D);
    private static final AxisAlignedBB CLOSED_N =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.35D);
    private static final AxisAlignedBB CLOSED_S =
            new AxisAlignedBB(0.0D, 0.0D, 0.65D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB CLOSED_W =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.35D, 1.0D, 1.0D);
    private static final AxisAlignedBB CLOSED_E =
            new AxisAlignedBB(0.65D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB OPEN_W =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.25D, 1.0D, 1.0D);
    private static final AxisAlignedBB OPEN_E =
            new AxisAlignedBB(0.75D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB OPEN_N =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.25D);
    private static final AxisAlignedBB OPEN_S =
            new AxisAlignedBB(0.0D, 0.0D, 0.75D, 1.0D, 1.0D, 1.0D);

    private final boolean sliding;
    private final boolean doubleDoor;
    private final boolean splitInsideOneBlock;
    private final float leftPivot;
    private final float rightPivot;
    private final float pivotZ;
    private final float leftAngle;
    private final float rightAngle;
    private final float leftSlide;
    private final float rightSlide;

    public BlockDetailedDoor(String name, DoorMotion motion, boolean sliding,
            boolean doubleDoor, boolean splitInsideOneBlock,
            float leftPivot, float rightPivot, float pivotZ,
            float leftAngle, float rightAngle, float leftSlide, float rightSlide) {
        super(name, motion, true);
        this.sliding = sliding;
        this.doubleDoor = doubleDoor;
        this.splitInsideOneBlock = splitInsideOneBlock;
        this.leftPivot = leftPivot;
        this.rightPivot = rightPivot;
        this.pivotZ = pivotZ;
        this.leftAngle = leftAngle;
        this.rightAngle = rightAngle;
        this.leftSlide = leftSlide;
        this.rightSlide = rightSlide;
    }

    public boolean isSlidingModel() { return sliding; }
    public boolean isDoubleModel() { return doubleDoor; }
    public boolean isSplitInsideOneBlock() { return splitInsideOneBlock; }
    public float getPivot(boolean right) { return right ? rightPivot : leftPivot; }
    public float getPivotZ() { return pivotZ; }
    public float getAngle(boolean right) { return right ? rightAngle : leftAngle; }
    public float getSlide(boolean right) { return right ? rightSlide : leftSlide; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        IBlockState actual = getActualState(state, world, pos);
        EnumFacing facing = actual.getValue(FACING);
        if (sliding) {
            return facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH
                    ? CLOSED_NS : CLOSED_EW;
        }
        if (!actual.getValue(OPEN) || splitInsideOneBlock) {
            switch (facing) {
                case SOUTH: return CLOSED_S;
                case EAST: return CLOSED_E;
                case WEST: return CLOSED_W;
                case NORTH:
                default: return CLOSED_N;
            }
        }
        // A rotating leaf finishes on its physical hinge side.  These wider
        // edge boxes cover the pack's hinge barrels and raised armor, whereas
        // the old generic 2-pixel door box missed much of the visible model.
        boolean vanillaLeft = actual.getValue(HINGE)
                == BlockDoor.EnumHingePosition.LEFT;
        switch (facing) {
            case SOUTH: return vanillaLeft ? OPEN_E : OPEN_W;
            case EAST: return vanillaLeft ? OPEN_N : OPEN_S;
            case WEST: return vanillaLeft ? OPEN_S : OPEN_N;
            case NORTH:
            default: return vanillaLeft ? OPEN_W : OPEN_E;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            IBlockAccess world, BlockPos pos) {
        IBlockState actual = getActualState(state, world, pos);
        // Sliding and split leaves clear the doorway.  Rotating leaves retain
        // their accurately positioned edge collision. Closed rotating doors
        // now share the near-edge placement of the legacy hinged models.
        if (actual.getValue(OPEN) && (sliding || splitInsideOneBlock
                || getDoorMotion().clearsOpening())) {
            return NULL_AABB;
        }
        return getBoundingBox(actual, world, pos);
    }
}
