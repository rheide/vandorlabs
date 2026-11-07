package com.vandorlabs.neoforge.credentials;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.ControlMountSettings;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Inactive standalone reader block; dedicated menu and public registry binding are supplied separately. */
public class NativeCredentialReaderBlock extends DirectionalBlock implements EntityBlock,NativeMountedControl,NativeMountedControlGeometry.Profile {
    private final boolean chip;
    private final Supplier<? extends BlockEntityType<?>> entities;
    public NativeCredentialReaderBlock(Properties properties,boolean chip,Supplier<? extends BlockEntityType<?>> entities){super(properties.noOcclusion().dynamicShape());this.chip=chip;this.entities=java.util.Objects.requireNonNull(entities);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(NativeMountedSwitchBlock.ON,false).setValue(NativeMountedSwitchBlock.ROTATION,0));}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return MapCodec.unit(this);}
    public boolean chip(){return chip;}
    public String mountedFamily(){return chip?"data_chip_slot":"access_card_slot";}
    public boolean isMomentary(){return false;}
    public boolean powered(BlockState state){return state.getValue(NativeMountedSwitchBlock.ON);}
    public BlockState withPowered(BlockState state,boolean on){return state.setValue(NativeMountedSwitchBlock.ON,on);}
    public Direction supportDirection(BlockState state){return state.getValue(FACING).getOpposite();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,NativeMountedSwitchBlock.ON,NativeMountedSwitchBlock.ROTATION);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeCredentialReaderEntity(entities.get(),pos,state,chip);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){if(context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).contains("NativeReaderRecovery"))return null;Direction face=context.getClickedFace();int rotation=face.getAxis()==Direction.Axis.Y?(context.getHorizontalDirection().get2DDataValue()+(face==Direction.DOWN?2:0))&3:0;BlockState state=defaultBlockState().setValue(FACING,face).setValue(NativeMountedSwitchBlock.ROTATION,rotation);return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return supported(state,level,pos);}
    private VoxelShape shape(BlockState state,BlockGetter world,BlockPos pos,boolean outline){var raw=world.getBlockEntity(pos);var mount=raw instanceof NativeCredentialReaderEntity owner?owner.mountSettings():ControlMountSettings.of(0,0,0);int rotation=raw instanceof NativeCredentialReaderEntity owner?owner.mountRotation():state.getValue(NativeMountedSwitchBlock.ROTATION);return outline?NativeReaderGeometry.outline(state,mount,rotation,chip):NativeReaderGeometry.collision(state,mount,rotation);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state,level,pos,true);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state,level,pos,false);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner)owner.place(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("RedstoneChannelSettings"),state.getValue(NativeMountedSwitchBlock.ROTATION));}
    public ItemStack configured(NativeCredentialReaderEntity owner){var stack=new ItemStack(this);if(owner!=null){var tag=new net.minecraft.nbt.CompoundTag();tag.put("RedstoneChannelSettings",owner.configuration());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return stack;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){return configured(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner?owner:null);}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){var tile=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);if(tile instanceof NativeCredentialReaderEntity owner&&owner.inert())return java.util.List.of();return java.util.List.of(configured(tile instanceof NativeCredentialReaderEntity owner?owner:null));}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner){var recovery=owner.recoveryForRemoval(asItem());if(!recovery.isEmpty())Block.popResource(level,pos,recovery);var token=owner.takeForDrop();if(!token.isEmpty())Block.popResource(level,pos,token);}super.onRemove(state,level,pos,next,moving);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(level instanceof ServerLevel server&&level.hasChunkAt(pos.relative(supportDirection(state)))&&!state.canSurvive(level,pos))dropUnsupported(state,server,pos);}
    /** Both Configurizer and ordinary sneaking route through this dedicated family hook. */
    public boolean openConfiguration(Player player,Level level,BlockPos pos){return false;}
    private boolean activate(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(!(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner)||!NativeConfigurationAccess.canEditDialog(player,owner))return false;
        var held=player.getItemInHand(hand);if(player.isShiftKeyDown()||held.is(VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,level,pos);
        if(level.isClientSide){if(held.isEmpty())return owner.visual().contents().occupied()||hand!=InteractionHand.MAIN_HAND||!NativeCredentialItems.accepts(player.getOffhandItem(),chip);return NativeCredentialItems.accepts(held,chip);}
        return player instanceof ServerPlayer server&&owner.interact(server,hand);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return activate(level,pos,player,hand)?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(level,pos,player,InteractionHand.MAIN_HAND)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner)owner.scheduledTick();}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override public int physicalSignal(BlockState state,BlockGetter level,BlockPos pos){return powered(state)&&level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity owner?owner.physicalLevel():0;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return physicalSignal(state,level,pos);}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return physicalSignal(state,level,pos);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
