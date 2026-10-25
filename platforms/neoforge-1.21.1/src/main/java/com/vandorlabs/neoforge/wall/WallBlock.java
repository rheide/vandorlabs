package com.vandorlabs.neoforge.wall;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.FlatPanelPlacement;
import com.vandorlabs.ui.WallDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Three flat wall profiles with shared placement, material and contour policies. */
public final class WallBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty INVERTED=BooleanProperty.create("inverted");
    public static final IntegerProperty DEPTH=IntegerProperty.create("depth",0,2);
    private final WallDialog.Profile profile;
    public WallBlock(WallDialog.Profile profile,BlockBehaviour.Properties properties){super(properties);this.profile=profile;registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(DEPTH,0).setValue(INVERTED,false));}
    public WallDialog.Profile profile(){return profile;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(properties->new WallBlock(profile,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,DEPTH,INVERTED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WallEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos support=context.getClickedPos().relative(context.getClickedFace().getOpposite());BlockState clicked=context.getLevel().getBlockState(support);
        Direction facing=clicked.is(this)?clicked.getValue(FACING):context.getHorizontalDirection().getOpposite();
        var hit=context.getClickLocation().subtract(context.getClickedPos().getX(),context.getClickedPos().getY(),context.getClickedPos().getZ());
        return defaultBlockState().setValue(FACING,facing).setValue(DEPTH,profile==WallDialog.Profile.FULL_PORTHOLE?0:FlatPanelPlacement.depth(facing.get3DDataValue(),(float)hit.x,(float)hit.z));
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return WallGeometry.collision(WallGeometry.live(state,level,pos));}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
    @Override public boolean hasDynamicLightEmission(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
    @Override public BlockState getStateAtViewpoint(BlockState state,BlockGetter level,BlockPos pos,Vec3 point){return NativeFluidWorld.viewpoint(state,level,pos,point);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){super.neighborChanged(state,level,pos,block,from,moving);NativeFluidWorld.queueAt(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){NativeFluidWorld.queueAt(level,pos);return super.updateShape(state,side,neighbor,level,pos,other);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        boolean wants=player.isCreative()&&player.isShiftKeyDown()||player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get());
        if(!wants||!(level.getBlockEntity(pos) instanceof WallEntity tile)||!NativeConfigurationAccess.canEdit(player,tile))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server)WallMenu.open(server,tile);return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof WallEntity tile)tile.saveToItem(stack,level.registryAccess());return stack;}
}
