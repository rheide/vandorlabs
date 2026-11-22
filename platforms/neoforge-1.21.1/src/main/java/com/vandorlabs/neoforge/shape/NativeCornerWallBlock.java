package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.persistence.WallSettings;
import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.render.DiagonalCornerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A beveled Programmable Wall between two walls meeting at a right angle, with the programmable
 * wall material and dialog. The flat Corner Wall joins flat walls at the same depth band; the
 * Diagonal Corner Wall follows the cross section of diagonal walls (size, lean and near or far
 * face) and joins only diagonal walls it continues exactly. Bevel shape, placement and joins are
 * the shared Corner Glass geometry, as on 1.12.2. The facing holds the bevel's turn; a diagonal
 * corner's size lives in its wall settings, as a diagonal wall's does.
 */
public final class NativeCornerWallBlock extends NativePanelBlock {
    public static final BooleanProperty INVERTED=NativeFlatWallBlock.INVERTED;
    public static final IntegerProperty DEPTH=NativeFlatWallBlock.DEPTH;
    private static final Direction[] SIDES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    /** Flat corners: depth*4+turn. Diagonal corners: size*16 + (turn | lean<<2 | far<<3). */
    private final CornerGlassGeometry[] geometry=new CornerGlassGeometry[48];
    private final VoxelShape[] shapes=new VoxelShape[48];
    private final NativeCornerWallGeometry[][] published=new NativeCornerWallGeometry[48][4];
    private final boolean sloped;
    private final MapCodec<NativeCornerWallBlock> codec;

