package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.render.SlabMergePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ProgrammableSlabBlock extends ProgrammableBlock {
    private static final MapCodec<ProgrammableSlabBlock> CODEC=simpleCodec(ProgrammableSlabBlock::new);
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    public ProgrammableSlabBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(HALF,Half.BOTTOM));}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(HALF);}
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableShapeEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(HALF,SlabMergePolicy.upperHalf(context.getClickedFace().get3DDataValue(),(float)(context.getClickLocation().y-context.getClickedPos().getY()))?Half.TOP:Half.BOTTOM);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);return super.updateShape(state,side,neighbor,level,pos,other);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);super.neighborChanged(state,level,pos,neighbor,from,moving);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ShapeGeometry.shape(state,new com.vandorlabs.persistence.StairState(8,0));}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
    @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
    @Override public BlockState getStateAtViewpoint(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.Vec3 point){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.viewpoint(state,level,pos,point);}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return ShapeInteractions.open(level,pos,player);}
}
