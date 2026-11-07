package com.vandorlabs.neoforge.programmer;

import java.util.*;
import java.util.function.Supplier;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Inactive four-cell block. Physical ownership and authored interaction geometry stay separate. */
public class NativeProgrammerBlock extends BaseEntityBlock implements com.vandorlabs.neoforge.NativeAuthoredBlockRay {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
 private final Supplier<BlockEntityType<NativeProgrammerEntity>> entities;
 private final Supplier<com.vandorlabs.programmer.ProgrammerPicking.Mesh> geometry;
 private com.vandorlabs.programmer.ProgrammerJournalPlane journalPlane;
 public NativeProgrammerBlock(Properties properties,Supplier<BlockEntityType<NativeProgrammerEntity>> entities){this(properties,entities,NativeProgrammerPicking::authored);}
 public NativeProgrammerBlock(Properties properties,Supplier<BlockEntityType<NativeProgrammerEntity>> entities,Supplier<com.vandorlabs.programmer.ProgrammerPicking.Mesh> geometry){super(properties.forceSolidOn().noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false).isRedstoneConductor((s,w,p)->false));this.entities=entities;this.geometry=java.util.Objects.requireNonNull(geometry);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
 public NativeProgrammerPicking.Hit trace(BlockPos pos,BlockState state,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end){return NativeProgrammerPicking.trace(geometry.get(),root(pos,state),state.getValue(FACING),state.getValue(PART),start,end);}
 @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end){var hit=trace(pos,state,start,end);return hit==null?null:hit.blockHit();}
 public com.vandorlabs.programmer.ProgrammerJournalPlane journalPlane(){if(journalPlane==null)journalPlane=new com.vandorlabs.programmer.ProgrammerJournalPlane(geometry.get());return journalPlane;}
 protected net.minecraft.world.inventory.MenuType<NativeProgrammerMenu> menuType(){return null;}
 /** Configurizer keeps the same authored socket/body precedence as an ordinary activation. */
 public boolean openConfiguration(Player player,Level level,BlockPos pos){return activate(level,pos,player,InteractionHand.MAIN_HAND);}
 public NativeProgrammerEntity installed(Level level,BlockPos pos){
  if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return null;if(!level.hasChunkAt(pos)||!level.getBlockState(pos).is(this))return null;var state=level.getBlockState(pos);var root=root(pos,state);if(!level.hasChunkAt(root))return null;var lower=level.getBlockState(root);
  return lower.is(this)&&lower.getValue(PART)==0&&lower.getValue(FACING)==state.getValue(FACING)&&level.getBlockEntity(root) instanceof NativeProgrammerEntity owner&&!owner.isRemoved()?owner:null;
 }
 private boolean activate(Level level,BlockPos pos,Player player,InteractionHand hand){
  if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return false;var owner=installed(level,pos);if(owner==null)return false;if(level.isClientSide)return true;if(!(player instanceof net.minecraft.server.level.ServerPlayer actor)||!owner.usable(player))return true;
  var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(Math.min(8,player.blockInteractionRange())));var hit=trace(pos,level.getBlockState(pos),start,end);
  if(hit!=null&&hit.slot()>=0&&hit.slot()<3){owner.socket(actor,hand,hit.slot());return true;}
  if(hit!=null&&hit.slot()==3&&owner.hasJournal()){
   if(hand==InteractionHand.MAIN_HAND){var visibleEnd=start.add(hit.point().subtract(start).scale(1.000001));boolean unavailable=BlockGetter.traverseBlocks(start,visibleEnd,(ServerLevel)level,(world,cell)->world.getChunkSource().getChunkNow(cell.getX()>>4,cell.getZ()>>4)==null?Boolean.TRUE:null,world->Boolean.FALSE);if(!unavailable){var first=level.clip(new net.minecraft.world.level.ClipContext(start,visibleEnd,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));if(first.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&installed(level,first.getBlockPos())==owner){var selected=trace(first.getBlockPos(),level.getBlockState(first.getBlockPos()),start,end);if(selected!=null&&selected.slot()==3){var layout=NativeProgrammerLayout.of(owner.getBlockState().getValue(FACING));var root=owner.getBlockPos();var pixels=journalPlane().hit(layout.transform(),root.getX()+layout.originX(),root.getY(),root.getZ()+layout.originZ(),start.x,start.y,start.z,end.x,end.y,end.z);if(pixels!=null&&pixels.action()!=0)owner.turnJournalPage(actor,pixels.action());}}}}
   return true;
  }
  NativeProgrammerConfiguration.open(actor,(ServerLevel)level,owner.getBlockPos(),menuType());return true;
 }
 @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return activate(level,pos,player,hand)?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(level,pos,player,InteractionHand.MAIN_HAND)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){throw new UnsupportedOperationException("Registry supplied programmer");}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(PART)==0?new NativeProgrammerEntity(entities.get(),pos,state):null;}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide||state.getValue(PART)!=0?null:createTickerHelper(type,entities.get(),(world,pos,current,tile)->tile.tick());}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var stack=context.getItemInHand();var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(stack.has(DataComponents.BLOCK_ENTITY_DATA)||tag.contains("BlockEntityTag")||tag.contains("ProgrammerPlacementRecovery"))return null;return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
 public static BlockPos root(BlockPos pos,BlockState state){var cell=NativeProgrammerLayout.of(state.getValue(FACING)).cells().get(state.getValue(PART));return pos.offset(-cell.x(),-cell.y(),-cell.z());}
 public static BlockPos cell(BlockPos root,Direction facing,int part){var cell=NativeProgrammerLayout.of(facing).cells().get(part);return root.offset(cell.x(),cell.y(),cell.z());}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.block();}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.block();}
 @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
 @Override protected float getShadeBrightness(BlockState state,BlockGetter world,BlockPos pos){return 1F;}
 @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter world,BlockPos pos){return true;}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
 @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,Player player){return new ItemStack(asItem());}
 @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){if(state.getValue(PART)!=0)return List.of();var tile=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);return tile instanceof NativeProgrammerEntity owner&&owner.inert()?List.of():List.of(new ItemStack(asItem()));}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moving){
  if(state.getBlock()==after.getBlock()){super.onRemove(state,level,pos,after,moving);return;}
  if(NativeProgrammerItem.mutating(level,pos)){super.onRemove(state,level,pos,after,moving);return;}
  List<ItemStack> drops=List.of();CompoundTag recovery=null;var previous=level.getBlockEntity(pos);
  if(level instanceof ServerLevel&&previous instanceof NativeProgrammerEntity owner){owner.beginRemoval();recovery=owner.recovery();if(recovery==null)drops=owner.takeForDrop();}
  super.onRemove(state,level,pos,after,moving);
  if(!(level instanceof ServerLevel))return;
  if(recovery!=null){var item=new ItemStack(asItem());var tag=new CompoundTag();tag.put("ProgrammerPlacementRecovery",recovery);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),item);}else for(var item:drops)Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),item);
  if(level.getBlockState(pos).is(this))return;
  BlockPos root=root(pos,state);if(state.getValue(PART)!=0){if(level.hasChunkAt(root)&&level.getBlockState(root).is(this))level.destroyBlock(root,true);}else for(int part=1;part<4;part++){BlockPos member=cell(root,state.getValue(FACING),part);if(level.hasChunkAt(member)){var old=level.getBlockState(member);if(old.is(this)&&old.getValue(PART)==part&&old.getValue(FACING)==state.getValue(FACING))level.removeBlock(member,false);}}
 }
}
