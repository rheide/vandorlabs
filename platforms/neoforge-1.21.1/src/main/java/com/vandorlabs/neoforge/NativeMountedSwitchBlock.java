package com.vandorlabs.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
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

/** Current push button and split rocker semantics, with authored mounted component collision. */
public final class NativeMountedSwitchBlock extends DirectionalBlock implements EntityBlock,NativeMountedControl {
    public static final BooleanProperty ON=BooleanProperty.create("on");
    public static final IntegerProperty ROTATION=IntegerProperty.create("rotation",0,3);
    public static final MapCodec<NativeMountedSwitchBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(propertiesCodec(),Codec.BOOL.fieldOf("momentary").forGetter(NativeMountedSwitchBlock::isMomentary)).apply(instance,NativeMountedSwitchBlock::new));
    private static final ControlMountSettings DEFAULT_MOUNT=ControlMountSettings.of(0,0,0);
    private final boolean momentary;
    public NativeMountedSwitchBlock(BlockBehaviour.Properties properties,boolean momentary){super(properties);this.momentary=momentary;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ON,false).setValue(ROTATION,0));}
    public boolean isMomentary(){return momentary;}
    @Override public boolean powered(BlockState state){return state.getValue(ON);}
    @Override public BlockState withPowered(BlockState state,boolean value){return state.setValue(ON,value);}
    @Override public Direction supportDirection(BlockState state){return state.getValue(FACING).getOpposite();}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ON,ROTATION);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeBinaryControlBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction facing=context.getClickedFace();int rotation=facing.getAxis()==Direction.Axis.Y?(context.getHorizontalDirection().get2DDataValue()+(facing==Direction.DOWN?2:0))&3:0;BlockState state=defaultBlockState().setValue(FACING,facing).setValue(ROTATION,rotation);return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;}
    @Override protected boolean canSurvive(BlockState state,LevelReader world,BlockPos pos){BlockPos support=pos.relative(state.getValue(FACING).getOpposite());return world.hasChunkAt(support)&&world.getBlockState(support).isFaceSturdy(world,support,state.getValue(FACING));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var tile=world.getBlockEntity(pos);var mount=tile instanceof NativeBinaryControlBlockEntity control?control.mountSettings():DEFAULT_MOUNT;int rotation=tile instanceof NativeBinaryControlBlockEntity control?control.mountRotation():state.getValue(ROTATION);return NativeMountedControlGeometry.shape(state,mount,rotation);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tile.place(data.getCompound("RedstoneChannelSettings"),state.getValue(ROTATION));}}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(this);if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){var tag=new net.minecraft.nbt.CompoundTag();tag.put("RedstoneChannelSettings",tile.configuration());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return stack;}
    public void dropUnsupported(BlockState state,ServerLevel level,BlockPos pos){if(!level.hasChunkAt(pos)||level.getBlockState(pos).getBlock()!=this)return;Block.dropResources(state,level,pos,level.getBlockEntity(pos));level.removeBlock(pos,false);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){if(level instanceof ServerLevel server){BlockPos support=pos.relative(state.getValue(FACING).getOpposite());if(server.hasChunkAt(support)&&!state.canSurvive(server,pos))dropUnsupported(state,server,pos);}}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moving){if(state.getBlock()!=after.getBlock()&&!level.isClientSide){if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile)com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(tile);if(state.getValue(ON)){BlockPos support=pos.relative(state.getValue(FACING).getOpposite());if(level.hasChunkAt(support))for(Direction face:Direction.values()){BlockPos target=support.relative(face);if(level.hasChunkAt(target))level.neighborChanged(target,this,pos);}}}super.onRemove(state,level,pos,after,moving);}
    public InteractionResult activate(BlockState state,Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown())return BinaryControlConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;if(!player.isAlive()||player.isSpectator()||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||level.getBlockState(pos).getBlock()!=this)return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile))return InteractionResult.PASS;
        boolean on=momentary||!state.getValue(ON);
        com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch.apply(()->{
        if(!momentary||!state.getValue(ON)){level.setBlock(pos,state.setValue(ON,on),2);tile.setLocalOn(on);tile.notifyPhysical();}
        if(momentary)level.scheduleTick(pos,this,20);
        level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.6f,on?.7f:.5f);});return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(state,level,pos,player);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!momentary||!state.getValue(ON))return;com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch.apply(()->{if(level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile){tile.setLocalOn(false);level.setBlock(pos,state.setValue(ON,false),2);tile.notifyPhysical();}level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.6f,.5f);});}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){if(!state.getValue(ON))return 0;return level.getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity tile?(tile.localRedstone()?tile.getOutputLevel():0):15;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
