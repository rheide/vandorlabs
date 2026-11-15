package com.vandorlabs.neoforge.gear.track;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.gear.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Terrain-baked native tracks; the existing arm controller draws mounted moving members. */
public final class NativeTrackBlock extends NativeGearBlock {
    private final int style;
    public NativeTrackBlock(NativeGearDefinition definition,int style,Properties properties,Supplier<BlockEntityType<NativeGearEntity>> entities){super(definition,properties,entities);this.style=style;}
    public int defaultStyle(){return style;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world,BlockState state,BlockEntityType<T> type){return world.isClientSide?null:createTickerHelper(type,entityType(),NativeTrackRuntime::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);var support=context.getClickedPos().relative(context.getClickedFace().getOpposite());var neighbor=NativeGearFormation.at(context.getLevel(),support);Direction facing=neighbor instanceof NativeTrackEntity?neighbor.getBlockState().getValue(FACING):neighbor!=null&&neighbor.definition().arm()?neighbor.getBlockState().getValue(FACING):context.getClickedFace().getAxis().isHorizontal()?context.getClickedFace().getClockWise():context.getHorizontalDirection().getOpposite();return state.setValue(FACING,facing);}
    private static boolean moving(BlockGetter world,BlockPos pos){return world.getBlockEntity(pos) instanceof NativeTrackEntity track&&track.controllerEntity()!=null;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return moving(world,pos)?super.getShape(state,world,pos,context):world.getBlockEntity(pos) instanceof NativeTrackEntity track?NativeTrackGeometry.shape(track):Shapes.empty();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return moving(world,pos)?super.getCollisionShape(state,world,pos,context):world.getBlockEntity(pos) instanceof NativeTrackEntity track?NativeTrackGeometry.shape(track):Shapes.empty();}
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player actor,BlockHitResult hit){if(!actor.getMainHandItem().isEmpty())return net.minecraft.world.InteractionResult.PASS;if(level.getBlockEntity(pos) instanceof NativeTrackEntity track&&track.controllerEntity()==null)return NativeTracks.open(actor,level,pos)?net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide):net.minecraft.world.InteractionResult.FAIL;return super.useWithoutItem(state,level,pos,actor,hit);}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player actor,net.minecraft.world.InteractionHand hand,BlockHitResult hit){if(hand==net.minecraft.world.InteractionHand.MAIN_HAND&&stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeTracks.open(actor,level,pos)?net.minecraft.world.ItemInteractionResult.SUCCESS:net.minecraft.world.ItemInteractionResult.FAIL;return super.useItemOn(stack,state,level,pos,actor,hand,hit);}
    @Override protected void neighborChanged(BlockState state,Level world,BlockPos pos,Block block,BlockPos from,boolean moving){super.neighborChanged(state,world,pos,block,from,moving);NativeTrackRuntime.around(world,pos);}
    @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeTrackEntity track)for(var old:track.trackMembers())if(world.hasChunkAt(old)&&world.getBlockEntity(old) instanceof NativeTrackEntity member)member.requestRebuild();super.onRemove(state,world,pos,next,moving);}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader world,BlockPos pos,Player player){return configured(world.getBlockEntity(pos));}
    private static ItemStack configured(BlockEntity tile){var item=NativeGearBindings.ITEMS.get("compact_track");var stack=NativeGearItem.pick(item,tile instanceof NativeGearEntity gear?gear:null);if(tile instanceof NativeTrackEntity track){var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();var choices=data.getCompound("ModularGearSettings");choices.putInt("TrackStyle",track.style().ordinal());data.put("ModularGearSettings",choices);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));}return stack;}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){return java.util.List.of(configured(context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)));}
}
