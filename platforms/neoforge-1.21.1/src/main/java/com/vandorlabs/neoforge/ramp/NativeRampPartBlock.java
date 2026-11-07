package com.vandorlabs.neoforge.ramp;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Inactive generated surface, never an interaction proxy for its controller. */
public class NativeRampPartBlock extends BaseEntityBlock implements NativeOwnsBlockInteraction {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 private static final VoxelShape TOP_SUPPORT=Shapes.box(0,1-1e-6,0,1,1,1);
 private final Supplier<? extends BlockEntityType<?>> entities;
 public NativeRampPartBlock(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(properties.strength(-1,3600000).dynamicShape().noOcclusion().isRedstoneConductor((s,w,p)->false));this.entities=Objects.requireNonNull(entities);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 private static NativeRampPartEntity owner(BlockGetter world,BlockPos pos){return world.getBlockEntity(pos) instanceof NativeRampPartEntity part&&part.ownershipKnown()?part:null;}
 private static NativeRampPartEntity.Publication publication(BlockGetter world,BlockPos pos){var part=owner(world,pos);return part==null?null:part.publication();}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var value=publication(world,pos);return value==null?Shapes.empty():value.collisionShape();}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
  var part=owner(world,pos);if(part==null)return Shapes.empty();var value=part.publication();if(value==null||world instanceof Level level&&NativeRampRiders.carrying(level,part.controller()))return Shapes.empty();
  var entity=context instanceof EntityCollisionContext query?query.getEntity():null;boolean moving=value.data().motion().moving()||value.data().belt().running();if(!moving||entity==null)return value.collisionShape();
  ArrayList<AABB> clipped=null;for(int i=0;i<value.worldBoxes().size();i++){var original=value.worldBoxes().get(i);var supported=NativeRampRiders.supportCollision(original,entity,true);if(supported!=original&&clipped==null){clipped=new ArrayList<>(value.worldBoxes().size());for(int j=0;j<i;j++)clipped.add(value.geometry().pieces().get(j).box());}if(clipped!=null&&supported!=null)clipped.add(supported==original?value.geometry().pieces().get(i).box():supported.move(-pos.getX(),-pos.getY(),-pos.getZ()));}
  // A single common feet plane adds at most one Y coordinate to the checked base grid.
  return clipped==null?value.collisionShape():NativeVoxelShapes.union(clipped,8*1024*1024,1e-8);
 }
 @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){var value=publication(world,pos);return value!=null&&value.stationaryTop()?TOP_SUPPORT:Shapes.empty();}
 @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
 @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return true;}
 @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
 @Override protected float getShadeBrightness(BlockState state,BlockGetter world,BlockPos pos){return 1;}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,BlockHitResult hit){return InteractionResult.SUCCESS;}
 @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return ItemInteractionResult.SUCCESS;}
 @Override public boolean onDestroyedByPlayer(BlockState state,Level world,BlockPos pos,Player player,boolean harvest,FluidState fluid){if(!world.isClientSide)player.displayClientMessage(Component.literal("Retract or remove the Ramp Controller to restore this platform."),true);return false;}
 @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){return List.of();}
 @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader world,BlockPos pos,Player player){var value=publication(world,pos);return value==null?ItemStack.EMPTY:new ItemStack(value.data().original().getBlock());}
}
