package com.vandorlabs.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A detailed single door that borrows a matching double-door model when two
 * complete copies meet with their leaves resting at the outside jambs.
 * Pairing is calculated from neighbors and never consumes metadata or save
 * data, so old worlds remain compatible and split pairs revert immediately.
 */
public class BlockConnectingDetailedDoor extends BlockDetailedDoor {

    public static final PropertyBool PAIRED = PropertyBool.create("paired");

    private final BlockDetailedDoor pairedModel;

    public BlockConnectingDetailedDoor(String name, DoorMotion motion,
            boolean sliding, boolean doubleDoor, boolean splitInsideOneBlock,
            float leftPivot, float rightPivot, float pivotZ,
            float leftAngle, float rightAngle, float leftSlide, float rightSlide,
            BlockDetailedDoor pairedModel) {
        super(name, motion, sliding, doubleDoor, splitInsideOneBlock,
                leftPivot, rightPivot, pivotZ, leftAngle, rightAngle,
                leftSlide, rightSlide);
        if (pairedModel == null) {
            throw new IllegalArgumentException(name + " has no paired door model");
        }
        this.pairedModel = pairedModel;
        setDefaultState(getDefaultState().withProperty(PAIRED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, HALF, OPEN, HINGE,
                POWERED, PAIRED);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        IBlockState actual = super.getActualState(state, world, pos);
        return actual.withProperty(PAIRED, hasMatchingNeighbor(world, pos, actual));
    }

    /** Model and motion definition used by the animated leaf renderer. */
    public BlockDetailedDoor getVisualModel(IBlockState actualState) {
        return actualState.getValue(PAIRED) ? pairedModel : this;
    }

    public String getPairedModelId() {
        return pairedModel.getRegistryName().getResourcePath();
    }

    public int getLeafMetadata(boolean right, IBlockState actualState) {
        if (!actualState.getValue(PAIRED)) {
            return com.vandorlabs.render.DoorLeaf.fromRight(right).legacyMetadata;
        }
        return right ? 4 : 3;
    }

    private boolean hasMatchingNeighbor(IBlockAccess world, BlockPos pos,
            IBlockState actual) {
        BlockPos lowerPos = actual.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER
                ? pos : pos.down();
        IBlockState lower = world.getBlockState(lowerPos);
        if (lower.getBlock() != this
                || lower.getValue(HALF) != BlockDoor.EnumDoorHalf.LOWER
                || world.getBlockState(lowerPos.up()).getBlock() != this) {
            return false;
        }
        IBlockState lowerActual = super.getActualState(lower, world, lowerPos);
        EnumFacing facing = lowerActual.getValue(FACING);
        BlockDoor.EnumHingePosition hinge = lowerActual.getValue(HINGE);

        // Vanilla's hinge labels are mirrored from the visual hand. A LEFT
        // hinge rests on the visual right jamb and therefore needs its mate
        // on visual left; RIGHT is the converse.
        EnumFacing neighborDirection = hinge == BlockDoor.EnumHingePosition.LEFT
                ? facing.rotateY() : facing.rotateYCCW();
        BlockPos neighborPos = lowerPos.offset(neighborDirection);
        IBlockState neighbor = world.getBlockState(neighborPos);
        if (neighbor.getBlock() != this
                || neighbor.getValue(HALF) != BlockDoor.EnumDoorHalf.LOWER) {
            return false;
        }
        IBlockState neighborUpper = world.getBlockState(neighborPos.up());
        if (neighborUpper.getBlock() != this
                || neighborUpper.getValue(HALF) != BlockDoor.EnumDoorHalf.UPPER) {
            return false;
        }
        IBlockState neighborActual = super.getActualState(
                neighbor, world, neighborPos);
        return neighborActual.getValue(FACING) == facing
                && neighborActual.getValue(HINGE) != hinge;
    }

    private void refreshPairArea(World world, BlockPos pos) {
        world.markBlockRangeForRenderUpdate(
                pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        refreshPairArea(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
            Block changedBlock, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, changedBlock, fromPos);
        refreshPairArea(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        refreshPairArea(world, pos);
    }
}
