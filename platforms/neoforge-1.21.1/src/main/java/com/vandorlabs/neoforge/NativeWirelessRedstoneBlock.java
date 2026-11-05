package com.vandorlabs.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

/** Solid directional endpoint; the authored port/type/arrow cube is unchanged. */
public final class NativeWirelessRedstoneBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<NativeWirelessRedstoneBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(propertiesCodec(),Codec.BOOL.fieldOf("transmitter").forGetter(NativeWirelessRedstoneBlock::transmitter)).apply(instance,NativeWirelessRedstoneBlock::new));
    private final boolean transmitter;
    public NativeWirelessRedstoneBlock(BlockBehaviour.Properties properties,boolean transmitter){super(properties);this.transmitter=transmitter;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    public boolean transmitter(){return transmitter;}
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeWirelessRedstoneBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var player=context.getPlayer();Direction face=context.getHorizontalDirection().getOpposite();
        if(player!=null){var pos=context.getClickedPos();if(Math.abs(player.getX()-pos.getX()-.5)<2&&Math.abs(player.getZ()-pos.getZ()-.5)<2){double eye=player.getEyeY()-pos.getY();if(eye>2)face=Direction.UP;else if(eye<0)face=Direction.DOWN;}}
        return defaultBlockState().setValue(FACING,face);
    }
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side==state.getValue(FACING).getOpposite();}
    @Override public boolean shouldCheckWeakPower(BlockState state,SignalGetter level,BlockPos pos,Direction side){return false;}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){if(level instanceof ServerLevel server&&(!NativeRedstoneChannels.open(server)||!server.hasChunkAt(pos)))return 0;if(!(level.getBlockEntity(pos) instanceof NativeWirelessRedstoneBlockEntity tile))return 0;return transmitter?(tile.localRedstone()&&side==state.getValue(FACING)?tile.localSignalLevel(0):0):(side==state.getValue(FACING).getOpposite()?tile.getOutputLevel():0);}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){if(level instanceof ServerLevel server&&NativeRedstoneChannels.open(server)&&server.hasChunkAt(pos)&&server.getBlockEntity(pos) instanceof NativeWirelessRedstoneBlockEntity tile)tile.sampleInput();}
    public InteractionResult activate(Level level,BlockPos pos,Player player){return WirelessConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return activate(level,pos,player);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState after,boolean moving){
        if(level instanceof ServerLevel serverLevel&&!NativeRedstoneChannels.open(serverLevel))return;
        if(state.getBlock()!=after.getBlock()&&level instanceof ServerLevel server){NativeSignalUpdateBatch.apply(()->{if(server.getBlockEntity(pos) instanceof NativeWirelessRedstoneBlockEntity tile)NativeRedstoneChannels.unregister(tile);super.onRemove(state,level,pos,after,moving);NativeRedstoneChannels.settlePublishedWorldChange(server);NativeWirelessRedstoneBlockEntity.notifyAround(server,pos,this);NativeWirelessRedstoneBlockEntity.notifyAround(server,pos.relative(state.getValue(FACING)),this);});}
        else super.onRemove(state,level,pos,after,moving);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
}
