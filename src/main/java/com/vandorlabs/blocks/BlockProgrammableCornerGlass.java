package com.vandorlabs.blocks;

import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.util.List;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A miter between adjacent walls, with the usual programmable-glass tile and editor. */
public final class BlockProgrammableCornerGlass extends BlockProgrammableGlass {
    public static final PropertyInteger TURN=PropertyInteger.create("corner",0,3);
    private static final EnumFacing[] SIDES={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
    private final CornerGlassGeometry[] geometry=new CornerGlassGeometry[12];
    @SuppressWarnings("unchecked")
    private final List<AxisAlignedBB>[] boxes=(List<AxisAlignedBB>[])new List<?>[12];
    private final AxisAlignedBB[] bounds=new AxisAlignedBB[12];
    private final boolean sloped;

    public BlockProgrammableCornerGlass(String name,boolean sloped){
        super(name);
        this.sloped=sloped;
        setDefaultState(getDefaultState().withProperty(TURN,0));
        for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++){
            int at=depth*4+turn;
            geometry[at]=new CornerGlassGeometry(turn,depth,sloped);
            boxes[at]=geometry[at].collision(AxisAlignedBB::new);
            bounds[at]=geometry[at].bounds(AxisAlignedBB::new);
        }
    }
    @Override protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this,TURN,ROTATED,DEPTH,SIZE,TOP,BOTTOM,LEFT,RIGHT,INNER_TL,INNER_TR,INNER_BL,INNER_BR);
    }
    private int index(IBlockState state){return state.getValue(DEPTH)*4+state.getValue(TURN);}
    public CornerGlassGeometry geometry(IBlockState state){return geometry[index(state)];}
    @Override public int getMetaFromState(IBlockState state){return index(state);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(TURN,meta&3).withProperty(DEPTH,Math.min(2,meta>>2));}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(TURN,(state.getValue(TURN)+rotation.ordinal())&3);}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){
        int turn=state.getValue(TURN);
        return state.withProperty(TURN,mirror==Mirror.LEFT_RIGHT?3-turn:mirror==Mirror.FRONT_BACK?(1-turn)&3:turn);
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,net.minecraft.util.EnumHand hand){
        return getStateForPlacement(world,pos,side,x,y,z,meta,placer);
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        IBlockState support=world.getBlockState(pos.offset(side.getOpposite()));
        // Stacking on or under a corner keeps its bevel.
        if(!side.getAxis().isHorizontal()&&support.getBlock() instanceof BlockProgrammableCornerGlass)
            return getStateFromMeta(((BlockProgrammableCornerGlass)support.getBlock()).index(support));
        CornerGlassGeometry.Neighbor[] neighbors=new CornerGlassGeometry.Neighbor[4];
        for(int at=0;at<4;at++)neighbors[at]=neighbor(world.getBlockState(pos.offset(SIDES[at])));
        int stacked=-1;
        for(EnumFacing vertical:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}){
            IBlockState other=world.getBlockState(pos.offset(vertical));
            if(other.getBlock()==this){stacked=index(other);break;}
        }
        // Forge supplies hit fractions relative to the clicked block; use the new cell.
        double cellX=x-side.getFrontOffsetX(),cellZ=z-side.getFrontOffsetZ();
        double playerX=placer==null?.5:placer.posX-pos.getX(),playerZ=placer==null?.5:placer.posZ-pos.getZ();
        return getStateFromMeta(CornerGlassGeometry.placement(sloped,side.getAxis().isHorizontal()?side(side.getOpposite()):-1,
                cellX,cellZ,neighbors,stacked,playerX,playerZ));
    }
    private static CornerGlassGeometry.Neighbor neighbor(IBlockState state){
        if(state.getBlock() instanceof BlockProgrammableCornerGlass){
            BlockProgrammableCornerGlass corner=(BlockProgrammableCornerGlass)state.getBlock();
            return CornerGlassGeometry.Neighbor.corner(state.getValue(TURN),state.getValue(DEPTH),corner.sloped);
        }
        if(state.getBlock() instanceof BlockProgrammableGlass)
            return CornerGlassGeometry.Neighbor.straight(state.getValue(ROTATED),state.getValue(DEPTH));
        return CornerGlassGeometry.Neighbor.NONE;
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){return state;}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){return state;}
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state){return net.minecraft.util.EnumBlockRenderType.INVISIBLE;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return bounds[index(state)];}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return NULL_AABB;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> result,Entity entity,boolean actual){
        for(AxisAlignedBB box:boxes[index(state)])addCollisionBoxToList(pos,query,result,box);
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        RayTraceResult found=null;double distance=Double.POSITIVE_INFINITY;
        for(AxisAlignedBB box:boxes[index(state)]){
            RayTraceResult hit=rayTrace(pos,start,end,box);
            if(hit==null)continue;
            double next=start.squareDistanceTo(hit.hitVec);
            if(next<distance){distance=next;found=hit;}
        }
        return found;
    }
    private static boolean enabled(IBlockAccess world,BlockPos pos){
        return world.getTileEntity(pos) instanceof TileEntityProgrammableGlass
                &&((TileEntityProgrammableGlass)world.getTileEntity(pos)).isJoin();
    }
    public int frameMask(IBlockAccess world,BlockPos pos,IBlockState state){
        if(!enabled(world,pos))return 15;
        int mask=15,turn=state.getValue(TURN);
        for(int edge=0;edge<2;edge++){
            EnumFacing side=SIDES[edge==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn)];
            BlockPos neighbor=pos.offset(side);
            if(world instanceof World&&!((World)world).isBlockLoaded(neighbor))continue;
            IBlockState other=world.getBlockState(neighbor);
            if(joinsEnd(state,side,other,world,neighbor)&&TextureJoins.allows(world.getTileEntity(pos),pos,neighbor))mask&=~(1<<edge);
        }
        for(EnumFacing vertical:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}){
            BlockPos neighbor=pos.offset(vertical);
            if(world instanceof World&&!((World)world).isBlockLoaded(neighbor))continue;
            IBlockState other=world.getBlockState(neighbor);
            if(other.getBlock() instanceof BlockProgrammableCornerGlass&&enabled(world,neighbor)
                    &&geometry(state).joinsVertical(vertical==EnumFacing.UP,((BlockProgrammableCornerGlass)other.getBlock()).geometry(other))
                    &&TextureJoins.allows(world.getTileEntity(pos),pos,neighbor))mask&=~(vertical==EnumFacing.DOWN?4:8);
        }
        return mask;
    }
    private boolean joinsEnd(IBlockState state,EnumFacing side,IBlockState other,IBlockAccess world,BlockPos neighbor){
        if(!(other.getBlock() instanceof BlockProgrammableGlass)||!enabled(world,neighbor))return false;
        int direction=side(side);
        CornerGlassGeometry current=geometry(state);
        if(other.getBlock() instanceof BlockProgrammableCornerGlass)
            return current.joinsEdge(direction,((BlockProgrammableCornerGlass)other.getBlock()).geometry(other));
        return current.joinsStraight(direction,other.getValue(ROTATED),other.getValue(DEPTH));
    }
    private static int side(EnumFacing face){return (face.getHorizontalIndex()+2)&3;}
    public boolean joinsStraight(IBlockAccess world,BlockPos corner,IBlockState state,BlockPos straight,IBlockState other){
        return joinsStraightAt(world,corner,state,straight,straight,other);
    }
    /** Join a straight pane described by {@code other} and owned at {@code owner}, as if it sat at {@code level}. */
    boolean joinsStraightAt(IBlockAccess world,BlockPos corner,IBlockState state,BlockPos level,BlockPos owner,IBlockState other){
        if(corner.distanceSq(level)!=1||level.getY()!=corner.getY()||!enabled(world,corner)||!enabled(world,owner))return false;
        EnumFacing side=EnumFacing.getFacingFromVector(level.getX()-corner.getX(),0,level.getZ()-corner.getZ());
        return joinsEnd(state,side,other,world,owner);
    }
}
