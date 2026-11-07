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

/** Inactive four-cell block foundation; authored picking, editor routes and placement adapter follow. */
public class NativeProgrammerBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
 private final Supplier<BlockEntityType<NativeProgrammerEntity>> entities;
 public NativeProgrammerBlock(Properties properties,Supplier<BlockEntityType<NativeProgrammerEntity>> entities){super(properties.forceSolidOn().noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false).isRedstoneConductor((s,w,p)->false));this.entities=entities;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
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
