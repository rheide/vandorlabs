package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.StairJoinPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ProgrammableStairsBlock extends StairBlock implements net.minecraft.world.level.block.EntityBlock {
    private static final MapCodec<ProgrammableStairsBlock> CODEC=simpleCodec(ProgrammableStairsBlock::new);
    public ProgrammableStairsBlock(BlockBehaviour.Properties properties){super(Blocks.IRON_BLOCK.defaultBlockState(),properties);}
    @Override public MapCodec<? extends StairBlock> codec(){return CODEC;}
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableShapeEntity(pos,state);}
    private StairState settings(BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile?tile.stairState():new StairState(8,0);}
    public BlockState connected(BlockState state,BlockGetter level,BlockPos pos){return ShapeGeometry.connected(state,level,pos,settings(level,pos));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){BlockState state=super.getStateForPlacement(context);return state==null?null:connected(state.setValue(WATERLOGGED,false),context.getLevel(),context.getClickedPos());}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);return connected(state.setValue(WATERLOGGED,false),level,pos);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);super.neighborChanged(state,level,pos,neighbor,from,moving);}
    @Override public FluidState getFluidState(BlockState state){return Fluids.EMPTY.defaultFluidState();}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ShapeGeometry.shape(connected(state,level,pos),settings(level,pos));}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 15;}
    @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
    @Override public BlockState getStateAtViewpoint(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.Vec3 point){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.viewpoint(state,level,pos,point);}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return ShapeInteractions.open(level,pos,player);}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile)tile.saveToItem(stack,level.registryAccess());return stack;}
}
