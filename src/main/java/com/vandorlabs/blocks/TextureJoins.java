package com.vandorlabs.blocks;

import com.vandorlabs.surface.TextureJoinOwner;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Read-only root and mutual endpoint admission; callers retain material/geometry checks. */
public final class TextureJoins {
    public static boolean allows(TileEntity owner,BlockPos from,BlockPos next){
        if(!(owner instanceof TextureJoinOwner))return true;
        if(!((TextureJoinOwner)owner).textureJoin().connects(from.getX(),from.getY(),from.getZ(),next.getX(),next.getY(),next.getZ()))return false;
        net.minecraft.world.World world=owner.getWorld();
        if(world==null||!world.isBlockLoaded(from)||!world.isBlockLoaded(next))return false;
        TileEntity a=world.getTileEntity(from),b=world.getTileEntity(next);
        com.vandorlabs.surface.TextureJoinSettings first=a instanceof TextureJoinOwner?((TextureJoinOwner)a).textureJoin():com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
        com.vandorlabs.surface.TextureJoinSettings second=b instanceof TextureJoinOwner?((TextureJoinOwner)b).textureJoin():com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
        return first.connectsWith(second,from.getX(),from.getY(),from.getZ(),next.getX(),next.getY(),next.getZ());
    }
    public static boolean defaultPolicy(TileEntity owner){return !(owner instanceof TextureJoinOwner)
            ||((TextureJoinOwner)owner).textureJoin().equals(com.vandorlabs.surface.TextureJoinSettings.DEFAULT);}
    public static boolean joined(net.minecraft.world.World world,BlockPos from,BlockPos next){
        if(!world.isBlockLoaded(from))return false;TileEntity tile=world.getTileEntity(from);
        return tile instanceof TextureJoinOwner&&joined(world,from,next,((TextureJoinOwner)tile).textureJoin());
    }
    /** Actual direct artwork neighbours; selection separately checks matching settings and edit rights. */
    public static boolean joined(net.minecraft.world.World world,BlockPos from,BlockPos next,com.vandorlabs.surface.TextureJoinSettings policy){
        if(!world.isBlockLoaded(from)||!world.isBlockLoaded(next)||!policy.connects(from.getX(),from.getY(),from.getZ(),next.getX(),next.getY(),next.getZ()))return false;
        TileEntity a=world.getTileEntity(from),b=world.getTileEntity(next);
        if(!(a instanceof TextureJoinOwner)||!(b instanceof TextureJoinOwner)||!((TextureJoinOwner)a).textureJoin().connectsWith(((TextureJoinOwner)b).textureJoin(),from.getX(),from.getY(),from.getZ(),next.getX(),next.getY(),next.getZ()))return false;
        net.minecraft.block.state.IBlockState as=world.getBlockState(from),bs=world.getBlockState(next);if(as.getBlock()!=bs.getBlock())return false;
        BlockPos delta=next.subtract(from);int distance=Math.abs(delta.getX())+Math.abs(delta.getY())+Math.abs(delta.getZ());
        if(a instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass){
            if(distance!=1||!PanelJoins.flat(as)||!PanelJoins.flat(bs))return false;
            net.minecraft.util.EnumFacing facing=PanelJoins.flatFacing(as,world,from),other=PanelJoins.flatFacing(bs,world,next);
            return facing.getAxis()==other.getAxis()&&PanelPlane.axis(delta,facing)==0&&PanelJoins.flatNear(as,world,from)==PanelJoins.flatNear(bs,world,next);
        }
        if(!(a instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)||!(b instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector))return false;
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)a,other=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)b;
        if(as.getBlock().getClass()==BlockProgrammableBlock.class)return distance==1&&tile.getMainTextureLayout()!=1&&other.getMainTextureLayout()!=1;
        if(as.getBlock() instanceof BlockProgrammableSlab)return false;
        if(!(as.getBlock() instanceof BlockProgrammableWall))return false;
        BlockProgrammableWall wall=(BlockProgrammableWall)as.getBlock();
        if(wall.isPortholeShape()&&(!tile.isJoinPortholes()||!other.isJoinPortholes()))return false;
        if(!wall.isDiagonalShape())return distance==1&&PanelPlane.axis(delta,as.getValue(BlockProgrammableWall.FACING))==0&&as.getValue(BlockProgrammableWall.FACING)==bs.getValue(BlockProgrammableWall.FACING)&&com.vandorlabs.surface.TextureJoinSettings.samePanelDepth(wall instanceof BlockProgrammablePortholeBlock,as.getValue(BlockProgrammableWall.DEPTH),bs.getValue(BlockProgrammableWall.DEPTH));
        int mode=BlockProgrammableWall.geometry(world,from);if(mode!=BlockProgrammableWall.geometry(world,next)||tile.getDiagonalJoin()==1||other.getDiagonalJoin()==1)return false;
        net.minecraft.util.EnumFacing across=as.getValue(BlockProgrammableWall.FACING).rotateY();
        boolean sideways=next.equals(from.offset(across))||next.equals(from.offset(across.getOpposite()));
        boolean row=DiagonalPanelGeometry.rowCandidates(from,as,mode,true).contains(next)||DiagonalPanelGeometry.rowCandidates(from,as,mode,false).contains(next);
        return (sideways||row)&&(DiagonalPanelGeometry.samePlane(from,as,next,bs,mode)||DiagonalPanelGeometry.rowsMeet(from,as,next,bs,mode));
    }
    private TextureJoins(){}
}
