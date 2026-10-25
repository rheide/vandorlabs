package com.vandorlabs.neoforge.table;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.render.IndustrialTableGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** Native table properties; connections are derived from loaded matching modules. */
public final class TableBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<TableBlock> CODEC=simpleCodec(TableBlock::new);
    public static final BooleanProperty LEFT=BooleanProperty.create("left"),RIGHT=BooleanProperty.create("right"),UPSIDE_DOWN=BooleanProperty.create("upside_down");
    private static final VoxelShape[] COLLISION=buildShapes();
    private static VoxelShape[] buildShapes(){VoxelShape[] result=new VoxelShape[32];for(int face=2;face<=5;face++)for(int inverted=0;inverted<2;inverted++)for(int mask=0;mask<4;mask++){java.util.List<net.minecraft.world.phys.AABB> boxes=new java.util.ArrayList<>();for(var box:IndustrialTableGeometry.collision(face,inverted!=0,(mask&1)!=0,(mask&2)!=0))boxes.add(new net.minecraft.world.phys.AABB(box.x0,box.y0,box.z0,box.x1,box.y1,box.z1));result[(face-2)*8+inverted*4+mask]=com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(boxes);}return result;}
    public TableBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LEFT,false).setValue(RIGHT,false).setValue(UPSIDE_DOWN,false));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LEFT,RIGHT,UPSIDE_DOWN);}
    private boolean matches(BlockGetter level,BlockPos pos,BlockState root){
        // ClientLevel.hasChunk() is deliberately unconditional; ask the actual cache instead.
        if(level instanceof Level live&&!live.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4))return false;
        if(!(level instanceof Level)&&level instanceof LevelReader reader&&!reader.hasChunkAt(pos))return false;BlockState neighbor=level.getBlockState(pos);boolean same=neighbor.is(this);
        return IndustrialTableGeometry.matches(true,same,root.getValue(FACING).get3DDataValue(),root.getValue(UPSIDE_DOWN),same?neighbor.getValue(FACING).get3DDataValue():-1,same&&neighbor.getValue(UPSIDE_DOWN));
    }
    public BlockState actual(BlockState state,BlockGetter level,BlockPos pos){Direction face=state.getValue(FACING);return state.setValue(LEFT,matches(level,pos.relative(face.getCounterClockWise()),state)).setValue(RIGHT,matches(level,pos.relative(face.getClockWise()),state));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return actual(defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(UPSIDE_DOWN,IndustrialTableGeometry.invertedPlacement(context.getClickedFace().get3DDataValue())),context.getLevel(),context.getClickedPos());}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){return actual(state,level,pos);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){BlockState next=actual(state,level,pos);if(next!=state)level.setBlock(pos,next,3);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING))).setValue(LEFT,false).setValue(RIGHT,false);}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){BlockState connected=actual(state,level,pos);return COLLISION[(connected.getValue(FACING).get3DDataValue()-2)*8+(connected.getValue(UPSIDE_DOWN)?4:0)+(connected.getValue(LEFT)?1:0)+(connected.getValue(RIGHT)?2:0)];}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return state.getValue(UPSIDE_DOWN)?Shapes.box(0,0,0,1,2D/16,1):Shapes.box(0,14D/16,0,1,1,1);}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
}
