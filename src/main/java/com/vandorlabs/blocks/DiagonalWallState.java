package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Immutable numeric chunk-build input; never retains a world or tile entity. */
public final class DiagonalWallState {
    public static final IUnlistedProperty<DiagonalWallState> PROPERTY=new IUnlistedProperty<DiagonalWallState>() {
        public String getName(){return "diagonal_mesh";}
        public boolean isValid(DiagonalWallState value){return value!=null;}
        public Class<DiagonalWallState> getType(){return DiagonalWallState.class;}
        public String valueToString(DiagonalWallState value){return "diagonal mesh";}
    };
    public final EnumFacing facing;
    public final boolean inverted,halfHeight,hideLower,hideUpper;
    public final int texture,fill,light;
    public final double span,lowerEnd,upperEnd;
    public final BlockProgrammableWall.Corner corner;
    private final double[] clip;

    /** Keep overhanging boundary panels on their original expanded TESR bounds. */
    public static boolean baked(IBlockState state,BlockPos pos) {
        return baked(state.getBlock(),pos);
    }
    public static boolean baked(net.minecraft.block.Block block,BlockPos pos) {
        if(!(block instanceof BlockProgrammableWall)
                || ((BlockProgrammableWall)block).getShape()!=BlockProgrammableWall.Shape.DIAGONAL)return false;
        int x=pos.getX()&15,y=pos.getY()&15,z=pos.getZ()&15;
        return x>0 && x<15 && y>0 && y<15 && z>0 && z<15;
    }
    public DiagonalWallState(IBlockState state,IBlockAccess world,BlockPos pos) {
        TileEntity raw=world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector tile=raw instanceof TileEntityAnimatedScreenSelector?(TileEntityAnimatedScreenSelector)raw:null;
        facing=state.getValue(BlockProgrammableWall.FACING);inverted=state.getValue(BlockProgrammableWall.INVERTED);
        texture=tile==null?0:tile.getHousingTexture();halfHeight=tile!=null && tile.isDiagonalHalfHeight();
        fill=tile==null?0:tile.getDiagonalFill();span=BlockProgrammableWall.diagonalSpan(world,pos);
        corner=((BlockProgrammableWall)state.getBlock()).corner(state,world,pos);
        lowerEnd=BlockProgrammableWall.flatEnd(state,world,pos,false)*16;
        upperEnd=BlockProgrammableWall.flatEnd(state,world,pos,true)*16;
        hideLower=DiagonalPanelGeometry.coveredEnd(world,pos,state,false);
        hideUpper=DiagonalPanelGeometry.coveredEnd(world,pos,state,true);
        clip=DiagonalNeighbourBounds.local(world,pos,state,BlockProgrammableWall.geometry(world,pos),16);
        light=world.getCombinedLight(pos,0);
    }
    public double[] clip(){return clip.clone();}
}
