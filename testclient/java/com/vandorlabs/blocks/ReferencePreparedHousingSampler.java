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

/** Frozen delivered performance-patch sampler for end-to-end state preparation costs. */
public final class ReferencePreparedHousingSampler {
    private static final EnumFacing[] DIRECTIONS=EnumFacing.values();
    private static final IUnlistedProperty<HousingFaceLayouts> JOINED=ProgrammableHousingState.JOINED;
    public static IBlockState extend(IBlockState state, IBlockAccess world, BlockPos pos,
            boolean slab) {
        TileEntity raw = world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector tile = raw instanceof TileEntityAnimatedScreenSelector
                ? (TileEntityAnimatedScreenSelector)raw : null;
        int finish = tile == null ? 0 : tile.getHousingTexture();
        int tileSides = tile != null ? tile.getMainTextureLayout() : 0;
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
        IExtendedBlockState sampled=ReferencePreparedHousingState.sample((IExtendedBlockState)state,finish,tile==null?-1:tile.getSideTexture(),
                tile==null?com.vandorlabs.tiles.FaceTextures.DEFAULT:tile.getFaceTextures(),
                tileSides,visible,0);
        if(tile!=null&&tile.getWorld()!=null&&tile.getWorld().isRemote&&state.getBlock().getClass()==BlockProgrammableBlock.class&&tileSides!=1){
            HousingFaceLayouts joined=com.vandorlabs.client.JoinedHousingFaces.sample(tile,visible);
            if(joined!=HousingFaceLayouts.NONE)sampled=sampled.withProperty(JOINED,joined);
        }
        return sampled;
    }

}
