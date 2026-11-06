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
 private final PropulsionDefinition definition;private final Supplier<BlockEntityType<NativePropulsionEntity>> entities;private final VoxelShape[] shapes=new VoxelShape[6];
 public NativePropulsionBlock(String id,Properties properties,Supplier<BlockEntityType<NativePropulsionEntity>> entities){super(properties.noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false));this.definition=PropulsionDefinition.of(id);this.entities=entities;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.from3DDataValue(definition.defaultFacing)).setValue(POWERED,true));for(var face:Direction.values()){double[] b=PropulsionPlacement.bounds(definition.depth,face.get3DDataValue());shapes[face.get3DDataValue()]=Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]);}}
 public final PropulsionDefinition definition(){return definition;}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,POWERED);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
 @Override protected VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return shapes[s.getValue(FACING).get3DDataValue()];}
 @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c){return getShape(s,w,p,c);}
 @Override public int getLightEmission(BlockState state,BlockGetter world,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(world,pos);}
 @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
 @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override protected void onPlace(BlockState s,Level w,BlockPos p,BlockState previous,boolean moving){super.onPlace(s,w,p,previous,moving);NativePropulsionService.queue(w,p);}
 @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block b,BlockPos from,boolean moving){NativePropulsionService.queue(w,p);}
 @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState next,boolean moving){if(s.getBlock()!=next.getBlock()&&NativePropulsionService.loaded(w,p)&&w.getBlockEntity(p) instanceof NativePropulsionEntity owner)NativePropulsionService.retire(owner);super.onRemove(s,w,p,next,moving);}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
