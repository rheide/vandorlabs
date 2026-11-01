package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Authored display plane, shared by visual joining and local redstone grouping. */
public final class DisplayJoinGeometry {
    public static final class Basis {
        public final EnumFacing right,up;
        public final BlockPos[] steps;
        Basis(TileEntityAnimatedScreenSelector tile,IBlockState state){
            EnumFacing face=state.getBlock() instanceof BlockProgrammableInput?state.getValue(BlockProgrammableInput.FACING):state.getBlock() instanceof BlockProgrammableDiagonalScreen?state.getValue(BlockProgrammableDiagonalScreen.FACING):state.getValue(BlockAnimatedScreenSelector.FACING);
            right=face.getAxis().isHorizontal()?face.rotateY():EnumFacing.EAST;
            EnumFacing vertical=face.getAxis().isHorizontal()?EnumFacing.UP:face==EnumFacing.UP?EnumFacing.NORTH:EnumFacing.SOUTH;
            if(state.getBlock() instanceof BlockProgrammableInput&&(state.getValue(BlockProgrammableInput.KEYBOARD)||tile.isCeilingMounted()))vertical=face.getOpposite();
            up=vertical;BlockPos row=new BlockPos(up.getFrontOffsetX(),up.getFrontOffsetY(),up.getFrontOffsetZ());
            if(state.getBlock() instanceof BlockProgrammableDiagonalScreen)row=row.offset(state.getValue(BlockProgrammableDiagonalScreen.INVERTED)?face:face.getOpposite());
            BlockPos column=new BlockPos(right.getFrontOffsetX(),right.getFrontOffsetY(),right.getFrontOffsetZ());
            steps=new BlockPos[]{column,new BlockPos(-column.getX(),-column.getY(),-column.getZ()),row,new BlockPos(-row.getX(),-row.getY(),-row.getZ())};
        }
        public long cell(BlockPos pos){return com.vandorlabs.surface.JoinedRectangles.cell(pos.getX()*right.getFrontOffsetX()+pos.getZ()*right.getFrontOffsetZ(),pos.getX()*up.getFrontOffsetX()+pos.getY()*up.getFrontOffsetY()+pos.getZ()*up.getFrontOffsetZ());}
    }
    public static Basis basis(TileEntityAnimatedScreenSelector tile,IBlockState state){return new Basis(tile,state);}
    public static boolean supportsJoining(TileEntityAnimatedScreenSelector tile){
        return !(tile.getBlockType() instanceof BlockProgrammableHalfConsole||tile.getBlockType() instanceof BlockDiagonalHalfConsole)
            &&!(tile.isSmallInput()&&tile.getBlockType() instanceof BlockProgrammableFullInput);
    }
    public static boolean sameShape(TileEntityAnimatedScreenSelector a,TileEntityAnimatedScreenSelector b){return a.isCeilingMounted()==b.isCeilingMounted()&&a.isSmallInput()==b.isSmallInput()&&a.getCeilingPosition(-1)==b.getCeilingPosition(-1)&&a.getWallPosition(-1)==b.getWallPosition(-1);}
    private DisplayJoinGeometry(){}
}