    public NativeCornerWallBlock(Properties properties,boolean sloped){
        super(properties);
        this.sloped=sloped;
        codec=simpleCodec(p->new NativeCornerWallBlock(p,sloped));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(DEPTH,0).setValue(INVERTED,false));
        for(int at=0;at<(sloped?48:12);at++){
            geometry[at]=sloped?CornerGlassGeometry.diagonal(at>>4,at&15):new CornerGlassGeometry(at&3,at>>2,false);
            var boxes=geometry[at].collision(AABB::new);
            shapes[at]=NativeFlatWallGeometry.collision(boxes);
            AABB bounds=geometry[at].bounds(AABB::new);
            for(int joined=0;joined<4;joined++)published[at][joined]=new NativeCornerWallGeometry(geometry[at],joined,boxes,bounds);
        }
    }
    public boolean sloped(){return sloped;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,DEPTH,INVERTED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,NativePanelKind.WALL);}

    private static int turn(BlockState state){return state.getValue(FACING).get2DDataValue();}
    /** Flat: depth*4+turn. Diagonal: turn | lean<<2 | far<<3 (lean as INVERTED, far as DEPTH 2). */
    public int metadata(BlockState state){
        if(!sloped)return state.getValue(DEPTH)*4+turn(state);
        return turn(state)|(state.getValue(INVERTED)?4:0)|(state.getValue(DEPTH)!=0?8:0);
    }
    public BlockState stateFor(int metadata){
        BlockState state=defaultBlockState().setValue(FACING,Direction.from2DDataValue(metadata&3));
        if(sloped)return state.setValue(INVERTED,(metadata&4)!=0).setValue(DEPTH,(metadata&8)!=0?2:0);
        return state.setValue(DEPTH,Math.min(2,metadata>>2));
    }
    /** A diagonal corner's size, from its wall settings; flat corners are full. */
    public static int size(WallSettings walls){return walls==null?CornerGlassGeometry.FULL:WallSettings.geometry(walls.halfHeight,walls.fullWidth);}
    public int size(BlockGetter level,BlockPos pos){
        return sloped&&level.getBlockEntity(pos) instanceof NativeWallEntity wall?size(wall.wallSettings()):CornerGlassGeometry.FULL;
    }
    private int slot(BlockState state,int size){return sloped?size*16+metadata(state):metadata(state);}
    public CornerGlassGeometry geometry(BlockState state,int size){return geometry[slot(state,size)];}
    public CornerGlassGeometry geometry(BlockGetter level,BlockPos pos,BlockState state){return geometry(state,size(level,pos));}
    public NativeCornerWallGeometry publication(BlockState state,int size,int joined){return published[slot(state,size)][joined&3];}

    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes[slot(state,size(level,pos))];}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return false;}
    @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    // Corner Walls hold no contained fluid.
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return false;}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){return false;}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return ItemStack.EMPTY;}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){
        int turn=turn(state);
        turn=mirror==Mirror.LEFT_RIGHT?3-turn:mirror==Mirror.FRONT_BACK?(1-turn)&3:turn;
        return state.setValue(FACING,Direction.from2DDataValue(turn));
    }

    // Placement: the shared policy, with flat walls, diagonal walls and other corner walls as neighbours.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction side=context.getClickedFace();Level level=context.getLevel();BlockPos pos=context.getClickedPos();
        BlockPos supportPos=pos.relative(side.getOpposite());BlockState support=level.getBlockState(supportPos);
        // Stacking on or under a corner keeps its bevel.
        if(side.getAxis().isVertical()&&support.getBlock()==this)return stateFor(metadata(support));
        CornerGlassGeometry.Neighbor[] neighbors=new CornerGlassGeometry.Neighbor[4];
        for(int at=0;at<4;at++){BlockPos next=pos.relative(SIDES[at]);neighbors[at]=neighbor(level,next,level.getBlockState(next));}
        int stacked=-1;
        for(Direction vertical:new Direction[]{Direction.DOWN,Direction.UP}){
            BlockState other=level.getBlockState(pos.relative(vertical));
            if(other.getBlock()==this){stacked=metadata(other);break;}
        }
        Vec3 hit=context.getClickLocation();var player=context.getPlayer();
        double playerX=player==null?.5:player.getX()-pos.getX(),playerZ=player==null?.5:player.getZ()-pos.getZ();
        int clicked=side.getAxis().isHorizontal()?side(side.getOpposite()):-1;
        double cellX=hit.x-pos.getX(),cellZ=hit.z-pos.getZ();
        if(sloped)return stateFor(CornerGlassGeometry.placementDiagonal(placementSize(level,supportPos,context.getItemInHand()),clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
        return stateFor(CornerGlassGeometry.placement(false,clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ));
    }
    /** A diagonal corner placed against a diagonal wall or Diagonal Corner Wall takes its size; then a configured item's; then full. */
    public int placementSize(Level level,BlockPos support,ItemStack stack){
        BlockState clicked=level.getBlockState(support);
        int clickedSize=diagonalWall(clicked)?NativeDiagonalWallTopology.mode(level,support)
                :clicked.getBlock() instanceof NativeCornerWallBlock corner&&corner.sloped?corner.size(level,support):-1;
        var data=stack.isEmpty()?null:stack.get(DataComponents.BLOCK_ENTITY_DATA);
        var tag=data==null?null:data.copyTag();
        int configured=tag==null||!tag.contains(WallSettings.HALF)&&!tag.contains(WallSettings.FULL)?-1
                :WallSettings.geometry(tag.getBoolean(WallSettings.HALF),tag.getBoolean(WallSettings.FULL));
        return DiagonalCornerRules.placementSize(clickedSize,configured);
    }
    /** N/E/S/W numbering used by the shared corner geometry. */
    static int side(Direction face){return (face.get2DDataValue()+2)&3;}
    private static boolean diagonalWall(BlockState state){return state.getBlock() instanceof NativeDiagonalWallBlock block&&!block.isGlass();}
    private static boolean flatWall(BlockState state){return state.getBlock() instanceof NativeFlatWallBlock block&&!block.isPorthole();}
    /** Flat walls in the shared glass terms: north/south walls run along x with the depth band measured from north (mirrored facing south). */
    private static int glassDepth(BlockState wall){
        Direction facing=wall.getValue(NativeFlatWallBlock.FACING);int depth=wall.getValue(NativeFlatWallBlock.DEPTH);
        return facing==Direction.NORTH||facing==Direction.EAST?depth:2-depth;
    }
    private CornerGlassGeometry.Neighbor neighbor(Level level,BlockPos at,BlockState state){
        if(state.getBlock() instanceof NativeCornerWallBlock corner)return CornerGlassGeometry.Neighbor.corner(corner.geometry(level,at,state));
        if(sloped&&diagonalWall(state))
            return CornerGlassGeometry.Neighbor.diagonal(side(state.getValue(NativeDiagonalWallBlock.FACING)),NativeDiagonalWallTopology.mode(level,at),state.getValue(NativeDiagonalWallBlock.INVERTED));
        if(!sloped&&flatWall(state))
            return CornerGlassGeometry.Neighbor.straight(state.getValue(NativeFlatWallBlock.FACING).getAxis()==Direction.Axis.X,glassDepth(state));
        return CornerGlassGeometry.Neighbor.NONE;
    }

    // Joins: which of its two ends continue into a loaded neighbour, for hiding the rims they share.
    /** Bit 0: the end on the corner's first side joins; bit 1: the end on its second side. */
    public int joinedEnds(Level level,BlockPos pos,BlockState state,int size){
        int mask=0,turn=turn(state);CornerGlassGeometry current=geometry(state,size);
        for(int end=0;end<2;end++){
            Direction side=SIDES[end==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn)];
            BlockPos next=pos.relative(side);
            if(NativeFluidWorld.loaded(level,next)&&joinsEnd(level,current,side,next,level.getBlockState(next)))mask|=1<<end;
        }
        return mask;
    }
    private boolean joinsEnd(Level level,CornerGlassGeometry current,Direction side,BlockPos next,BlockState other){
        int direction=side(side);
        if(other.getBlock() instanceof NativeCornerWallBlock corner)return current.joinsEdge(direction,corner.geometry(level,next,other));
        if(sloped&&diagonalWall(other)){
            var settings=NativeDiagonalWallTopology.settings(level,next);int size=WallSettings.geometry(settings.halfHeight,settings.fullWidth);
            boolean porthole=((NativeDiagonalWallBlock)other.getBlock()).isPorthole();
            return DiagonalCornerRules.continues(current,true,direction,side(other.getValue(NativeDiagonalWallBlock.FACING)),size,other.getValue(NativeDiagonalWallBlock.INVERTED),
                    DiagonalCornerRules.wallAcceptsCorner(size,settings.join,porthole,settings.joinPortholes));
        }
        if(!sloped&&flatWall(other))return current.joinsStraight(direction,other.getValue(NativeFlatWallBlock.FACING).getAxis()==Direction.Axis.X,glassDepth(other));
        return false;
    }
    /** Whether the wall at {@code wall} continues the Corner Wall at {@code corner}. */
    public static boolean joins(Level level,BlockPos corner,BlockPos wall){
        BlockState state=level.getBlockState(corner);
        if(!(state.getBlock() instanceof NativeCornerWallBlock block)||corner.distManhattan(wall)!=1||corner.getY()!=wall.getY())return false;
        Direction side=Direction.fromDelta(wall.getX()-corner.getX(),0,wall.getZ()-corner.getZ());
        return side!=null&&block.joinsEnd(level,block.geometry(level,corner,state),side,wall,level.getBlockState(wall));
    }
    /** The topology publication for a loaded Corner Wall. */
    NativeCornerWallGeometry topology(Level level,BlockPos pos,BlockState state,NativeWallEntity host){
        int size=sloped?size(host.wallSettings()):CornerGlassGeometry.FULL;
        return publication(state,size,joinedEnds(level,pos,state,size));
    }
}
