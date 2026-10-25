package com.vandorlabs.neoforge.control;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Four-detent controls use the common mount and latch bank, with a distinct live level. */
public final class MountedSignalControlBlock extends MountedControlBlock {
    public static final IntegerProperty LEVEL=IntegerProperty.create("level",0,3);
    public final int controlType;
    public MountedSignalControlBlock(int type,BlockBehaviour.Properties properties){super(false,properties,false);controlType=type;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(ON,false).setValue(ROTATION,0).setValue(LEVEL,0));}
    @Override protected MapCodec<? extends net.minecraft.world.level.block.DirectionalBlock> codec(){return simpleCodec(p->new MountedSignalControlBlock(controlType,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ON,ROTATION,LEVEL);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(selectableType()&&next.getBlock() instanceof MountedSignalControlBlock signal&&signal.selectableType())return;super.onRemove(state,level,pos,next,moving);}
    public boolean selectableType(){return controlType>=0;}
    @Override public Block itemBlock(){return selectableType()?ControlRegistration.THRUSTER.get():this;}
    @Override public InteractionResult activate(BlockState state,Level level,BlockPos pos,Player player){if(!(level.getBlockEntity(pos) instanceof MountedControlEntity tile))return InteractionResult.PASS;if(player.isShiftKeyDown()){if(player instanceof ServerPlayer server)ControlMenu.open(server,tile);}else if(!level.isClientSide){tile.setStep((tile.step()+1)%4);level.playSound(null,pos,net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,.3F,.5F+tile.step()*.1F);}return InteractionResult.sidedSuccess(level.isClientSide);}
}
