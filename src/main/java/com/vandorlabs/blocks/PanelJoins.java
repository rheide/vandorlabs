package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Shared wall/glass compatibility and canonical four-pixel panel coordinates. */
public final class PanelJoins {
    public static BlockProgrammableWall.FlatCorner flatCorner(IBlockState state,IBlockAccess world,BlockPos pos) {
        return BlockProgrammableWall.findFlatCorner(state,world,pos);
    }
    /** Identify an aligned full-width T bar; L and cross junctions retain their geometry. */
    public static BlockProgrammableWall.FlatCorner flatBar(IBlockState state,IBlockAccess world,BlockPos pos) {
        if(!cornerPanel(state))return null;
        BlockProgrammableWall.FlatCorner c=flatCorner(state,world,pos);
        return c!=null && c.frontArm!=null && c.backArm!=null
                && Math.abs(c.frontArm-c.backArm)<1E-7 && c.straightLeft!=c.straightRight?c:null;
    }
    public static EnumFacing flatFacing(IBlockState state,IBlockAccess world,BlockPos pos) {
        EnumFacing canonical=facing(state);
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass
                ?((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).getPanelFacing(canonical):canonical;
    }
    public static double flatNear(IBlockState state,IBlockAccess world,BlockPos pos) {
        return flatFacing(state,world,pos)==facing(state)?near(state):12-near(state);
    }
    static boolean flatEnabled(IBlockAccess world,BlockPos pos) {
        if(!enabled(world,pos))return false;
        IBlockState state=world.getBlockState(pos);
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return !(state.getBlock() instanceof BlockProgrammableWall)
                || !((BlockProgrammableWall)state.getBlock()).isPortholeShape()
                || !(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)
                || ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isJoinPortholes();
    }
    static boolean cornerPanel(IBlockState state) {
        return flat(state) && !(state.getBlock() instanceof BlockProgrammablePortholeBlock);
    }
    public static java.util.List<net.minecraft.util.math.AxisAlignedBB> flatBoxes(IBlockState state,IBlockAccess world,BlockPos pos) {
        BlockProgrammableWall.FlatCorner c=flatCorner(state,world,pos);
        double n=flatNear(state,world,pos);
        EnumFacing orientation=flatFacing(state,world,pos);
        java.util.List<net.minecraft.util.math.AxisAlignedBB> result=new java.util.ArrayList<>();
        for(double[] box:com.vandorlabs.render.FlatWallTopology.boxes(n,c==null?null:c.portable()))
            result.add(PanelPlacement.rotateFromNorth(new net.minecraft.util.math.AxisAlignedBB(box[0]/16,0,box[1]/16,box[2]/16,1,box[3]/16),orientation));
        return result;
    }
    static com.vandorlabs.render.FlatWallTopology.Panel flatSnapshot(IBlockState state,IBlockAccess world,BlockPos pos) {
        if(!flat(state))return null;
        return new com.vandorlabs.render.FlatWallTopology.Panel(true,cornerPanel(state),enabled(world,pos),flatEnabled(world,pos),
                facing(state).getIndex(),flatFacing(state,world,pos).getIndex(),near(state),flatNear(state,world,pos));
    }
    /** A placed regular panel anchors to the diagonal's actual end, avoiding a folded slope. */
    public static int placementDepth(IBlockAccess world,BlockPos support,IBlockState state,EnumFacing side){
        if(!diagonal(state) || BlockProgrammableWall.halfHeight(world,support) || side.getAxis()!=EnumFacing.Axis.Y)return -1;
        double end=16*com.vandorlabs.render.DiagonalWallGeometry.near(BlockProgrammableWall.geometry(world,support),state.getValue(BlockProgrammableWall.INVERTED),side==EnumFacing.UP?1:0);
        int best=0;double distance=Double.POSITIVE_INFINITY;
        for(int depth=0;depth<3;depth++){double delta=Math.abs(PanelDepth.start(depth)-end);if(delta<distance){best=depth;distance=delta;}}
        return best;
    }
    /** Full-height corner participants; full-width portholes share the wall/glass topology. */
    public static boolean cornerDiagonal(IBlockState state,IBlockAccess world,BlockPos pos) {
        if(!(state.getBlock() instanceof BlockProgrammableWall))return false;
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        return block.isDiagonalShape() && !BlockProgrammableWall.halfHeight(world,pos)
                && (!block.isPortholeShape() || BlockProgrammableWall.geometry(world,pos)==1);
    }
    public static boolean cornerDiagonalEnabled(IBlockAccess world,BlockPos pos) {
        if(!enabled(world,pos))return false;
        if(BlockProgrammableWall.geometry(world,pos)!=1)return true;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
        if(!(raw instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector))return true;
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)raw;
        IBlockState state=world.getBlockState(pos);
        return tile.getDiagonalJoin()!=1 && (!(state.getBlock() instanceof BlockProgrammableWall)
                || !((BlockProgrammableWall)state.getBlock()).isPortholeShape() || tile.isJoinPortholes());
    }
    public static boolean diagonal(IBlockState state){return state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).isDiagonalShape() && !((BlockProgrammableWall)state.getBlock()).isPortholeShape();}
    public static boolean flat(IBlockState state){return state.getBlock() instanceof BlockProgrammableGlass || state.getBlock() instanceof BlockProgrammableWall && !((BlockProgrammableWall)state.getBlock()).isDiagonalShape();}
    public static EnumFacing facing(IBlockState state){return state.getBlock() instanceof BlockProgrammableGlass ? (state.getValue(BlockGlassWall.ROTATED)?EnumFacing.EAST:EnumFacing.NORTH):state.getValue(BlockProgrammableWall.FACING);}
    public static double near(IBlockState state){return PanelDepth.start(state.getValue(state.getBlock() instanceof BlockProgrammableGlass?BlockGlassWall.DEPTH:BlockProgrammableWall.DEPTH));}
    public static boolean enabled(IBlockAccess world,BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return !(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass) || ((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).isJoin();}
    /** Match a flat panel's edge to an aligned flat face or a diagonal endpoint. */
    public static boolean connectsFlat(IBlockAccess world,BlockPos flatPos,IBlockState flatState,BlockPos next){
        if(world instanceof net.minecraft.world.World && (!((net.minecraft.world.World)world).isBlockLoaded(flatPos) || !((net.minecraft.world.World)world).isBlockLoaded(next)))return false;
        if(!enabled(world,flatPos) || !enabled(world,next))return false;
        IBlockState other=world.getBlockState(next);
        EnumFacing facing=facing(flatState);
        if(flat(other)){
            EnumFacing of=facing(other);
            if(of.getAxis()!=facing.getAxis())return false;
            double n=near(other);if(of!=facing)n=12-n;
            EnumFacing normal=facing.getOpposite();
            BlockPos delta=next.subtract(flatPos);
            n+=16D*PanelPlane.axis(delta,normal);
            return n==near(flatState);
        }
        if(!diagonal(other) || BlockProgrammableWall.halfHeight(world,next)
                || facing(other).getAxis()!=facing.getAxis() || Math.abs(flatPos.getY()-next.getY())!=1)return false;
        boolean upper=flatPos.getY()>next.getY();
        double end=BlockProgrammableWall.flatEnd(other,world,next,upper);
        if(Double.isNaN(end))return false;
        double flatNear=near(flatState)/16;
        if(facing!=facing(other))flatNear=.75-flatNear;
        int dx=flatPos.getX()-next.getX(),dz=flatPos.getZ()-next.getZ();
        EnumFacing normal=facing(other).getOpposite();
        flatNear+=dx*normal.getFrontOffsetX()+dz*normal.getFrontOffsetZ();
        return Math.abs(end-flatNear)<1e-8;
    }
    private PanelJoins() { }
}
