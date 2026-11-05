package com.vandorlabs.neoforge;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.persistence.PowerLeverSize;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Four retained IDs for two sized lever families; size belongs to the installed shared tile. */
public final class NativePowerLeverBlock extends HorizontalDirectionalBlock implements EntityBlock,NativeMountedControl {
    public static final BooleanProperty POWERED=BooleanProperty.create("powered"),FLOOR=BooleanProperty.create("floor");
    public static final MapCodec<NativePowerLeverBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(propertiesCodec(),Codec.BOOL.fieldOf("twin").forGetter(NativePowerLeverBlock::isTwin),Codec.INT.fieldOf("default_size").forGetter(NativePowerLeverBlock::defaultSize)).apply(instance,NativePowerLeverBlock::new));
    private static final ControlMountSettings DEFAULT_MOUNT=ControlMountSettings.of(0,0,0);
    private final boolean twin;private final int defaultSize;
    public NativePowerLeverBlock(BlockBehaviour.Properties properties,boolean twin,int defaultSize){super(properties);if(!PowerLeverSize.valid(defaultSize))throw new IllegalArgumentException("Invalid lever default size");this.twin=twin;this.defaultSize=defaultSize;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(FLOOR,false));}
    public boolean isTwin(){return twin;}public int defaultSize(){return defaultSize;}
    @Override public int defaultPowerLeverSize(){return defaultSize;}@Override public boolean isTwinPowerLever(){return twin;}
    @Override public boolean isMomentary(){return false;}@Override public boolean powered(BlockState state){return state.getValue(POWERED);}@Override public BlockState withPowered(BlockState state,boolean value){return state.setValue(POWERED,value);}
    @Override public Direction supportDirection(BlockState state){return state.getValue(FLOOR)?Direction.DOWN:state.getValue(FACING).getOpposite();}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,FLOOR);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeBinaryControlBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction face=context.getClickedFace();if(face==Direction.DOWN)return null;boolean floor=face==Direction.UP;var state=defaultBlockState().setValue(FLOOR,floor).setValue(FACING,floor?context.getHorizontalDirection().getOpposite():face);return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return supported(state,level,pos);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var tile=world.getBlockEntity(pos);var mount=tile instanceof NativeBinaryControlBlockEntity control?control.mountSettings():DEFAULT_MOUNT;int size=tile instanceof NativeBinaryControlBlockEntity control?control.getPowerLeverSize():defaultSize;return NativeMountedControlGeometry.powerShape(state,mount,size);}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile)tile.place(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("RedstoneChannelSettings"),0);}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){var stack=new ItemStack(twin?VandorLabsBootstrap.SMALL_POWER_LEVER_ITEM.get():VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER_ITEM.get());if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){var tag=new net.minecraft.nbt.CompoundTag();tag.put("RedstoneChannelSettings",tile.configuration());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return stack;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){NativeMountedControlActions.neighborChanged(this,state,level,pos);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moving){NativeMountedControlActions.removed(this,state,level,pos,after);super.onRemove(state,level,pos,after,moving);}
    public InteractionResult activate(BlockState state,Level level,BlockPos pos,Player player){return NativeMountedControlActions.activate(this,state,level,pos,player,.3f,.6f);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(state,level,pos,player);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return physicalSignal(state,level,pos);}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}@Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
