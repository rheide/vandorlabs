package com.vandorlabs.neoforge.surface;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.PushReaction;

/** Clicked-face placement and locked claims preserve Source pad construction semantics. */
public final class NativeSurfaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty FORMED=BooleanProperty.create("formed");
    public NativeSurfaceBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(FORMED,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,FORMED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var facing=context.getClickedFace();var adjacent=context.getLevel().getBlockState(context.getClickedPos().relative(facing.getOpposite()));if(adjacent.is(this)&&adjacent.getValue(FACING).getAxis()!=facing.getAxis())facing=adjacent.getValue(FACING);return defaultBlockState().setValue(FACING,facing);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeSurfaceEntity(NativeSurfaceBindings.ENTITY,pos,state);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player actor,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.MAIN_HAND&&stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeSurfaceBindings.open(actor,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return stack.isEmpty()?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player actor,BlockHitResult hit){if(!actor.isShiftKeyDown())return InteractionResult.PASS;return NativeSurfaceBindings.open(actor,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity actor,ItemStack stack){super.setPlacedBy(level,pos,state,actor,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile){var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(custom!=null&&custom.copyTag().contains("ProgrammableSurface")){var tag=custom.copyTag().getCompound("ProgrammableSurface");tile.placedAppearance(tag.getString("SurfaceOn"),tag.getString("SurfaceOff"),tag.getInt("SurfaceLayout"));}tile.schedule();}}
    @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,Player actor){var stack=new ItemStack(asItem());if(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile){stack.applyComponents(tile.collectComponents());if(tile.inert())stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tile.saveWithId(tile.getLevel().registryAccess())));else{var tag=new net.minecraft.nbt.CompoundTag();tag.putString("SurfaceOn",tile.activeArtwork());tag.putString("SurfaceOff",tile.inactiveArtwork());tag.putInt("SurfaceLayout",tile.layoutMode());var existing=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);var custom=existing==null?new net.minecraft.nbt.CompoundTag():existing.copyTag();custom.put("ProgrammableSurface",tag);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(custom));}}return stack;}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var stack=new ItemStack(asItem());if(context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof NativeSurfaceEntity tile){stack.applyComponents(tile.collectComponents());if(tile.inert())stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tile.saveWithId(tile.getLevel().registryAccess())));}return java.util.List.of(stack);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)tile.schedule();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)tile.removed();super.onRemove(state,level,pos,next,moving);}
    @Override protected float getDestroyProgress(BlockState state,Player actor,BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile&&tile.locked()?0:super.getDestroyProgress(state,actor,level,pos);}
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){return !(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile&&tile.locked())&&super.onDestroyedByPlayer(state,level,pos,player,harvest,fluid);}
    @Override public boolean canEntityDestroy(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.entity.Entity entity){return !(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile&&tile.locked())&&super.canEntityDestroy(state,level,pos,entity);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
