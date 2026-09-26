package com.vandorlabs.blocks;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Half-height programmable block with vanilla-style top and bottom placement. */
public final class BlockProgrammableSlab extends BlockAnimatedScreenSelector {
    public static final PropertyEnum<BlockSlab.EnumBlockHalf> HALF =
            PropertyEnum.create("half", BlockSlab.EnumBlockHalf.class);
    private static final AxisAlignedBB BOTTOM =
            new AxisAlignedBB(0, 0, 0, 1, .5, 1);
    private static final AxisAlignedBB TOP =
            new AxisAlignedBB(0, .5, 0, 1, 1, 1);

    public BlockProgrammableSlab() {
        super("programmable_slab");
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(HALF, BlockSlab.EnumBlockHalf.BOTTOM));
        setLightLevel(0);
        useNeighborBrightness = true;
    }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING, HALF},
                new IUnlistedProperty<?>[]{ProgrammableHousingState.FINISH,
                        ProgrammableHousingState.TILE_SIDES, ProgrammableHousingState.VISIBLE,
                        ProgrammableHousingState.LIGHT});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.extend(state, world, pos, true);
    }

    @Override public int getPackedLightmapCoords(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.light(state, world, pos);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        BlockSlab.EnumBlockHalf half = side == EnumFacing.DOWN
                || (side != EnumFacing.UP && hitY > .5F)
                ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM;
        return getDefaultState().withProperty(FACING,
                placementFacing(world, pos, side, placer)).withProperty(HALF, half);
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        int facing = meta & 7;
        return getDefaultState().withProperty(FACING,
                        facing < EnumFacing.values().length
                                ? EnumFacing.getFront(facing) : EnumFacing.NORTH)
                .withProperty(HALF, (meta & 8) == 0
                        ? BlockSlab.EnumBlockHalf.BOTTOM : BlockSlab.EnumBlockHalf.TOP);
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex()
                | (state.getValue(HALF) == BlockSlab.EnumBlockHalf.TOP ? 8 : 0);
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state,
            IBlockAccess world, BlockPos pos) {
        return state.getValue(HALF) == BlockSlab.EnumBlockHalf.TOP ? TOP : BOTTOM;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world,
            BlockPos pos) { return 0; }
}
