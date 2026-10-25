package com.vandorlabs.neoforge.control;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Existing mounted switch interaction, six-face support and native scheduled button release. */
public class MountedControlBlock extends DirectionalBlock implements EntityBlock {
    public static final BooleanProperty ON=BooleanProperty.create("on");
    public static final IntegerProperty ROTATION=IntegerProperty.create("rotation",0,3);
    public final boolean momentary;
    public MountedControlBlock(boolean momentary,BlockBehaviour.Properties properties){this(momentary,properties,true);}
    protected MountedControlBlock(boolean momentary,BlockBehaviour.Properties properties,boolean binary){super(properties);this.momentary=momentary;if(binary)registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ON,false).setValue(ROTATION,0));}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return simpleCodec(properties->new MountedControlBlock(momentary,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ON,ROTATION);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MountedControlEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction face=context.getClickedFace();int rotation=face.getAxis().isVertical()?(context.getHorizontalDirection().get2DDataValue()+(face==Direction.DOWN?2:0))&3:0;return defaultBlockState().setValue(FACING,face).setValue(ROTATION,rotation);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){Direction face=outward(state);BlockPos support=pos.relative(face.getOpposite());return level.hasChunkAt(support)&&level.getBlockState(support).isFaceSturdy(level,support,face);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(!level.isClientSide&&!state.canSurvive(level,pos)){dropResources(state,level,pos,level.getBlockEntity(pos));level.removeBlock(pos,false);}}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ControlGeometry.shape(state,level,pos,false);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return ControlGeometry.shape(state,level,pos,true);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return active(state)?level.getBlockEntity(pos) instanceof MountedControlEntity tile?tile.outputLevel():15:0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null;}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(momentary&&active(state)&&level.getBlockEntity(pos) instanceof MountedControlEntity tile){tile.setLocalOn(false);clickSound(level,pos,false);}}
    public InteractionResult activate(BlockState state,Level level,BlockPos pos,Player player){
        if(!(level.getBlockEntity(pos) instanceof MountedControlEntity tile))return InteractionResult.PASS;
        if(player.isShiftKeyDown()){if(player instanceof ServerPlayer server)ControlMenu.open(server,tile);return InteractionResult.sidedSuccess(level.isClientSide);}
        if(!level.isClientSide){tile.setLocalOn(momentary||!active(state));if(momentary)level.scheduleTick(pos,this,20);clickSound(level,pos,momentary||tile.localOn());}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(state,level,pos,player);}
    protected void clickSound(Level level,BlockPos pos,boolean on){level.playSound(null,pos,net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,.6F,on?.7F:.5F);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){super.onRemove(state,level,pos,next,moving);if(active(state))level.updateNeighborsAt(pos.relative(outward(state).getOpposite()),this);}}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(itemBlock());if(level.getBlockEntity(pos) instanceof MountedControlEntity tile)stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(wrap(tile.configuration())));return stack;}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params){
        ItemStack stack=new ItemStack(itemBlock());var entity=params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if(entity instanceof MountedControlEntity tile)stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(wrap(tile.configuration())));
        return java.util.List.of(stack);
    }
    public boolean active(BlockState state){return state.getValue(ON);}
    public BlockState withActive(BlockState state,boolean active){return state.setValue(ON,active);}
    public Direction outward(BlockState state){return state.getValue(FACING);}
    public int mountRotation(BlockState state){return state.getValue(ROTATION);}
    public BlockState withMountRotation(BlockState state,int rotation){return state.setValue(ROTATION,outward(state).getAxis().isVertical()?rotation:0);}
    public Block itemBlock(){return this;}
    static net.minecraft.nbt.CompoundTag wrap(net.minecraft.nbt.CompoundTag settings){var tag=new net.minecraft.nbt.CompoundTag();tag.put("RedstoneChannelSettings",settings);return tag;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof MountedControlEntity tile){tile.placedRotation(mountRotation(state));var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data!=null&&data.copyTag().contains("RedstoneChannelSettings",10))tile.applyConfiguration(data.copyTag().getCompound("RedstoneChannelSettings"));}
    }
}
