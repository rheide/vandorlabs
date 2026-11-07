package com.vandorlabs.neoforge.glass;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Inactive ordinary glass block; original palette properties remain independently loadable. */
public class NativeGlassBlock extends Block implements EntityBlock {
 public static final BooleanProperty ROTATED=BooleanProperty.create("rotated"),TOP=BooleanProperty.create("top"),BOTTOM=BooleanProperty.create("bottom"),LEFT=BooleanProperty.create("left"),RIGHT=BooleanProperty.create("right"),INNER_TL=BooleanProperty.create("inner_tl"),INNER_TR=BooleanProperty.create("inner_tr"),INNER_BL=BooleanProperty.create("inner_bl"),INNER_BR=BooleanProperty.create("inner_br");
 public static final IntegerProperty DEPTH=IntegerProperty.create("depth",0,2),SIZE=IntegerProperty.create("size",0,2);
 private final Supplier<BlockEntityType<?>> type;private final MapCodec<NativeGlassBlock> codec;
 public NativeGlassBlock(Properties properties,Supplier<BlockEntityType<?>> type){super(properties.forceSolidOn().dynamicShape().noOcclusion());this.type=java.util.Objects.requireNonNull(type);codec=simpleCodec(p->new NativeGlassBlock(p,type));registerDefaultState(stateDefinition.any().setValue(ROTATED,false).setValue(DEPTH,0).setValue(SIZE,1).setValue(TOP,true).setValue(BOTTOM,true).setValue(LEFT,true).setValue(RIGHT,true).setValue(INNER_TL,false).setValue(INNER_TR,false).setValue(INNER_BL,false).setValue(INNER_BR,false));}
 public BlockEntityType<?> entityType(){return type.get();}
 protected net.minecraft.world.inventory.MenuType<NativeGlassMenu> menuType(){return null;}
 public final boolean openConfiguration(net.minecraft.world.entity.player.Player player,Level world,BlockPos pos){return NativeGlassConfiguration.open(player,world,pos,menuType());}
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){return player.isCreative()&&player.isShiftKeyDown()&&openConfiguration(player,world,pos)?net.minecraft.world.InteractionResult.sidedSuccess(world.isClientSide):net.minecraft.world.InteractionResult.PASS;}
 @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level world,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){if(hand==net.minecraft.world.InteractionHand.OFF_HAND)return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,world,pos)?net.minecraft.world.ItemInteractionResult.SUCCESS:net.minecraft.world.ItemInteractionResult.FAIL;return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override protected MapCodec<? extends Block> codec(){return codec;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(ROTATED,DEPTH,SIZE,TOP,BOTTOM,LEFT,RIGHT,INNER_TL,INNER_TR,INNER_BL,INNER_BR);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeGlassEntity(entityType(),pos,state);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return NativeGlassService.placement(this,context);}
 public static NativeGlassGeometry.Snapshot geometry(BlockState state,BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeGlassEntity host?host.publication().geometry():NativeGlassGeometry.straight(state.getValue(ROTATED),state.getValue(DEPTH));}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return geometry(state,level,pos).selection();}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return geometry(state,level,pos).collision();}
 @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
 @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
 @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
 @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
 @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);NativeFluidWorld.blockChanged(level,pos);}
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){NativeFluidWorld.blockChanged(level,pos);}
 @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader world,BlockPos pos,net.minecraft.world.entity.player.Player player){return NativeGlassItem.pick(this,world.getBlockEntity(pos) instanceof NativeGlassEntity owner?owner:null,state);}
 @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var raw=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);return java.util.List.of(NativeGlassItem.drop(this,raw instanceof NativeGlassEntity owner?owner:null,state));}
 @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeGlassEntity owner)owner.prepareRecoveryDrop(owner.inert()&&(player.isCreative()||!state.canHarvestBlock(world,pos,player))?NativeGlassItem.pick(this,owner,state):net.minecraft.world.item.ItemStack.EMPTY);return super.playerWillDestroy(world,pos,state,player);}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())){if(level.getBlockEntity(pos) instanceof NativeGlassEntity host){if(!level.isClientSide&&!NativeGlassItem.mutating(level,pos)&&host.inert()){var recovery=host.takeRecoveryDrop();if(!recovery.isEmpty())Block.popResource(level,pos,recovery);}NativeGlassService.retire(host);}NativeFluidWorld.blockChanged(level,pos);}super.onRemove(state,level,pos,next,moving);}
}
