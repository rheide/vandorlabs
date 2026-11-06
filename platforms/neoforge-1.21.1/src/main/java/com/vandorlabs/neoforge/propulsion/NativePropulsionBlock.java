package com.vandorlabs.neoforge.propulsion;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.propulsion.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Inactive Source engine/hover housing. Wedges retain full-cube collision. */
public class NativePropulsionBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.FACING;public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
 private final java.util.function.Function<String,NativePropulsionBlock> variants;
 private final PropulsionDefinition definition;private final Supplier<BlockEntityType<NativePropulsionEntity>> entities;private final VoxelShape[] shapes=new VoxelShape[6];
 public NativePropulsionBlock(String id,Properties properties,Supplier<BlockEntityType<NativePropulsionEntity>> entities){this(id,properties,entities,null);}
 public NativePropulsionBlock(String id,Properties properties,Supplier<BlockEntityType<NativePropulsionEntity>> entities,java.util.function.Function<String,NativePropulsionBlock> variants){super(properties.noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false));this.definition=PropulsionDefinition.of(id);this.entities=entities;this.variants=variants;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.from3DDataValue(definition.defaultFacing)).setValue(POWERED,true));for(var face:Direction.values()){double[] b=PropulsionPlacement.bounds(definition.depth,face.get3DDataValue());shapes[face.get3DDataValue()]=Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]);}}
 final BlockEntityType<NativePropulsionEntity> entityType(){return entities.get();}
 final NativePropulsionBlock variant(String id){if(definition.id.equals(id))return this;if(variants!=null)return variants.apply(id);var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs",id));return block instanceof NativePropulsionBlock value?value:null;}
 final net.minecraft.world.item.Item familyItem(){var base=variant(NativePropulsionPlacement.item(definition));return base==null?net.minecraft.world.item.Items.AIR:base.asItem();}
 public final PropulsionDefinition definition(){return definition;}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,POWERED);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
 @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return shapes[s.getValue(FACING).get3DDataValue()];}
 @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return getShape(s,w,p,c);}
 protected net.minecraft.world.inventory.MenuType<NativePropulsionMenu> menuType(){return null;}
 public final boolean openConfiguration(net.minecraft.world.entity.player.Player player,Level world,BlockPos pos){return NativePropulsionConfiguration.open(player,world,pos,menuType());}
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){if(!world.isClientSide&&NativePropulsionService.loaded(world,pos)&&world.getBlockEntity(pos) instanceof NativePropulsionEntity owner){if(player.isShiftKeyDown()){if(player.isCreative())openConfiguration(player,world,pos);}else owner.manualUse(player);}return net.minecraft.world.InteractionResult.sidedSuccess(world.isClientSide);}
 @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level world,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){if(hand==net.minecraft.world.InteractionHand.OFF_HAND)return net.minecraft.world.ItemInteractionResult.SUCCESS;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,world,pos)?net.minecraft.world.ItemInteractionResult.SUCCESS:net.minecraft.world.ItemInteractionResult.FAIL;return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override public int getLightEmission(BlockState state,BlockGetter world,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(world,pos);}
 @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
 @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader world,BlockPos pos,net.minecraft.world.entity.player.Player player){return NativePropulsionItem.pick(this,world.getBlockEntity(pos) instanceof NativePropulsionEntity owner?owner:null);}
 @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var raw=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);return java.util.List.of(NativePropulsionItem.drop(this,raw instanceof NativePropulsionEntity owner?owner:null));}
 @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativePropulsionEntity owner)owner.prepareRecoveryDrop(owner.inert()&&(player.isCreative()||!state.canHarvestBlock(world,pos,player))?NativePropulsionItem.pick(this,owner):net.minecraft.world.item.ItemStack.EMPTY);return super.playerWillDestroy(world,pos,state,player);}
 @Override protected void onPlace(BlockState s,Level w,BlockPos p,BlockState previous,boolean moving){super.onPlace(s,w,p,previous,moving);NativePropulsionService.queue(w,p);}
 @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block b,BlockPos from,boolean moving){if(w instanceof net.minecraft.server.level.ServerLevel&&NativePropulsionService.loaded(w,p)&&w.getBlockEntity(p) instanceof NativePropulsionEntity owner&&owner.available())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.outputChanged(owner);NativePropulsionService.queue(w,p);}
 @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos other){if(world instanceof Level level)NativePropulsionService.queue(level,pos);return state;}
 @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState next,boolean moving){if(s.getBlock()!=next.getBlock()&&NativePropulsionService.loaded(w,p)&&w.getBlockEntity(p) instanceof NativePropulsionEntity owner){if(!w.isClientSide&&!NativePropulsionItem.mutating(w,p)&&owner.inert()){var recovery=owner.takeRecoveryDrop();if(!recovery.isEmpty())Block.popResource(w,p,recovery);}NativePropulsionService.retire(owner);}super.onRemove(s,w,p,next,moving);}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){var face=state.getValue(FACING);if(definition.shape==2&&face.getAxis()==Direction.Axis.Y){int corner=PropulsionPlacement.rotateCorner(definition.corner,face.get3DDataValue(),rotation.ordinal());var target=variant(PropulsionDefinition.wedgeId(definition.family,corner));if(target!=null)return target.defaultBlockState().setValue(FACING,face).setValue(POWERED,state.getValue(POWERED));}return state.setValue(FACING,rotation.rotate(face));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
