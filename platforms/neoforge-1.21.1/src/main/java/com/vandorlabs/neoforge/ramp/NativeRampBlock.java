package com.vandorlabs.neoforge.ramp;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
/** Inactive controller housing; generation and configuration are installed by its runtime separately. */
public class NativeRampBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING,ACTIVE_FACE=DirectionProperty.create("active_face");
 public static final BooleanProperty ACTIVE=BooleanProperty.create("active");public static final IntegerProperty MODE=IntegerProperty.create("mode",0,6);
 private final Supplier<? extends BlockEntityType<?>> entities;
 public NativeRampBlock(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(properties);this.entities=java.util.Objects.requireNonNull(entities);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE_FACE,Direction.NORTH).setValue(ACTIVE,false).setValue(MODE,0));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ACTIVE_FACE,ACTIVE,MODE);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 public static Direction scanFacing(BlockState state){var scan=state.getValue(ACTIVE_FACE);return scan.getAxis().isVertical()?scan:state.getValue(FACING);}
 public BlockState legacyState(int meta){var scan=(meta&8)!=0?((meta&1)==0?Direction.UP:Direction.DOWN):Direction.from2DDataValue(meta&3);return defaultBlockState().setValue(FACING,scan.getAxis().isVertical()?Direction.NORTH:scan).setValue(ACTIVE_FACE,scan).setValue(ACTIVE,(meta&4)!=0);}
 public int legacyMeta(BlockState state){var scan=scanFacing(state);return (scan.getAxis().isVertical()?8|(scan==Direction.DOWN?1:0):scan.get2DDataValue())|(state.getValue(ACTIVE)?4:0);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var facing=context.getHorizontalDirection();var player=context.getPlayer();var scan=player!=null&&player.getXRot()>45?Direction.DOWN:player!=null&&player.getXRot()< -45?Direction.UP:facing;return defaultBlockState().setValue(FACING,facing).setValue(ACTIVE_FACE,scan);}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING))).setValue(ACTIVE_FACE,rotation.rotate(state.getValue(ACTIVE_FACE)));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
 static boolean updateModelState(NativeRampEntity owner,boolean active){if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||owner.inert()||owner.isRemoved()||!(owner.getBlockState().getBlock() instanceof NativeRampBlock))return false;var pos=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null||chunk.getBlockEntities().get(pos)!=owner)return false;var state=owner.getBlockState();var next=state.setValue(MODE,owner.settings().mode()).setValue(ACTIVE,active);return state!=next&&level.setBlock(pos,next,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
}
