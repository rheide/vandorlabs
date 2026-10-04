package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import static com.vandorlabs.blocks.ProgrammableHousingState.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
/** Released six-step state builder for paired allocation and value comparisons. */
final class ReferenceHousingState {
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
                .withProperty(SIDE_FINISH,tile==null?-1:tile.getSideTexture())
                .withProperty(FACES, tile == null ? com.vandorlabs.tiles.FaceTextures.DEFAULT : tile.getFaceTextures())
                .withProperty(TILE_SIDES, tileSides).withProperty(VISIBLE, visible)
                .withProperty(LIGHT, neighborLight(world, pos));
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
