package com.vandorlabs.neoforge.ramp;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
/** The scan face never rotates the half-height housing out of its horizontal plane. */
public class NativeRampSlabBlock extends NativeRampBlock {
 public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
 private static final VoxelShape BOTTOM=Block.box(0,0,0,16,8,16),TOP=Block.box(0,8,0,16,16,16);
 public NativeRampSlabBlock(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(properties,entities);registerDefaultState(defaultBlockState().setValue(HALF,Half.BOTTOM));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(HALF);}
 @Override public BlockState legacyState(int meta){int face=meta&7;var scan=face<6?Direction.from3DDataValue(face):Direction.NORTH;return defaultBlockState().setValue(ACTIVE_FACE,scan).setValue(FACING,scan.getAxis().isVertical()?Direction.NORTH:scan).setValue(HALF,(meta&8)!=0?Half.TOP:Half.BOTTOM);}
 @Override public int legacyMeta(BlockState state){return scanFacing(state).get3DDataValue()|(state.getValue(HALF)==Half.TOP?8:0);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var side=context.getClickedFace();double y=com.vandorlabs.neoforge.placement.NativePlacementHit.hit(context).y;return super.getStateForPlacement(context).setValue(HALF,side==Direction.DOWN||side!=Direction.UP&&y>.5?Half.TOP:Half.BOTTOM);}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return state.getValue(HALF)==Half.TOP?TOP:BOTTOM;}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
}
