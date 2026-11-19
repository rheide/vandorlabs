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
    private final Shape[] shapes=new Shape[12];
    private final MapCodec<NativeCornerGlassBlock> codec;

    public NativeCornerGlassBlock(Properties properties,Supplier<BlockEntityType<?>> type,boolean sloped){
        super(properties,type);
        codec=simpleCodec(p->new NativeCornerGlassBlock(p,type,sloped));
        registerDefaultState(defaultBlockState().setValue(TURN,0));
        for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++){
            var geometry=new CornerGlassGeometry(turn,depth,sloped);
            var boxes=geometry.collision(AABB::new);
            shapes[depth*4+turn]=new Shape(geometry,boxes,NativeFlatWallGeometry.collision(boxes),
                    Shapes.create(geometry.bounds(AABB::new)));
        }
    }
    @Override protected MapCodec<? extends Block> codec(){return codec;}
    @Override protected MenuType<NativeGlassMenu> menuType(){return NativeGlassRegistrations.MENU.get();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(TURN);
    }
    private Shape shape(BlockState state){return shapes[state.getValue(DEPTH)*4+state.getValue(TURN)];}
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
        BlockState support=context.getLevel().getBlockState(context.getClickedPos().relative(side.getOpposite()));
        if(side.getAxis()==Direction.Axis.Y&&support.is(this))
            return defaultBlockState().setValue(TURN,support.getValue(TURN)).setValue(DEPTH,support.getValue(DEPTH));
        var hit=NativePlacementHit.hit(context);double x=hit.x,z=hit.z;
        Direction facing=side.getAxis().isHorizontal()?side.getOpposite():context.getHorizontalDirection();
        if(side.getAxis()==Direction.Axis.X)x=side==Direction.EAST?0:1;
        if(side.getAxis()==Direction.Axis.Z)z=side==Direction.SOUTH?0:1;
        int turn=CornerGlassGeometry.orientation((facing.get2DDataValue()+2)&3,x,z);
        return defaultBlockState().setValue(TURN,turn)
                .setValue(DEPTH,CornerGlassGeometry.placementDepth(turn,x,z,side.getAxis().isHorizontal()));
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
            if(joinsEnd(state,side,other))mask&=~(1<<edge);
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
    private boolean joinsEnd(BlockState state,Direction side,BlockState other){
        if(!(other.getBlock() instanceof NativeGlassBlock))return false;
        int direction=(side.get2DDataValue()+2)&3;
        var current=projection(state);
        if(other.getBlock() instanceof NativeCornerGlassBlock corner)return current.joinsEdge(direction,corner.projection(other));
        return current.joinsStraight(direction,other.getValue(ROTATED),other.getValue(DEPTH));
    }
    public boolean joinsStraight(Level level,BlockPos corner,BlockState state,BlockPos straight,BlockState other){
        if(!NativeGlassService.enabled(level,corner)||!NativeGlassService.enabled(level,straight))return false;
        Direction side=Direction.fromDelta(straight.getX()-corner.getX(),straight.getY()-corner.getY(),straight.getZ()-corner.getZ());
        return side!=null&&side.getAxis().isHorizontal()&&joinsEnd(state,side,other);
    }
    NativeGlassGeometry.Snapshot fluidGeometry(BlockState state){return shape(state).fluidGeometry();}
}
