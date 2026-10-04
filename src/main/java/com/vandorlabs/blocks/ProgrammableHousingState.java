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
    public static final IUnlistedProperty<Integer> SIDE_FINISH=integer("housing_side_finish");
    public static final IUnlistedProperty<Integer> FINISH = integer("housing_finish");
    public static final IUnlistedProperty<Integer> VISIBLE = integer("housing_visible_faces");
    public static final IUnlistedProperty<Integer> TILE_SIDES = integer("housing_tile_sides");
    public static final IUnlistedProperty<Integer> LIGHT = integer("housing_light");

    public static final IUnlistedProperty<com.vandorlabs.tiles.FaceTextures> FACES =
            new IUnlistedProperty<com.vandorlabs.tiles.FaceTextures>() {
                public String getName() { return "housing_face_textures"; }
                public boolean isValid(com.vandorlabs.tiles.FaceTextures value) { return value != null; }
                public Class<com.vandorlabs.tiles.FaceTextures> getType() { return com.vandorlabs.tiles.FaceTextures.class; }
                public String valueToString(com.vandorlabs.tiles.FaceTextures value) { return value.toString(); }
            };

    private static final EnumFacing[] DIRECTIONS=EnumFacing.values();

    private ProgrammableHousingState() { }

    public static IUnlistedProperty<Integer> integer(String name) {
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
        BlockPos.MutableBlockPos next=new BlockPos.MutableBlockPos();
        for (EnumFacing side : DIRECTIONS) {
            next.setPos(pos.getX()+side.getFrontOffsetX(),pos.getY()+side.getFrontOffsetY(),pos.getZ()+side.getFrontOffsetZ());
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
        return HousingBlockState.sample((IExtendedBlockState)state,finish,tile==null?-1:tile.getSideTexture(),
                tile==null?com.vandorlabs.tiles.FaceTextures.DEFAULT:tile.getFaceTextures(),
                tileSides,visible,neighborLight(world,pos));
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
        BlockPos.MutableBlockPos next=new BlockPos.MutableBlockPos();
        for (EnumFacing side : DIRECTIONS) {
            next.setPos(pos.getX()+side.getFrontOffsetX(),pos.getY()+side.getFrontOffsetY(),pos.getZ()+side.getFrontOffsetZ());
            if (world instanceof World && !((World)world).isBlockLoaded(next)) continue;
            int combined = world.getCombinedLight(next, 0);
            sky = Math.max(sky, combined >>> 16);
            block = Math.max(block, combined & 65535);
        }
        return (sky << 16) | block;
    }
}
