package com.vandorlabs.blocks;

import com.vandorlabs.render.InputSurfaceLayout;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Full-square counterpart to the half-height programmable input surface. */
public class BlockProgrammableFullInput extends BlockProgrammableInput {

    public static final String NAME = "programmable_input";
    public BlockProgrammableFullInput() {
        super(NAME);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        net.minecraft.tileentity.TileEntity raw=source.getTileEntity(pos);
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=
                raw instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                        ?(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)raw:null;
        InputSurfaceLayout.Box box=tile!=null && tile.isCeilingMounted()
                ?InputSurfaceLayout.ceilingInput(true,false,tile.getCeilingPosition(1)).housing
                :InputSurfaceLayout.fullInput(state.getValue(KEYBOARD),state.getValue(UPPER)).housing;
        AxisAlignedBB local=new AxisAlignedBB(box.x0/16,box.y0/16,box.z0/16,
                box.x1/16,box.y1/16,box.z1/16);
        return rotateFromNorth(local, state.getValue(FACING));
    }
}
