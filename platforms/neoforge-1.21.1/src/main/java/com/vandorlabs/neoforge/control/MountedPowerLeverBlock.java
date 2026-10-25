package com.vandorlabs.neoforge.control;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Power-lever placement retains its horizontal heading independently from floor mounting. */
public final class MountedPowerLeverBlock extends MountedControlBlock {
    public static final DirectionProperty HEADING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED=BooleanProperty.create("powered"),FLOOR=BooleanProperty.create("floor");
    public final boolean twin;
    public final int defaultSize;
    public MountedPowerLeverBlock(boolean twin,int size,BlockBehaviour.Properties properties){super(false,properties,false);this.twin=twin;defaultSize=size;registerDefaultState(stateDefinition.any().setValue(HEADING,Direction.NORTH).setValue(POWERED,false).setValue(FLOOR,false));}
    @Override protected MapCodec<? extends net.minecraft.world.level.block.DirectionalBlock> codec(){return simpleCodec(p->new MountedPowerLeverBlock(twin,defaultSize,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HEADING,POWERED,FLOOR);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction face=context.getClickedFace();if(face==Direction.DOWN)return null;boolean floor=face==Direction.UP;return defaultBlockState().setValue(HEADING,floor?context.getHorizontalDirection().getOpposite():face).setValue(FLOOR,floor);}
    @Override public boolean active(BlockState state){return state.getValue(POWERED);}
    @Override public BlockState withActive(BlockState state,boolean value){return state.setValue(POWERED,value);}
    @Override public Direction outward(BlockState state){return state.getValue(FLOOR)?Direction.UP:state.getValue(HEADING);}
    @Override public int mountRotation(BlockState state){return state.getValue(FLOOR)?state.getValue(HEADING).get2DDataValue():0;}
    @Override public BlockState withMountRotation(BlockState state,int rotation){return state;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HEADING,rotation.rotate(state.getValue(HEADING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(HEADING)));}
    @Override protected void clickSound(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,boolean on){level.playSound(null,pos,net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,.3F,on?.6F:.5F);}
    @Override public Block itemBlock(){return twin?ControlRegistration.SMALL_POWER.get():ControlRegistration.INDUSTRIAL_POWER.get();}
}
