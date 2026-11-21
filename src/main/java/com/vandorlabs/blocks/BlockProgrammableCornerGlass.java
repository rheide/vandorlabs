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
    /** Flat corners: depth*4+turn. Diagonal corners: size*16 + (turn | lean<<2 | far<<3). */
    private final CornerGlassGeometry[] geometry=new CornerGlassGeometry[48];
    @SuppressWarnings("unchecked")
    private final List<AxisAlignedBB>[] boxes=(List<AxisAlignedBB>[])new List<?>[48];
    private final AxisAlignedBB[] bounds=new AxisAlignedBB[48];
    private final boolean sloped;

    public BlockProgrammableCornerGlass(String name,boolean sloped){
        super(name);
        this.sloped=sloped;
        setDefaultState(getDefaultState().withProperty(TURN,0));
        for(int at=0;at<(sloped?48:12);at++){
            geometry[at]=sloped?CornerGlassGeometry.diagonal(at>>4,at&15):new CornerGlassGeometry(at&3,at>>2,false);
            boxes[at]=geometry[at].collision(AxisAlignedBB::new);
            bounds[at]=geometry[at].bounds(AxisAlignedBB::new);
        }
    }
    public boolean sloped(){return sloped;}
    /**
     * Corners render through their tile, so they keep only the properties their metadata and the
     * glass dialog use. The ordinary glass frame-connection flags would multiply each corner block
     * to over eighteen thousand states.
     */
    @Override protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this,TURN,ROTATED,DEPTH,SIZE);
    }
    /** Block metadata: flat depth*4+turn; diagonal turn | lean<<2 | far<<3 (lean in ROTATED, far as DEPTH 1). */
    private int index(IBlockState state){
        if(!sloped)return state.getValue(DEPTH)*4+state.getValue(TURN);
        return state.getValue(TURN)|(state.getValue(ROTATED)?4:0)|(state.getValue(DEPTH)==1?8:0);
    }
    /** A diagonal corner's size lives in its tile; flat corners and missing tiles use full width. */
    public static int size(IBlockAccess world,BlockPos pos){
        net.minecraft.tileentity.TileEntity tile=world==null||pos==null?null:world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).getCornerMode():CornerGlassGeometry.FULL;
    }
    private int slot(IBlockState state,int size){return sloped?size*16+index(state):index(state);}
    /** Geometry without world context: diagonal corners assume full width. */
    public CornerGlassGeometry geometry(IBlockState state){return geometry[slot(state,CornerGlassGeometry.FULL)];}
    public CornerGlassGeometry geometry(IBlockAccess world,BlockPos pos,IBlockState state){return geometry[slot(state,size(world,pos))];}
    public CornerGlassGeometry geometry(IBlockState state,TileEntityProgrammableGlass tile){return geometry[slot(state,tile==null?CornerGlassGeometry.FULL:tile.getCornerMode())];}
    @Override public int getMetaFromState(IBlockState state){return index(state);}
    @Override public IBlockState getStateFromMeta(int meta){
        if(sloped)return getDefaultState().withProperty(TURN,meta&3).withProperty(ROTATED,(meta&4)!=0).withProperty(DEPTH,(meta&8)!=0?1:0);
        return getDefaultState().withProperty(TURN,meta&3).withProperty(DEPTH,Math.min(2,meta>>2));
    }
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(TURN,(state.getValue(TURN)+rotation.ordinal())&3);}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){
        int turn=state.getValue(TURN);
        return state.withProperty(TURN,mirror==Mirror.LEFT_RIGHT?3-turn:mirror==Mirror.FRONT_BACK?(1-turn)&3:turn);
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,net.minecraft.util.EnumHand hand){
        return placement(world,pos,side,x,z,placer,placer==null?net.minecraft.item.ItemStack.EMPTY:placer.getHeldItem(hand));
    }
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        return placement(world,pos,side,x,z,placer,placer==null?net.minecraft.item.ItemStack.EMPTY:placer.getHeldItemMainhand());
    }
    private IBlockState placement(World world,BlockPos pos,EnumFacing side,float x,float z,EntityLivingBase placer,net.minecraft.item.ItemStack stack){
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
        // Forge supplies hit fractions relative to the clicked block; use the new cell.
        double cellX=x-side.getFrontOffsetX(),cellZ=z-side.getFrontOffsetZ();
        double playerX=placer==null?.5:placer.posX-pos.getX(),playerZ=placer==null?.5:placer.posZ-pos.getZ();
        int clicked=side.getAxis().isHorizontal()?side(side.getOpposite()):-1;
        if(sloped)return getStateFromMeta(CornerGlassGeometry.placementDiagonal(placementSize(world,supportPos,stack),clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
        return getStateFromMeta(CornerGlassGeometry.placement(false,clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
    }
    private static boolean diagonalWall(IBlockState state){
        return state.getBlock() instanceof BlockProgrammableWall&&((BlockProgrammableWall)state.getBlock()).isDiagonalShape();
    }
    /**
     * A diagonal corner placed against any diagonal block (glass, porthole or another diagonal
     * corner) takes that block's size. Otherwise a configured item keeps its own; plain items are
     * full width.
     */
    public int placementSize(World world,BlockPos support,net.minecraft.item.ItemStack stack){
        IBlockState clicked=world.getBlockState(support);
        int clickedSize=diagonalWall(clicked)?BlockProgrammableWall.geometry(world,support)
                :clicked.getBlock() instanceof BlockProgrammableCornerGlass&&((BlockProgrammableCornerGlass)clicked.getBlock()).sloped?size(world,support):-1;
        net.minecraft.nbt.NBTTagCompound tag=stack.isEmpty()?null:stack.getSubCompound("BlockEntityTag");
        int configured=tag!=null&&tag.hasKey(TileEntityProgrammableGlass.CORNER_MODE_TAG,99)?tag.getInteger(TileEntityProgrammableGlass.CORNER_MODE_TAG):-1;
        return com.vandorlabs.render.DiagonalCornerRules.placementSize(clickedSize,configured);
    }
    private CornerGlassGeometry.Neighbor neighbor(IBlockAccess world,BlockPos at,IBlockState state){
        if(state.getBlock() instanceof BlockProgrammableCornerGlass)
            return CornerGlassGeometry.Neighbor.corner(((BlockProgrammableCornerGlass)state.getBlock()).geometry(world,at,state));
        if(sloped&&diagonalWall(state))
            return CornerGlassGeometry.Neighbor.diagonal(side(state.getValue(BlockProgrammableWall.FACING)),BlockProgrammableWall.geometry(world,at),state.getValue(BlockProgrammableWall.INVERTED));
        if(state.getBlock() instanceof BlockProgrammableGlass)
            return CornerGlassGeometry.Neighbor.straight(state.getValue(ROTATED),state.getValue(DEPTH));
        return CornerGlassGeometry.Neighbor.NONE;
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){return state;}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){return state;}
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state){return net.minecraft.util.EnumBlockRenderType.INVISIBLE;}
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
            if(joinsEnd(state,pos,side,other,world,neighbor)&&TextureJoins.allows(world.getTileEntity(pos),pos,neighbor))mask&=~(1<<edge);
        }
        for(EnumFacing vertical:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}){
            BlockPos neighbor=pos.offset(vertical);
            if(world instanceof World&&!((World)world).isBlockLoaded(neighbor))continue;
            IBlockState other=world.getBlockState(neighbor);
            if(other.getBlock() instanceof BlockProgrammableCornerGlass&&enabled(world,neighbor)
                    &&geometry(world,pos,state).joinsVertical(vertical==EnumFacing.UP,((BlockProgrammableCornerGlass)other.getBlock()).geometry(world,neighbor,other))
                    &&TextureJoins.allows(world.getTileEntity(pos),pos,neighbor))mask&=~(vertical==EnumFacing.DOWN?4:8);
        }
        return mask;
    }
    private boolean joinsEnd(IBlockState state,BlockPos corner,EnumFacing side,IBlockState other,IBlockAccess world,BlockPos neighbor){
        int direction=side(side);
        CornerGlassGeometry current=geometry(world,corner,state);
        if(sloped&&diagonalWall(other))
            return com.vandorlabs.render.DiagonalCornerRules.continues(current,enabled(world,corner),direction,side(other.getValue(BlockProgrammableWall.FACING)),
                    BlockProgrammableWall.geometry(world,neighbor),other.getValue(BlockProgrammableWall.INVERTED),PanelJoins.cornerDiagonalEnabled(world,neighbor));
        if(!(other.getBlock() instanceof BlockProgrammableGlass)||!enabled(world,neighbor))return false;
        if(other.getBlock() instanceof BlockProgrammableCornerGlass)
            return current.joinsEdge(direction,((BlockProgrammableCornerGlass)other.getBlock()).geometry(world,neighbor,other));
        return current.joinsStraight(direction,other.getValue(ROTATED),other.getValue(DEPTH));
    }
    /**
     * Whether diagonal glass at {@code wall} continues the Diagonal Corner Glass at {@code corner}:
     * its slope crosses their shared face exactly where the bevel does, over the same heights.
     */
    public static boolean joinsDiagonalWall(IBlockAccess world,BlockPos corner,BlockPos wall){
        IBlockState state=world.getBlockState(corner),other=world.getBlockState(wall);
        if(!(state.getBlock() instanceof BlockProgrammableCornerGlass)||!((BlockProgrammableCornerGlass)state.getBlock()).sloped||!diagonalWall(other))return false;
        if(corner.distanceSq(wall)!=1||corner.getY()!=wall.getY()||!enabled(world,corner))return false;
        EnumFacing side=EnumFacing.getFacingFromVector(wall.getX()-corner.getX(),0,wall.getZ()-corner.getZ());
        return ((BlockProgrammableCornerGlass)state.getBlock()).joinsEnd(state,corner,side,other,world,wall);
    }
    private static int side(EnumFacing face){return (face.getHorizontalIndex()+2)&3;}
    public boolean joinsStraight(IBlockAccess world,BlockPos corner,IBlockState state,BlockPos straight,IBlockState other){
        return joinsStraightAt(world,corner,state,straight,straight,other);
    }
    /** Join a straight pane described by {@code other} and owned at {@code owner}, as if it sat at {@code level}. */
    boolean joinsStraightAt(IBlockAccess world,BlockPos corner,IBlockState state,BlockPos level,BlockPos owner,IBlockState other){
        if(corner.distanceSq(level)!=1||level.getY()!=corner.getY()||!enabled(world,corner)||!enabled(world,owner))return false;
        EnumFacing side=EnumFacing.getFacingFromVector(level.getX()-corner.getX(),0,level.getZ()-corner.getZ());
        return joinsEnd(state,corner,side,other,world,owner);
    }
}
