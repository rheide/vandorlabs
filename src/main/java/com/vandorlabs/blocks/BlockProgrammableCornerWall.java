package com.vandorlabs.blocks;

import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.render.DiagonalCornerRules;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * A beveled Programmable Wall between two walls meeting at a right angle, with the programmable
 * wall material and dialog. The flat Corner Wall joins flat walls at the same depth band; the
 * Diagonal Corner Wall follows the cross section of diagonal walls (size, lean and near or far
 * face) and joins only diagonal walls it continues exactly. The bevel shape, placement and join
 * tests are the shared Corner Glass geometry, so walls and glass behave alike on both versions.
 * The horizontal facing holds the bevel's turn; the wall tile holds a diagonal corner's size.
 */
public final class BlockProgrammableCornerWall extends BlockAnimatedScreenSelector {
    private static final EnumFacing[] SIDES={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
    /** Flat corners: depth*4+turn. Diagonal corners: size*16 + (turn | lean<<2 | far<<3). */
    private final CornerGlassGeometry[] geometry=new CornerGlassGeometry[48];
    @SuppressWarnings("unchecked")
    private final List<AxisAlignedBB>[] boxes=(List<AxisAlignedBB>[])new List<?>[48];
    private final AxisAlignedBB[] bounds=new AxisAlignedBB[48];
    private final boolean sloped;

    public BlockProgrammableCornerWall(String name,boolean sloped){
        super(name);
        this.sloped=sloped;
        setDefaultState(blockState.getBaseState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH)
                .withProperty(BlockProgrammableWall.INVERTED,false).withProperty(BlockProgrammableWall.DEPTH,0));
        for(int at=0;at<(sloped?48:12);at++){
            geometry[at]=sloped?CornerGlassGeometry.diagonal(at>>4,at&15):new CornerGlassGeometry(at&3,at>>2,false);
            boxes[at]=geometry[at].collision(AxisAlignedBB::new);
            bounds[at]=geometry[at].bounds(AxisAlignedBB::new);
        }
        useNeighborBrightness=true;
    }
    public boolean sloped(){return sloped;}
    /** Starts with the programmable wall's default artwork, like the walls it joins. */
    @Override public TileEntity createNewTileEntity(World world,int meta){return new TileEntityAnimatedScreenSelector();}
    @Override protected IProperty<EnumFacing> facingProperty(){return BlockProgrammableWall.FACING;}
    @Override protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this,BlockProgrammableWall.FACING,BlockProgrammableWall.INVERTED,BlockProgrammableWall.DEPTH);
    }
    private static int turn(IBlockState state){return state.getValue(BlockProgrammableWall.FACING).getHorizontalIndex();}
    /** Block metadata: flat depth*4+turn; diagonal turn | lean<<2 | far<<3 (lean as INVERTED, far as DEPTH 1). */
    private int index(IBlockState state){
        if(!sloped)return state.getValue(BlockProgrammableWall.DEPTH)*4+turn(state);
        return turn(state)|(state.getValue(BlockProgrammableWall.INVERTED)?4:0)|(state.getValue(BlockProgrammableWall.DEPTH)==1?8:0);
    }
    @Override public int getMetaFromState(IBlockState state){return index(state);}
    @Override public IBlockState getStateFromMeta(int meta){
        IBlockState state=getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.getHorizontal(meta&3));
        if(sloped)return state.withProperty(BlockProgrammableWall.INVERTED,(meta&4)!=0).withProperty(BlockProgrammableWall.DEPTH,(meta&8)!=0?1:0);
        return state.withProperty(BlockProgrammableWall.DEPTH,Math.min(2,meta>>2));
    }
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){
        int turn=turn(state);
        turn=mirror==Mirror.LEFT_RIGHT?3-turn:mirror==Mirror.FRONT_BACK?(1-turn)&3:turn;
        return state.withProperty(BlockProgrammableWall.FACING,EnumFacing.getHorizontal(turn));
    }
    /** A diagonal corner's size lives in its tile, as a diagonal wall's does. */
    public static int size(IBlockAccess world,BlockPos pos){return BlockProgrammableWall.geometry(world,pos);}
    private int slot(IBlockState state,int size){return sloped?size*16+index(state):index(state);}
    public CornerGlassGeometry geometry(IBlockAccess world,BlockPos pos,IBlockState state){return geometry[slot(state,size(world,pos))];}
    public CornerGlassGeometry geometry(IBlockState state,TileEntityAnimatedScreenSelector tile){
        int size=tile==null?CornerGlassGeometry.FULL:com.vandorlabs.persistence.WallSettings.geometry(tile.isDiagonalHalfHeight(),tile.isDiagonalFullWidth());
        return geometry[slot(state,size)];
    }

    // Placement: the shared policy, with flat walls, diagonal walls and other corner walls as neighbours.
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,EnumHand hand){
        return placement(world,pos,side,x,z,placer,placer==null?ItemStack.EMPTY:placer.getHeldItem(hand));
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        return placement(world,pos,side,x,z,placer,placer==null?ItemStack.EMPTY:placer.getHeldItemMainhand());
    }
    private IBlockState placement(World world,BlockPos pos,EnumFacing side,float x,float z,EntityLivingBase placer,ItemStack stack){
        BlockPos supportPos=pos.offset(side.getOpposite());
        IBlockState support=world.getBlockState(supportPos);
        // Stacking on or under a corner keeps its bevel.
        if(!side.getAxis().isHorizontal()&&support.getBlock()==this)return getStateFromMeta(index(support));
        CornerGlassGeometry.Neighbor[] neighbors=new CornerGlassGeometry.Neighbor[4];
        for(int at=0;at<4;at++){BlockPos next=pos.offset(SIDES[at]);neighbors[at]=neighbor(world,next,world.getBlockState(next));}
        int stacked=-1;
        for(EnumFacing vertical:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}){
            IBlockState other=world.getBlockState(pos.offset(vertical));
            if(other.getBlock()==this){stacked=index(other);break;}
        }
        double cellX=x-side.getFrontOffsetX(),cellZ=z-side.getFrontOffsetZ();
        double playerX=placer==null?.5:placer.posX-pos.getX(),playerZ=placer==null?.5:placer.posZ-pos.getZ();
        int clicked=side.getAxis().isHorizontal()?side(side.getOpposite()):-1;
        if(sloped)return getStateFromMeta(CornerGlassGeometry.placementDiagonal(placementSize(world,supportPos,stack),clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
        return getStateFromMeta(CornerGlassGeometry.placement(false,clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
    }
    /** A diagonal corner placed against a diagonal wall or diagonal corner wall takes its size. */
    public int placementSize(World world,BlockPos support,ItemStack stack){
        IBlockState clicked=world.getBlockState(support);
        int clickedSize=PanelJoins.diagonal(clicked)?BlockProgrammableWall.geometry(world,support)
                :clicked.getBlock() instanceof BlockProgrammableCornerWall&&((BlockProgrammableCornerWall)clicked.getBlock()).sloped?size(world,support):-1;
        net.minecraft.nbt.NBTTagCompound tag=stack.isEmpty()?null:stack.getSubCompound("BlockEntityTag");
        int configured=tag==null?-1:tag.getBoolean("DiagonalHalfHeight")?CornerGlassGeometry.WIDE:tag.getBoolean("DiagonalFullWidth")?CornerGlassGeometry.FULL
                :tag.hasKey("DiagonalHalfHeight")?CornerGlassGeometry.TALL:-1;
        return DiagonalCornerRules.placementSize(clickedSize,configured);
    }
    static int side(EnumFacing face){return (face.getHorizontalIndex()+2)&3;}
    /**
     * Flat walls in the shared glass terms: a wall facing north or south runs along x with its
     * depth band from the north face (mirrored when facing south); east or west runs along z.
     */
    static CornerGlassGeometry.Neighbor flatWall(IBlockState state){
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);int depth=state.getValue(BlockProgrammableWall.DEPTH);
        boolean rotated=facing.getAxis()==EnumFacing.Axis.X;
        int glassDepth=facing==EnumFacing.NORTH||facing==EnumFacing.EAST?depth:2-depth;
        return CornerGlassGeometry.Neighbor.straight(rotated,glassDepth);
    }
    static boolean flatWall(IBlockState state,IBlockAccess world,BlockPos pos){
        return state.getBlock() instanceof BlockProgrammableWall&&((BlockProgrammableWall)state.getBlock()).getShape()==BlockProgrammableWall.Shape.PLAIN
                &&!(state.getBlock() instanceof BlockProgrammableDiagonalGlass);
    }
    private CornerGlassGeometry.Neighbor neighbor(IBlockAccess world,BlockPos at,IBlockState state){
        if(state.getBlock() instanceof BlockProgrammableCornerWall)
            return CornerGlassGeometry.Neighbor.corner(((BlockProgrammableCornerWall)state.getBlock()).geometry(world,at,state));
        if(sloped&&PanelJoins.diagonal(state))
            return CornerGlassGeometry.Neighbor.diagonal(side(state.getValue(BlockProgrammableWall.FACING)),BlockProgrammableWall.geometry(world,at),state.getValue(BlockProgrammableWall.INVERTED));
        if(!sloped&&flatWall(state,world,at))return flatWall(state);
        return CornerGlassGeometry.Neighbor.NONE;
    }

    // Joins: which of its two ends continue into a neighbour, for hiding the rims they share.
    /** Bit 0: the end on the corner's first side joins; bit 1: the end on its second side. */
    public int joinedEnds(IBlockAccess world,BlockPos pos,IBlockState state){
        int mask=0,turn=turn(state);
        for(int end=0;end<2;end++){
            EnumFacing side=SIDES[end==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn)];
            BlockPos next=pos.offset(side);
            if(world instanceof World&&!((World)world).isBlockLoaded(next))continue;
            if(joinsEnd(world,pos,state,side,next,world.getBlockState(next)))mask|=1<<end;
        }
        return mask;
    }
    private boolean joinsEnd(IBlockAccess world,BlockPos corner,IBlockState state,EnumFacing side,BlockPos next,IBlockState other){
        int direction=side(side);
        CornerGlassGeometry current=geometry(world,corner,state);
        if(other.getBlock() instanceof BlockProgrammableCornerWall)
            return current.joinsEdge(direction,((BlockProgrammableCornerWall)other.getBlock()).geometry(world,next,other));
        if(sloped&&PanelJoins.diagonal(other))
            return DiagonalCornerRules.continues(current,true,direction,side(other.getValue(BlockProgrammableWall.FACING)),
                    BlockProgrammableWall.geometry(world,next),other.getValue(BlockProgrammableWall.INVERTED),PanelJoins.cornerDiagonalEnabled(world,next));
        if(!sloped&&flatWall(other,world,next)){
            CornerGlassGeometry.Neighbor flat=flatWall(other);
            return current.joinsStraight(direction,other.getValue(BlockProgrammableWall.FACING).getAxis()==EnumFacing.Axis.X,
                    other.getValue(BlockProgrammableWall.FACING)==EnumFacing.NORTH||other.getValue(BlockProgrammableWall.FACING)==EnumFacing.EAST
                            ?other.getValue(BlockProgrammableWall.DEPTH):2-other.getValue(BlockProgrammableWall.DEPTH));
        }
        return false;
    }
    /** Whether the wall at {@code wall} continues the Corner Wall at {@code corner}. */
    public static boolean joins(IBlockAccess world,BlockPos corner,BlockPos wall){
        IBlockState state=world.getBlockState(corner);
        if(!(state.getBlock() instanceof BlockProgrammableCornerWall)||corner.distanceSq(wall)!=1||corner.getY()!=wall.getY())return false;
        EnumFacing side=EnumFacing.getFacingFromVector(wall.getX()-corner.getX(),0,wall.getZ()-corner.getZ());
        return ((BlockProgrammableCornerWall)state.getBlock()).joinsEnd(world,corner,state,side,wall,world.getBlockState(wall));
    }

    // Shape and rendering: the tile renderer draws the bent wall mesh.
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.INVISIBLE;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean isSideSolid(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return false;}
    @Override public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing face){return net.minecraft.block.state.BlockFaceShape.UNDEFINED;}
    @Override public net.minecraft.util.BlockRenderLayer getBlockLayer(){return net.minecraft.util.BlockRenderLayer.CUTOUT;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return bounds[slot(state,size(world,pos))];}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return NULL_AABB;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> result,Entity entity,boolean actual){
        for(AxisAlignedBB box:boxes[slot(state,size(world,pos))])addCollisionBoxToList(pos,query,result,box);
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        RayTraceResult found=null;double distance=Double.POSITIVE_INFINITY;
        for(AxisAlignedBB box:boxes[slot(state,size(world,pos))]){
            RayTraceResult hit=rayTrace(pos,start,end,box);
            if(hit==null)continue;
            double next=start.squareDistanceTo(hit.hitVec);
            if(next<distance){distance=next;found=hit;}
        }
        return found;
    }
}
