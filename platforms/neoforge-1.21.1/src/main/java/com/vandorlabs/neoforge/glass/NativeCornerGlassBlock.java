package com.vandorlabs.neoforge.glass;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.NativeTextureJoins;
import com.vandorlabs.neoforge.placement.NativePlacementHit;
import com.vandorlabs.neoforge.shape.NativeFlatWallGeometry;
import com.vandorlabs.render.CornerGlassGeometry;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class NativeCornerGlassBlock extends NativeGlassBlock {
    public static final IntegerProperty TURN=IntegerProperty.create("corner",0,3);
    /** Diagonal corner size (tall, full, wide), as on diagonal glass. Flat corners keep it full. */
    public static final IntegerProperty CORNER_SIZE=IntegerProperty.create("corner_size",0,2);
    private static final Direction[] SIDES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static final class Shape {
        final CornerGlassGeometry geometry;
        final List<AABB> boxes;
        final VoxelShape collision,outline;
        private NativeGlassGeometry.Snapshot fluidGeometry;
        Shape(CornerGlassGeometry geometry,List<AABB> boxes,VoxelShape collision,VoxelShape outline){
            this.geometry=geometry;this.boxes=boxes;this.collision=collision;this.outline=outline;
        }
        synchronized NativeGlassGeometry.Snapshot fluidGeometry(){
            if(fluidGeometry==null)fluidGeometry=new NativeGlassGeometry.Snapshot(
                    NativeGlassGeometry.straight(false,0).scalar(),boxes,geometry.bounds(AABB::new),
                    com.vandorlabs.neoforge.fluid.NativeFluidVolume.build(boxes,geometry::side),outline);
            return fluidGeometry;
        }
    }
    /** Flat corners: depth*4+turn. Diagonal corners: size*16 + (turn | lean<<2 | far<<3). */
    private final Shape[] shapes=new Shape[48];
    private final MapCodec<NativeCornerGlassBlock> codec;
    private final boolean sloped;

    public NativeCornerGlassBlock(Properties properties,Supplier<BlockEntityType<?>> type,boolean sloped){
        super(properties,type);
        this.sloped=sloped;
        codec=simpleCodec(p->new NativeCornerGlassBlock(p,type,sloped));
        registerDefaultState(defaultBlockState().setValue(TURN,0).setValue(CORNER_SIZE,CornerGlassGeometry.FULL));
        for(int at=0;at<(sloped?48:12);at++){
            var geometry=sloped?CornerGlassGeometry.diagonal(at>>4,at&15):new CornerGlassGeometry(at&3,at>>2,false);
            var boxes=geometry.collision(AABB::new);
            shapes[at]=new Shape(geometry,boxes,NativeFlatWallGeometry.collision(boxes),
                    Shapes.create(geometry.bounds(AABB::new)));
        }
    }
    @Override protected MapCodec<? extends Block> codec(){return codec;}
    @Override protected MenuType<NativeGlassMenu> menuType(){return NativeGlassRegistrations.MENU.get();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(TURN,CORNER_SIZE);
    }
    public boolean sloped(){return sloped;}
    /**
     * Diagonal corner metadata ({@code turn | lean<<2 | far<<3}). Lean is ROTATED and far is DEPTH 2;
     * corners saved before 2.12 stored lean as DEPTH 1.
     */
    public static int metadata(BlockState state){
        return com.vandorlabs.render.DiagonalCornerRules.legacyMetadata(state.getValue(TURN),state.getValue(DEPTH))|(state.getValue(ROTATED)?4:0);
    }
    /** The diagonal corner state for {@code metadata} and {@code size}. */
    public BlockState diagonalState(BlockState base,int metadata,int size){
        return base.setValue(TURN,metadata&3).setValue(ROTATED,(metadata&4)!=0).setValue(DEPTH,(metadata&8)!=0?2:0).setValue(CORNER_SIZE,size);
    }
    private int slot(BlockState state){return sloped?state.getValue(CORNER_SIZE)*16+metadata(state):state.getValue(DEPTH)*4+state.getValue(TURN);}
    private Shape shape(BlockState state){return shapes[slot(state)];}
    public CornerGlassGeometry projection(BlockState state){return shape(state).geometry;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state).outline;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state).collision;}
    @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter level,BlockPos pos,Vec3 start,Vec3 end){
        return AABB.clip(shape(state).boxes,start,end,pos);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(TURN,(state.getValue(TURN)+rotation.ordinal())&3);}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){
        int turn=state.getValue(TURN);
        return state.setValue(TURN,mirror==Mirror.LEFT_RIGHT?3-turn:mirror==Mirror.FRONT_BACK?(1-turn)&3:turn);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction side=context.getClickedFace();
        Level level=context.getLevel();BlockPos pos=context.getClickedPos();
        BlockState support=level.getBlockState(pos.relative(side.getOpposite()));
        // Stacking on or under a corner keeps its bevel.
        if(side.getAxis().isVertical()&&support.getBlock()==this)
            return defaultBlockState().setValue(TURN,support.getValue(TURN)).setValue(DEPTH,support.getValue(DEPTH))
                    .setValue(ROTATED,support.getValue(ROTATED)).setValue(CORNER_SIZE,support.getValue(CORNER_SIZE));
        CornerGlassGeometry.Neighbor[] neighbors=new CornerGlassGeometry.Neighbor[4];
        for(int at=0;at<4;at++){BlockPos next=pos.relative(SIDES[at]);neighbors[at]=neighbor(level,next,level.getBlockState(next));}
        int stacked=-1;
        for(Direction vertical:new Direction[]{Direction.DOWN,Direction.UP}){
            BlockState other=level.getBlockState(pos.relative(vertical));
            if(other.getBlock()==this){stacked=sloped?metadata(other):other.getValue(DEPTH)*4+other.getValue(TURN);break;}
        }
        // Use hit and player coordinates relative to the new cell.
        Vec3 hit=context.getClickLocation();
        var player=context.getPlayer();
        double playerX=player==null?.5:player.getX()-pos.getX(),playerZ=player==null?.5:player.getZ()-pos.getZ();
        int clicked=side.getAxis().isHorizontal()?side(side.getOpposite()):-1;
        double cellX=hit.x-pos.getX(),cellZ=hit.z-pos.getZ();
        if(sloped){
            int size=placementSize(level,pos.relative(side.getOpposite()),support);
            return diagonalState(defaultBlockState(),CornerGlassGeometry.placementDiagonal(size,clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ),size);
        }
        int placement=CornerGlassGeometry.placement(false,clicked,cellX,cellZ,neighbors,stacked,playerX,playerZ);
        return defaultBlockState().setValue(TURN,placement&3).setValue(DEPTH,placement/4);
    }
    /** N/E/S/W numbering used by the shared corner geometry. */
    static int side(Direction face){return (face.get2DDataValue()+2)&3;}
    private static boolean diagonalWall(BlockState state){return state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock;}
    private static int wallSize(Level level,BlockPos pos){return com.vandorlabs.neoforge.shape.NativeDiagonalWallTopology.mode(level,pos);}
    /** A corner placed against any diagonal block takes that block's size; otherwise full width. */
    private static int placementSize(Level level,BlockPos support,BlockState clicked){
        int size=diagonalWall(clicked)?wallSize(level,support)
                :clicked.getBlock() instanceof NativeCornerGlassBlock corner&&corner.sloped?clicked.getValue(CORNER_SIZE):-1;
        return com.vandorlabs.render.DiagonalCornerRules.placementSize(size,-1);
    }
    private CornerGlassGeometry.Neighbor neighbor(Level level,BlockPos at,BlockState state){
        if(state.getBlock() instanceof NativeCornerGlassBlock corner)
            return CornerGlassGeometry.Neighbor.corner(corner.projection(state));
        if(sloped&&diagonalWall(state))
            return CornerGlassGeometry.Neighbor.diagonal(side(state.getValue(com.vandorlabs.neoforge.shape.NativePanelBlock.FACING)),wallSize(level,at),
                    state.getValue(com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock.INVERTED));
        if(state.getBlock() instanceof NativeGlassBlock)
            return CornerGlassGeometry.Neighbor.straight(state.getValue(ROTATED),state.getValue(DEPTH));
        return CornerGlassGeometry.Neighbor.NONE;
    }
    public int frameMask(NativeGlassEntity owner){
        Level level=owner.getLevel();BlockPos pos=owner.getBlockPos();BlockState state=owner.getBlockState();
        if(!NativeGlassService.enabled(level,pos)||owner.settings().frames().tile)return 15;
        int mask=15,turn=state.getValue(TURN);
        for(int edge=0;edge<2;edge++){
            Direction side=SIDES[edge==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn)];
            BlockPos next=pos.relative(side);
            if(!NativeGlassService.enabled(level,next)||!NativeTextureJoins.edge(owner,pos,next))continue;
            var other=level.getBlockState(next);
            if(joinsEnd(level,pos,state,side,next,other))mask&=~(1<<edge);
        }
        for(Direction side:new Direction[]{Direction.DOWN,Direction.UP}){
            BlockPos next=pos.relative(side);
            if(!NativeGlassService.enabled(level,next)||!NativeTextureJoins.edge(owner,pos,next))continue;
            var other=level.getBlockState(next);
            if(other.getBlock() instanceof NativeCornerGlassBlock corner&&projection(state).joinsVertical(side==Direction.UP,corner.projection(other)))
                mask&=~(side==Direction.DOWN?4:8);
        }
        return mask;
    }
    private boolean joinsEnd(Level level,BlockPos corner,BlockState state,Direction side,BlockPos next,BlockState other){
        int direction=side(side);
        var current=projection(state);
        if(sloped&&diagonalWall(other))
            return continuesWall(state,side,other,com.vandorlabs.neoforge.shape.NativeDiagonalWallTopology.settings(level,next),NativeGlassService.enabled(level,corner));
        if(!(other.getBlock() instanceof NativeGlassBlock))return false;
        if(other.getBlock() instanceof NativeCornerGlassBlock adjacent)return current.joinsEdge(direction,adjacent.projection(other));
        return current.joinsStraight(direction,other.getValue(ROTATED),other.getValue(DEPTH));
    }
    public boolean joinsStraight(Level level,BlockPos corner,BlockState state,BlockPos straight,BlockState other){
        if(!NativeGlassService.enabled(level,corner)||!NativeGlassService.enabled(level,straight))return false;
        Direction side=Direction.fromDelta(straight.getX()-corner.getX(),straight.getY()-corner.getY(),straight.getZ()-corner.getZ());
        return side!=null&&side.getAxis().isHorizontal()&&joinsEnd(level,corner,state,side,straight,other);
    }
    /**
     * Whether the diagonal wall {@code wall}, at {@code side} of this corner and with {@code settings},
     * continues the corner's bevel. Shared by the corner's frame and the wall's end rails.
     */
    public static boolean continuesWall(BlockState corner,Direction side,BlockState wall,com.vandorlabs.persistence.WallSettings settings,boolean cornerJoins){
        if(!(corner.getBlock() instanceof NativeCornerGlassBlock block)||!block.sloped||!diagonalWall(wall)||side.getAxis().isVertical())return false;
        int size=com.vandorlabs.persistence.WallSettings.geometry(settings.halfHeight,settings.fullWidth);
        boolean porthole=((com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock)wall.getBlock()).isPorthole();
        return com.vandorlabs.render.DiagonalCornerRules.continues(block.projection(corner),cornerJoins,side(side),
                side(wall.getValue(com.vandorlabs.neoforge.shape.NativePanelBlock.FACING)),size,wall.getValue(com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock.INVERTED),
                com.vandorlabs.render.DiagonalCornerRules.wallAcceptsCorner(size,settings.join,porthole,settings.joinPortholes));
    }
    NativeGlassGeometry.Snapshot fluidGeometry(BlockState state){return shape(state).fluidGeometry();}
}
