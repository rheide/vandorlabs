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

    /** Keep every possible overhang inside its section, including future neighbor joins. */
    public static boolean baked(IBlockState state,IBlockAccess world,BlockPos pos) {
        if(!diagonal(state.getBlock()))return false;
        TileEntity raw=world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector tile=raw instanceof TileEntityAnimatedScreenSelector?(TileEntityAnimatedScreenSelector)raw:null;
        return baked(state.getBlock(),pos,state.getBlock().getMetaFromState(state),
                tile!=null && tile.isDiagonalHalfHeight(),tile!=null && tile.isDiagonalFullWidth());
    }
    private static boolean diagonal(net.minecraft.block.Block block) {
        return block instanceof BlockProgrammableWall
                && ((BlockProgrammableWall)block).getShape()==BlockProgrammableWall.Shape.DIAGONAL;
    }
    /** Numeric predicate shared by chunk construction and the per-frame tile pass. */
    public static boolean baked(net.minecraft.block.Block block,BlockPos pos,int metadata,boolean halfHeight,boolean fullWidth) {
        if(!diagonal(block))return false;
        int x=pos.getX()&15,y=pos.getY()&15,z=pos.getZ()&15;
        // Shallow walls have no corner arms; only the slope's vertical end protrudes.
        if(halfHeight)return (metadata&4)==0?y>0:y<15;
        // Full-height geometry is vertically contained. Allow for horizontal corner arms.
        EnumFacing facing=EnumFacing.getHorizontal(metadata&3);
        if(fullWidth)return x>0 && x<15 && z>0 && z<15;
        switch(facing) {
            case NORTH:return x>0 && x<15 && z>0;
            case SOUTH:return x>0 && x<15 && z<15;
            case WEST:return z>0 && z<15 && x>0;
            case EAST:return z>0 && z<15 && x<15;
            default:return false;
        }
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
