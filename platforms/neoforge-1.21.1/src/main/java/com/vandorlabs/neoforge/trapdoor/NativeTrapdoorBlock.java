package com.vandorlabs.neoforge.trapdoor;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Unregistered host housing; no public registry, item, dialog or global moved-leaf routing is installed here. */
public abstract class NativeTrapdoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    private final boolean diagonal;
    protected NativeTrapdoorBlock(Properties properties,boolean diagonal){super(properties);this.diagonal=diagonal;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(HALF,Half.BOTTOM));}
    public final boolean diagonal(){return diagonal;}protected abstract BlockEntityType<?> entityType();
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,OPEN,HALF);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeTrapdoorEntity(entityType(),pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:(world,pos,block,tile)->{if(tile instanceof NativeTrapdoorEntity trapdoor)trapdoor.tickServer();};}
    private static final NativeTrapdoorGeometry.Snapshot[] MISSING=new NativeTrapdoorGeometry.Snapshot[32];private static int missingBytes;
    private static synchronized NativeTrapdoorGeometry.Snapshot missing(boolean diagonal,Direction face,boolean top,boolean open){int turns=NativeTrapdoorGeometry.turns(face),index=(diagonal?16:0)|turns<<2|(top?2:0)|(open?1:0);var found=MISSING[index];if(found==null){found=NativeTrapdoorGeometry.snapshot(NativeTrapdoorGeometry.missing(diagonal,turns,top),open);int cost=found.retainedBytes();if((long)missingBytes+cost>2*1024*1024)throw new IllegalStateException("Trapdoor fallback geometry exceeds bounded budget");missingBytes+=cost;MISSING[index]=found;}return found;}
    private NativeTrapdoorGeometry.Snapshot geometry(BlockState state,BlockGetter level,BlockPos pos){var raw=level.getBlockEntity(pos);if(raw instanceof NativeTrapdoorEntity tile&&!tile.inert()&&tile.trapdoorVisual()!=null)return tile.geometry();return missing(diagonal,state.getValue(FACING),state.getValue(HALF)==Half.TOP,state.getValue(OPEN));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return geometry(state,level,pos).outlineShape();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var raw=level.getBlockEntity(pos);if(raw instanceof NativeTrapdoorEntity tile&&!tile.inert()&&tile.geometry()!=null)return tile.collisionShape();return NativeTrapdoorEntity.localCollisionShape(geometry(state,level,pos));}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){var tile=NativeTrapdoorEntity.at(level,pos);if(tile!=null)tile.scheduleRefresh();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){NativeTrapdoorEntity old=NativeTrapdoorEntity.at(level,pos);var others=!state.is(next.getBlock())&&old!=null?old.group():java.util.List.<NativeTrapdoorEntity>of();super.onRemove(state,level,pos,next,moving);for(var tile:others)if(tile!=old)tile.scheduleRefresh();}
}
