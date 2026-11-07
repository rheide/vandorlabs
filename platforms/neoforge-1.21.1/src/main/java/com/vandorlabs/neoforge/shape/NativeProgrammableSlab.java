package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.SlabMergePolicy;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Current six-facing half slab; its independent fluid volume never uses vanilla WATERLOGGED. */
public final class NativeProgrammableSlab extends DirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    public static final MapCodec<NativeProgrammableSlab> CODEC=simpleCodec(NativeProgrammableSlab::new);
    private static final AABB BOTTOM_BOX=new AABB(0,0,0,1,.5,1),TOP_BOX=new AABB(0,.5,0,1,1,1);
    private static final List<AABB> BOTTOM=List.of(BOTTOM_BOX),TOP=List.of(TOP_BOX);
    private static final VoxelShape BOTTOM_SHAPE=Shapes.create(BOTTOM_BOX),TOP_SHAPE=Shapes.create(TOP_BOX);
    public NativeProgrammableSlab(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,Half.BOTTOM));}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,HALF);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableShapeEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,false);}
    public static List<AABB> solidBoxes(BlockState state){return state.getValue(HALF)==Half.TOP?TOP:BOTTOM;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var previous=context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));Direction facing=previous.is(this)?previous.getValue(FACING):context.getHorizontalDirection().getOpposite();boolean upper=SlabMergePolicy.upperHalf(context.getClickedFace().get3DDataValue(),(float)(context.getClickLocation().y-context.getClickedPos().getY()));return defaultBlockState().setValue(FACING,facing).setValue(HALF,upper?Half.TOP:Half.BOTTOM);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(HALF)==Half.TOP?TOP_SHAPE:BOTTOM_SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return true;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()&&CubeConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){var stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile)tile.saveToItem(stack,level.registryAccess());return stack;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){NativeFluidWorld.queueAt(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){NativeFluidWorld.queueAt(level,pos);return state;}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){return NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return NativeFluidWorld.pickupBlock(level,pos);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
