package com.vandorlabs.neoforge.segmented;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.context.BlockPlaceContext;
/** Unregistered stationary housing; global artwork/input binding remains separate. */
public abstract class NativeSegmentedDoorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 public static final BooleanProperty TOP=BooleanProperty.create("top"),BOTTOM=BooleanProperty.create("bottom");
 protected NativeSegmentedDoorBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(TOP,true).setValue(BOTTOM,true));}
 protected abstract BlockEntityType<?> entityType();
 protected net.minecraft.world.inventory.MenuType<NativeSegmentedMenu> menuType(){return null;}
 @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec(){return com.mojang.serialization.MapCodec.unit(this);}
 public final boolean openConfiguration(net.minecraft.world.entity.player.Player player,Level level,BlockPos pos){var menu=menuType();return menu!=null&&NativeSegmentedConfiguration.open(player,level,pos,menu);}
 private net.minecraft.world.InteractionResult interact(Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
  if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem)return net.minecraft.world.InteractionResult.PASS;
  if(player.isShiftKeyDown()||player.getItemInHand(hand).is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,level,pos)?net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide):net.minecraft.world.InteractionResult.FAIL;
  var tile=NativeSegmentedTargets.loaded(level,pos);if(tile==null)return net.minecraft.world.InteractionResult.FAIL;
  if(!level.isClientSide&&level.mayInteract(player,pos))try{tile.toggle(player);}catch(IllegalArgumentException denied){player.displayClientMessage(net.minecraft.network.chat.Component.literal(denied.getMessage()),true);}
  return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
 }
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){return interact(level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND);}
 @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
  if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||stack.getItem() instanceof net.minecraft.world.item.BlockItem)return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
  return switch(interact(level,pos,player,hand)){case SUCCESS,CONSUME,CONSUME_PARTIAL->net.minecraft.world.ItemInteractionResult.SUCCESS;case FAIL->net.minecraft.world.ItemInteractionResult.FAIL;default->net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};
 }
 @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter world,BlockPos pos){return true;}
 @Override protected float getShadeBrightness(BlockState state,BlockGetter world,BlockPos pos){return 1;}
 @Override protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return net.minecraft.world.phys.shapes.Shapes.empty();}
 @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
  if(world instanceof Level level){var tile=NativeSegmentedTargets.loaded(level,pos);if(tile!=null){var snapshot=NativeSegmentedTargets.geometry(tile,NativeSegmentedTargets.pose(tile.root()));if(snapshot!=null){var shape=net.minecraft.world.phys.shapes.Shapes.create(snapshot.bounds().move(-pos.getX(),-pos.getY(),-pos.getZ()));return snapshot.header()==null?shape:net.minecraft.world.phys.shapes.Shapes.or(shape,net.minecraft.world.phys.shapes.Shapes.create(snapshot.header().move(-pos.getX(),-pos.getY(),-pos.getZ())));}}}
  return net.minecraft.world.phys.shapes.Shapes.create(NativeSegmentedGeometry.closedPanel(state.getValue(FACING),0));
 }
 @Override protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
  var shape=net.minecraft.world.phys.shapes.Shapes.empty();if(world instanceof Level level){var tile=NativeSegmentedTargets.loaded(level,pos);if(tile!=null){var root=tile.root();if(!root.targetOpen()&&root.pose(0)<=0)shape=net.minecraft.world.phys.shapes.Shapes.create(NativeSegmentedGeometry.closedPanel(tile.facing(),tile.settings().position));var header=NativeSegmentedGeometry.header(tile.facing(),tile.settings().direction,tile.settings().position,tile.height(),tile.topOffset(),tile.column(),tile.columnsCount());return header==null?shape:net.minecraft.world.phys.shapes.Shapes.or(shape,net.minecraft.world.phys.shapes.Shapes.create(header));}}
  return net.minecraft.world.phys.shapes.Shapes.create(NativeSegmentedGeometry.closedPanel(state.getValue(FACING),0));
 }
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,TOP,BOTTOM);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeSegmentedDoorEntity(entityType(),pos,state);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var level=context.getLevel();var pos=context.getClickedPos();for(var p:new BlockPos[]{pos.above(),pos.below()})if(level.hasChunkAt(p)&&level.getBlockState(p).is(this))return defaultBlockState().setValue(FACING,level.getBlockState(p).getValue(FACING));return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
 public boolean canAdd(Level level,BlockPos pos){int count=1;for(Direction side:new Direction[]{Direction.UP,Direction.DOWN})for(BlockPos p=pos.relative(side);level.hasChunkAt(p)&&level.getBlockState(p).is(this);p=p.relative(side))if(++count>32)return false;return true;}
 @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return !(level instanceof Level actual)||canAdd(actual,pos);}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:(world,pos,block,tile)->{if(tile instanceof NativeSegmentedDoorEntity door)door.tickServer();};}
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);var tile=NativeSegmentedDoorEntity.at(level,pos);if(tile!=null)tile.scheduleRefresh();}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())){var owner=NativeSegmentedTargets.loaded(level,pos);if(owner!=null)owner.stopFluids();}super.onRemove(state,level,pos,next,moving);if(!state.is(next.getBlock()))for(BlockPos p:new BlockPos[]{pos.above(),pos.below(),pos.relative(state.getValue(FACING).getClockWise()),pos.relative(state.getValue(FACING).getCounterClockWise())}){var tile=NativeSegmentedDoorEntity.at(level,p);if(tile!=null)tile.scheduleRefresh();}}
 @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader reader,BlockPos pos,net.minecraft.world.entity.player.Player player){return NativeSegmentedItem.pick(net.minecraft.world.item.Item.byBlock(this),reader instanceof Level level?NativeSegmentedTargets.loaded(level,pos):null);}
 @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
 @Override public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
 @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.placeLiquid(level,pos,fluid);}
 @Override public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.entity.player.Player player,LevelAccessor level,BlockPos pos,BlockState state){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.pickupBlock(level,pos);}
}

