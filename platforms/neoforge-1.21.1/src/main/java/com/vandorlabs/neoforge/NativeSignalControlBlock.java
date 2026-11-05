package com.vandorlabs.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vandorlabs.persistence.ControlMountSettings;
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
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Current six-face, four-detent controls; selectable shapes retain one tile identity. */
public final class NativeSignalControlBlock extends DirectionalBlock implements EntityBlock,NativeMountedControl,NativeSignalControlPoses.Profile {
    public static final BooleanProperty ON=BooleanProperty.create("on");
    public static final IntegerProperty ROTATION=IntegerProperty.create("rotation",0,3);
    public static final IntegerProperty LEVEL=NativeSignalControlPoses.LEVEL;
    public static final MapCodec<NativeSignalControlBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(propertiesCodec(),Codec.INT.fieldOf("type").forGetter(NativeSignalControlBlock::controlType),Codec.BOOL.fieldOf("selectable").forGetter(NativeSignalControlBlock::hasSelectableType)).apply(instance,NativeSignalControlBlock::new));
    private static final ControlMountSettings DEFAULT_MOUNT=ControlMountSettings.of(0,0,0);
    private final int type;private final boolean selectable;
    public NativeSignalControlBlock(BlockBehaviour.Properties properties,int type,boolean selectable){super(properties);if(type<0||type>2||!selectable&&type!=0)throw new IllegalArgumentException("Invalid signal control family");this.type=type;this.selectable=selectable;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ON,false).setValue(ROTATION,0).setValue(LEVEL,0));}
    public int controlType(){return type;}
    public String controlKind(){return !selectable?"wall_slider":switch(type){case 0->"thruster_lever";case 1->"airliner_throttle";default->"fighter_throttle";};}
    public boolean hasSelectableType(){return selectable;}
    public boolean isMomentary(){return false;}
    public static NativeSignalControlBlock selectable(int type){return switch(type){case 0->VandorLabsBootstrap.THRUSTER_LEVER.get();case 1->VandorLabsBootstrap.AIRLINER_THROTTLE.get();case 2->VandorLabsBootstrap.FIGHTER_THROTTLE.get();default->throw new IllegalArgumentException("Invalid control type");};}
    public net.minecraft.world.level.block.Block canonical(){return selectable?VandorLabsBootstrap.THRUSTER_LEVER.get():this;}
    @Override public boolean powered(BlockState state){return state.getValue(ON);}
    @Override public BlockState withPowered(BlockState state,boolean value){return state.setValue(ON,value);}
    @Override public Direction supportDirection(BlockState state){return state.getValue(FACING).getOpposite();}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ON,ROTATION,LEVEL);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeSignalControlBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction facing=context.getClickedFace();int rotation=facing.getAxis()==Direction.Axis.Y?(context.getHorizontalDirection().get2DDataValue()+(facing==Direction.DOWN?2:0))&3:0;BlockState state=defaultBlockState().setValue(FACING,facing).setValue(ROTATION,rotation);return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return supported(state,level,pos);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var tile=world.getBlockEntity(pos);var mount=tile instanceof NativeBinaryControlBlockEntity control?control.mountSettings():DEFAULT_MOUNT;int rotation=tile instanceof NativeBinaryControlBlockEntity control?control.mountRotation():state.getValue(ROTATION);return NativeMountedControlGeometry.signalShape(state,mount,rotation);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tile.place(data.getCompound("RedstoneChannelSettings"),state.getValue(ROTATION));}}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(canonical());if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){var tag=new net.minecraft.nbt.CompoundTag();tag.put("RedstoneChannelSettings",tile.configuration());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return stack;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){NativeMountedControlActions.neighborChanged(this,state,level,pos);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moving){if(selectable&&after.getBlock() instanceof NativeSignalControlBlock block&&block.selectable)return;NativeMountedControlActions.removed(this,state,level,pos,after);super.onRemove(state,level,pos,after,moving);}
    public InteractionResult activate(BlockState state,Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown())return BinaryControlConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(NativeMountedControlActions.activeTile(this,level,pos,player) instanceof NativeSignalControlBlockEntity tile))return InteractionResult.PASS;
        int step=(tile.getStep()+1)%4;tile.setStep(step);level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.3f,.5f+tile.getStep()*.1f);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(state,level,pos,player);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return physicalSignal(state,level,pos);}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
