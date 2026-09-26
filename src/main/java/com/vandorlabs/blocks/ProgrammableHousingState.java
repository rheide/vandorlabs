package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Settings sampled when a chunk mesh is built. Registry metadata is unchanged. */
public final class ProgrammableHousingState {
    public static final IUnlistedProperty<Integer> FINISH = integer("housing_finish");
    public static final IUnlistedProperty<Integer> VISIBLE = integer("housing_visible_faces");
    public static final IUnlistedProperty<Integer> TILE_SIDES = integer("housing_tile_sides");
    public static final IUnlistedProperty<Integer> LIGHT = integer("housing_light");

    private ProgrammableHousingState() { }

    private static IUnlistedProperty<Integer> integer(String name) {
        return new IUnlistedProperty<Integer>() {
            @Override public String getName() { return name; }
            @Override public boolean isValid(Integer value) { return value != null; }
            @Override public Class<Integer> getType() { return Integer.class; }
            @Override public String valueToString(Integer value) { return value.toString(); }
        };
    }

    public static IBlockState extend(IBlockState state, IBlockAccess world, BlockPos pos,
            boolean slab) {
        TileEntity raw = world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector tile = raw instanceof TileEntityAnimatedScreenSelector
                ? (TileEntityAnimatedScreenSelector)raw : null;
        int finish = tile == null ? 0 : tile.getHousingTexture();
        int tileSides = tile != null && tile.isSlabTileSides() ? 1 : 0;
        int visible = 63;
        BlockSlab.EnumBlockHalf half = slab ? state.getValue(BlockProgrammableSlab.HALF) : null;
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos next = pos.offset(side);
            if (world instanceof World && !((World)world).isBlockLoaded(next)) continue;
            IBlockState neighbor = world.getBlockState(next);
            boolean hide = neighbor.isOpaqueCube() && neighbor.isFullCube();
            if (slab) {
                boolean boundary = side.getAxis() != EnumFacing.Axis.Y
                        || side == EnumFacing.UP && half == BlockSlab.EnumBlockHalf.TOP
                        || side == EnumFacing.DOWN && half == BlockSlab.EnumBlockHalf.BOTTOM;
                hide &= boundary;
                if (side.getAxis() != EnumFacing.Axis.Y
                        && neighbor.getBlock() instanceof BlockProgrammableSlab
                        && neighbor.getValue(BlockProgrammableSlab.HALF) == half)
                    hide = true;
            }
            if (hide) visible &= ~(1 << side.getIndex());
        }
        return ((IExtendedBlockState)state).withProperty(FINISH, finish)
                .withProperty(TILE_SIDES, tileSides).withProperty(VISIBLE, visible)
                .withProperty(LIGHT, neighborLight(world, pos));
    }

    public static int light(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state instanceof IExtendedBlockState) {
            Integer snapshot = ((IExtendedBlockState)state).getValue(LIGHT);
            if (snapshot != null) return snapshot;
        }
        return neighborLight(world, pos);
    }

    public static int neighborLight(IBlockAccess world, BlockPos pos) {
        int sky = 0, block = 0;
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos next = pos.offset(side);
            if (world instanceof World && !((World)world).isBlockLoaded(next)) continue;
            int combined = world.getCombinedLight(next, 0);
            sky = Math.max(sky, combined >>> 16);
            block = Math.max(block, combined & 65535);
        }
        return (sky << 16) | block;
    }
}
