package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.StairGeometry;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Vanilla stair placement/join state with the current shared tread/roll collision field. */
public final class NativeProgrammableStairs extends StairBlock implements EntityBlock {
    public static final MapCodec<NativeProgrammableStairs> CODEC=simpleCodec(NativeProgrammableStairs::new);
    public NativeProgrammableStairs(Properties properties){super(Blocks.IRON_BLOCK.defaultBlockState(),properties);}
    @Override public MapCodec<? extends StairBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableShapeEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,true);}
    public static List<AABB> solidBoxes(BlockState state,int packed){return NativeStairGeometry.boxes(state.getValue(FACING),state.getValue(HALF)==Half.TOP,StairGeometry.Shape.valueOf(state.getValue(SHAPE).name()),packed);}
    private static int settings(BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile?tile.appearance().stairs().packed():8;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(WATERLOGGED,false);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return NativeStairGeometry.shape(state.getValue(FACING),state.getValue(HALF)==Half.TOP,StairGeometry.Shape.valueOf(state.getValue(SHAPE).name()),settings(level,pos));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()&&CubeConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){var stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile)tile.saveToItem(stack,level.registryAccess());return stack;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){NativeFluidWorld.queueAt(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){NativeFluidWorld.queueAt(level,pos);return super.updateShape(state,side,neighbor,level,pos,other).setValue(WATERLOGGED,false);}
    @Override protected FluidState getFluidState(BlockState state){return Fluids.EMPTY.defaultFluidState();}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){return NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return NativeFluidWorld.pickupBlock(level,pos);}
}
