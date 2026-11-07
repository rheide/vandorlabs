package com.vandorlabs.neoforge.canopy;

import java.util.function.Supplier;
import com.vandorlabs.canopy.CanopyPoint;
import com.vandorlabs.neoforge.NativeAuthoredBlockRay;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Inactive standalone housing; detailed external collision routing is not installed by this class. */
public final class NativeCanopyBlock extends Block implements EntityBlock,NativeAuthoredBlockRay {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final String kind;private final Supplier<? extends BlockEntityType<?>> type;
    public NativeCanopyBlock(String kind,Supplier<? extends BlockEntityType<?>> type,Properties properties){super(properties.noOcclusion().dynamicShape().forceSolidOn());NativeCanopyModels.builtin().dimensions(kind);this.kind=kind;this.type=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    public String kind(){return kind;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeCanopyEntity(type.get(),pos,state,kind);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return actual==type.get()?(world,pos,current,tile)->NativeCanopyLifecycle.prepare((NativeCanopyEntity)tile):null;}
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){var raw=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);var drop=NativeCanopyItem.pick(asItem(),raw instanceof NativeCanopyEntity host?host:null);return drop.isEmpty()?java.util.List.of():java.util.List.of(drop);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){var raw=level.getBlockEntity(pos);return NativeCanopyItem.pick(asItem(),raw instanceof NativeCanopyEntity host?host:null);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&level.isClientSide&&!NativeCanopyItem.mutating(level,pos)&&!com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,pos)&&level.getBlockEntity(pos) instanceof NativeCanopyEntity old)NativeCanopyLifecycle.retireClientRoot(old);super.onRemove(state,level,pos,next,moving);}
    @Override protected RenderShape getRenderShape(BlockState state){return kind.equals("regular_canopy_glass")?RenderShape.MODEL:RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){if(world.getBlockEntity(pos) instanceof NativeCanopyEntity host){var geometry=host.publication().geometry();if(geometry!=null)return geometry.outline();}return Shapes.block();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,Vec3 start,Vec3 end){
        if(!(world.getBlockEntity(pos) instanceof NativeCanopyEntity host))return Shapes.block().clip(start,end,pos);var geometry=host.publication().geometry();if(geometry==null)return Shapes.block().clip(start,end,pos);
        var hit=geometry.trace(new CanopyPoint(start.x,start.y,start.z),new CanopyPoint(end.x,end.y,end.z));return hit==null?null:new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),pos,false);
    }
}
