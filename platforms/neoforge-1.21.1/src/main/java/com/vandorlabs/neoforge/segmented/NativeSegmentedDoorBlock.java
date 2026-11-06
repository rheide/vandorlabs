package com.vandorlabs.neoforge.segmented;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.context.BlockPlaceContext;
/** Unregistered stationary housing; global artwork/input binding remains separate. */
public abstract class NativeSegmentedDoorBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 public static final BooleanProperty TOP=BooleanProperty.create("top"),BOTTOM=BooleanProperty.create("bottom");
 protected NativeSegmentedDoorBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(TOP,true).setValue(BOTTOM,true));}
 protected abstract BlockEntityType<?> entityType();
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,TOP,BOTTOM);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeSegmentedDoorEntity(entityType(),pos,state);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var level=context.getLevel();var pos=context.getClickedPos();for(var p:new BlockPos[]{pos.above(),pos.below()})if(level.hasChunkAt(p)&&level.getBlockState(p).is(this))return defaultBlockState().setValue(FACING,level.getBlockState(p).getValue(FACING));return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
 public boolean canAdd(Level level,BlockPos pos){int count=1;for(Direction side:new Direction[]{Direction.UP,Direction.DOWN})for(BlockPos p=pos.relative(side);level.hasChunkAt(p)&&level.getBlockState(p).is(this);p=p.relative(side))if(++count>32)return false;return true;}
 @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return !(level instanceof Level actual)||canAdd(actual,pos);}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:(world,pos,block,tile)->{if(tile instanceof NativeSegmentedDoorEntity door)door.tickServer();};}
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){var tile=NativeSegmentedDoorEntity.at(level,pos);if(tile!=null)tile.scheduleRefresh();}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){super.onRemove(state,level,pos,next,moving);if(!state.is(next.getBlock()))for(BlockPos p:new BlockPos[]{pos.above(),pos.below(),pos.relative(state.getValue(FACING).getClockWise()),pos.relative(state.getValue(FACING).getCounterClockWise())}){var tile=NativeSegmentedDoorEntity.at(level,p);if(tile!=null)tile.scheduleRefresh();}}
}
