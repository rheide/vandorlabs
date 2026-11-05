package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.CubeConfiguration;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Current wall/porthole panel body. Joined geometry is published locally after loaded-neighbour checks. */
public final class NativeFlatWallBlock extends HorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
    public static final BooleanProperty INVERTED=BlockStateProperties.INVERTED;
    public static final IntegerProperty DEPTH=IntegerProperty.create("depth",0,2);
    private final boolean porthole;
    private final MapCodec<NativeFlatWallBlock> codec;
    public NativeFlatWallBlock(Properties properties,boolean porthole){super(properties);this.porthole=porthole;codec=simpleCodec(p->new NativeFlatWallBlock(p,porthole));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(DEPTH,0).setValue(INVERTED,false));}
    public boolean isPorthole(){return porthole;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,DEPTH,INVERTED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,porthole);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos support=context.getClickedPos().relative(context.getClickedFace().getOpposite());var clicked=context.getLevel().getBlockState(support);
        Direction facing=clicked.is(this)?clicked.getValue(FACING):context.getHorizontalDirection().getOpposite();
        Vec3 hit=context.getClickLocation().subtract(context.getClickedPos().getX(),context.getClickedPos().getY(),context.getClickedPos().getZ());
        return defaultBlockState().setValue(FACING,facing).setValue(DEPTH,PanelDepthPolicy.fromHit(facing.get3DDataValue(),(float)hit.x,(float)hit.z));
    }
    public static NativeFlatWallGeometry.Geometry geometry(BlockState state,BlockGetter level,BlockPos pos){
        if(level.getBlockEntity(pos) instanceof NativeWallEntity host){var g=host.wallAppearance().geometry();if(g instanceof NativeFlatWallGeometry.Geometry flat&&flat.facing()==state.getValue(FACING)&&flat.depth()==state.getValue(DEPTH))return flat;}
        return NativeFlatWallGeometry.straight(state.getValue(FACING),state.getValue(DEPTH));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return NativeFlatWallGeometry.collision(geometry(state,level,pos));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()&&CubeConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack item=new ItemStack(this);if(level.getBlockEntity(pos) instanceof NativeWallEntity tile)tile.saveToItem(item,level.registryAccess());return item;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){NativeWallLayouts.invalidateAt(level,pos);NativeFluidWorld.queueAt(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){if(level instanceof Level actual)NativeWallLayouts.invalidateAt(actual,pos);NativeFluidWorld.queueAt(level,pos);return state;}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){return NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return NativeFluidWorld.pickupBlock(level,pos);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
