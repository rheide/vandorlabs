package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import com.vandorlabs.render.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;
import javax.annotation.Nullable;

public final class BlockProgrammableDiagonalTrapdoor extends BlockProgrammableTrapdoor {
    public BlockProgrammableDiagonalTrapdoor(){super("programmable_diagonal_trapdoor");}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityProgrammableDiagonalTrapdoor();}
    public static int placementMode(World world,BlockPos support,ItemStack stack) {
        net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
        if(tag!=null)return tag.hasKey("TrapdoorPosition",3)?Math.max(0,Math.min(2,tag.getInteger("TrapdoorPosition"))):tag.getBoolean("DiagonalHalfHeight")?2:tag.getBoolean("DiagonalFullWidth")?1:0;
        TileEntity raw=world.isBlockLoaded(support)?world.getTileEntity(support):null;
        if(raw instanceof TileEntityProgrammableDiagonalTrapdoor)return ((TileEntityProgrammableDiagonalTrapdoor)raw).getPosition();
        return raw instanceof TileEntityAnimatedScreenSelector?BlockProgrammableWall.geometry(world,support):0;
    }
    public static IBlockState wallState(IBlockState state) {
        return com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState()
                .withProperty(BlockProgrammableWall.FACING,state.getValue(FACING))
                .withProperty(BlockProgrammableWall.INVERTED,state.getValue(HALF)==DoorHalf.TOP);
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer) {
        return placementState(world,pos,side,x,y,z,placer,placer.getHeldItemMainhand());
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,net.minecraft.util.EnumHand hand) {
        return placementState(world,pos,side,x,y,z,placer,placer.getHeldItem(hand));
    }
    private IBlockState placementState(World world,BlockPos pos,EnumFacing side,float x,float y,float z,EntityLivingBase placer,ItemStack stack) {
        BlockPos support=pos.offset(side.getOpposite());int mode=placementMode(world,support,stack);
        IBlockState clicked=world.getBlockState(support);
        boolean diagonal=clicked.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)clicked.getBlock()).isDiagonalShape();
        boolean trapdoor=clicked.getBlock()==this;
        int clickedMode=diagonal?BlockProgrammableWall.geometry(world,support):placementMode(world,support,ItemStack.EMPTY);
        if((diagonal || trapdoor) && clickedMode==mode) {
            IBlockState plane=trapdoor?wallState(clicked):clicked;
            for(EnumFacing facing:new EnumFacing[]{plane.getValue(BlockProgrammableWall.FACING),plane.getValue(BlockProgrammableWall.FACING).getOpposite()})
                for(boolean inverted:new boolean[]{false,true}) {
                    IBlockState candidate=getDefaultState().withProperty(FACING,facing).withProperty(HALF,inverted?DoorHalf.TOP:DoorHalf.BOTTOM);
                    if(DiagonalPanelGeometry.samePlane(support,plane,pos,wallState(candidate),mode))return candidate;
                }
            if(side.getAxis()==EnumFacing.Axis.Y) return getDefaultState().withProperty(FACING,plane.getValue(BlockProgrammableWall.FACING))
                    .withProperty(HALF,plane.getValue(BlockProgrammableWall.INVERTED)?DoorHalf.TOP:DoorHalf.BOTTOM);
        }
        EnumFacing facing=placer.getHorizontalFacing().getOpposite();
        boolean inverted=side==EnumFacing.DOWN || side.getAxis().isHorizontal() && y>.5F;
        if(mode!=2){float normal=facing.getAxis()==EnumFacing.Axis.X?x:z;
            if(normal<1F/3F)facing=facing.getAxis()==EnumFacing.Axis.X?EnumFacing.WEST:EnumFacing.NORTH;
            else if(normal>2F/3F)facing=facing.getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.SOUTH;}
        return getDefaultState().withProperty(FACING,facing).withProperty(HALF,inverted?DoorHalf.TOP:DoorHalf.BOTTOM);
    }
    public static double[][] corners(IBlockState state,TileEntityProgrammableDiagonalTrapdoor tile,double pose) {
        return corners(state,tile,pose,tile==null || tile.getPosition()==2 || pose==0
                ?java.util.Collections.emptyList():tile.group());
    }
    /** Share one loaded membership snapshot across the moving mesh and artwork layout. */
    public static double[][] corners(IBlockState state,TileEntityProgrammableDiagonalTrapdoor tile,double pose,List<TileEntityProgrammableTrapdoor> group) {
        return DiagonalTrapdoorGeometry.corners(tile==null?0:tile.getPosition(),state.getValue(HALF)==DoorHalf.TOP,
                quarterTurns(state.getValue(FACING)),tile!=null && tile.isSliding(),tile!=null && tile.rotationReverse(group),pose,tile==null?1/16D:tile.motionHinge(),tile==null?15/16D:tile.motionTravel(),tile==null?1:tile.slideLiftDirection(group),tile!=null && tile.isSlideIntoWall());
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        TileEntity raw=world.getTileEntity(pos);double[] b=DiagonalTrapdoorGeometry.bounds(corners(state,raw instanceof TileEntityProgrammableDiagonalTrapdoor?(TileEntityProgrammableDiagonalTrapdoor)raw:null,state.getValue(OPEN)?1:0));
        return new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]);
    }
    /** Tight slices instead of filling the entire slanted panel's bounding volume. */
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> boxes,@Nullable Entity entity,boolean actual) {
        TileEntity raw=world.getTileEntity(pos);TileEntityProgrammableDiagonalTrapdoor tile=raw instanceof TileEntityProgrammableDiagonalTrapdoor?(TileEntityProgrammableDiagonalTrapdoor)raw:null;
        double[][] v=corners(state,tile,state.getValue(OPEN)?1:0);
        int across=state.getValue(OPEN) && (tile==null || !tile.isSliding())?16:1;
        for(int slice=0;slice<16;slice++)for(int column=0;column<across;column++) {
            // Split the long sloping edge; opened rotating panels still use the same rigid mesh.
            int axis=tile!=null && tile.getPosition()==2?4:2;double[][] cell=new double[8][3];
            for(int i=0;i<8;i++)for(int a=0;a<3;a++)cell[i][a]=v[i&~axis][a]+(v[i|axis][a]-v[i&~axis][a])*(slice+((i&axis)==0?0:1))/16D;
            if(across>1) {
                double[][] split=new double[8][3];
                for(int i=0;i<8;i++)for(int a=0;a<3;a++)split[i][a]=cell[i&~1][a]+(cell[i|1][a]-cell[i&~1][a])*(column+((i&1)==0?0:1))/across;
                cell=split;
            }
            double[] b=DiagonalTrapdoorGeometry.bounds(cell);
            addCollisionBoxToList(pos,entityBox,boxes,new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]));
        }
    }
    private static Vec3d vector(double[] v){return new Vec3d(v[0],v[1],v[2]);}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end) {
        TileEntity raw=world.getTileEntity(pos);double[][] v=corners(state,raw instanceof TileEntityProgrammableDiagonalTrapdoor?(TileEntityProgrammableDiagonalTrapdoor)raw:null,state.getValue(OPEN)?1:0);
        Vec3d origin=start.subtract(new Vec3d(pos)),direction=end.subtract(start);double nearest=Double.POSITIVE_INFINITY;EnumFacing hitFace=null;
        for(int[] face:TrapdoorGeometry.FACES) {
            Vec3d a=vector(v[face[0]]),u=vector(v[face[1]]).subtract(a),w=vector(v[face[3]]).subtract(a),n=u.crossProduct(w);
            double denominator=n.dotProduct(direction);if(Math.abs(denominator)<1e-9)continue;
            double t=n.dotProduct(a.subtract(origin))/denominator;if(t<0 || t>1 || t>=nearest)continue;
            Vec3d hit=origin.add(direction.scale(t)).subtract(a);double uu=u.dotProduct(u),ww=w.dotProduct(w),uw=u.dotProduct(w),hu=hit.dotProduct(u),hw=hit.dotProduct(w),det=uu*ww-uw*uw;
            double s=(hu*ww-hw*uw)/det,r=(hw*uu-hu*uw)/det;
            if(s>=-1e-8 && s<=1+1e-8 && r>=-1e-8 && r<=1+1e-8){nearest=t;hitFace=EnumFacing.getFacingFromVector((float)n.x,(float)n.y,(float)n.z);}
        }
        return hitFace==null?null:new RayTraceResult(start.add(direction.scale(nearest)),hitFace,pos);
    }
}
