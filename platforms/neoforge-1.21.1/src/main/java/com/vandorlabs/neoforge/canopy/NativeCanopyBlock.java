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
public class NativeCanopyBlock extends Block implements EntityBlock,NativeAuthoredBlockRay {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final String kind;private final Supplier<? extends BlockEntityType<?>> type;private final Supplier<net.minecraft.world.inventory.MenuType<NativeCanopyMenu>> menu;
    public NativeCanopyBlock(String kind,Supplier<? extends BlockEntityType<?>> type,Properties properties){this(kind,type,()->null,properties);}
    public NativeCanopyBlock(String kind,Supplier<? extends BlockEntityType<?>> type,Supplier<net.minecraft.world.inventory.MenuType<NativeCanopyMenu>> menu,Properties properties){super(properties.noOcclusion().dynamicShape().forceSolidOn());this.menu=java.util.Objects.requireNonNull(menu);NativeCanopyModels.builtin().dimensions(kind);this.kind=kind;this.type=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    public String kind(){return kind;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeCanopyEntity(type.get(),pos,state,kind);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return actual==type.get()?(world,pos,current,tile)->NativeCanopyLifecycle.prepare((NativeCanopyEntity)tile):null;}
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){var raw=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);if(!(raw instanceof NativeCanopyEntity host)||host.links().anchor()!=null&&!host.getBlockPos().equals(host.links().anchor()))return java.util.List.of();var drop=NativeCanopyItem.pick(asItem(),host.inert()?host:NativeCanopyLifecycle.owner(host));return drop.isEmpty()?java.util.List.of():java.util.List.of(drop);}
    @Override public void playerDestroy(Level level,net.minecraft.world.entity.player.Player player,BlockPos pos,BlockState state,BlockEntity raw,net.minecraft.world.item.ItemStack tool){if(raw instanceof NativeCanopyEntity host&&!host.suppressHarvest){var drop=NativeCanopyItem.pick(asItem(),host);if(!drop.isEmpty())Block.popResource(level,pos,drop);}}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){var raw=level.getBlockEntity(pos);return NativeCanopyItem.pick(asItem(),raw instanceof NativeCanopyEntity host?(host.inert()?host:NativeCanopyLifecycle.owner(host)):null);}
    protected boolean removeOwnedCell(net.minecraft.server.level.ServerLevel world,BlockPos pos){return world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!NativeCanopyItem.mutating(level,pos)&&!com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,pos)&&level.getBlockEntity(pos) instanceof NativeCanopyEntity old){if(level.isClientSide)NativeCanopyLifecycle.retireClientRoot(old);else NativeCanopyRemoval.remove(old,state,pos,this::removeOwnedCell);}super.onRemove(state,level,pos,next,moving);}
    public final boolean openConfiguration(net.minecraft.world.entity.player.Player player,Level level,BlockPos pos){return NativeCanopyMenus.open(player,level,pos,menu.get());}
    private net.minecraft.world.InteractionResult interact(Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack held){
        if(held.getItem() instanceof NativeCanopyItem||held.getItem() instanceof com.vandorlabs.neoforge.NativeOwnsBlockInteraction)return net.minecraft.world.InteractionResult.PASS;
        boolean tool=held.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get());if(player.isShiftKeyDown()||tool){boolean opened=openConfiguration(player,level,pos);return opened?net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide):net.minecraft.world.InteractionResult.CONSUME;}
        var member=NativeCanopyLifecycle.at(level,pos);if(member==null)return net.minecraft.world.InteractionResult.PASS;var owner=NativeCanopyLifecycle.owner(member);if(!NativeCanopyControl.opening(owner))return net.minecraft.world.InteractionResult.PASS;if(!level.isClientSide){NativeCanopyLifecycle.prepare(owner);if(NativeCanopyMenus.usable(player,owner))NativeCanopyControl.toggle(owner);}return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,BlockHitResult hit){return interact(level,pos,player,player.getMainHandItem());}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;var result=interact(level,pos,player,stack);return result==net.minecraft.world.InteractionResult.PASS?net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:result==net.minecraft.world.InteractionResult.FAIL?net.minecraft.world.ItemInteractionResult.FAIL:net.minecraft.world.ItemInteractionResult.SUCCESS;}
    @Override protected RenderShape getRenderShape(BlockState state){return kind.equals("regular_canopy_glass")?RenderShape.MODEL:RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){if(world.getBlockEntity(pos) instanceof NativeCanopyEntity host){var owner=world instanceof Level?NativeCanopyLifecycle.owner(host):host;var geometry=owner.publication().geometry();if(geometry!=null)return geometry.outlineAt(pos.getX(),pos.getY(),pos.getZ());}return Shapes.block();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,Vec3 start,Vec3 end){
        if(!(world.getBlockEntity(pos) instanceof NativeCanopyEntity host)||!host.available())return Shapes.block().clip(start,end,pos);
        if(world instanceof Level level){var owner=NativeCanopyLifecycle.owner(host);if(owner.publication().draws())return NativeCanopyTargets.traceOwner(owner,start,end);var anchor=host.links().anchor();if(anchor!=null&&!anchor.equals(pos)&&level.hasChunkAt(anchor)){var parent=NativeCanopyLifecycle.at(level,anchor);if(parent==null||!parent.getBlockState().is(this))return Shapes.block().clip(start,end,pos);}return null;}
        var geometry=host.publication().geometry();if(geometry==null)return null;
        var hit=geometry.trace(new CanopyPoint(start.x,start.y,start.z),new CanopyPoint(end.x,end.y,end.z));return hit==null?null:new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.face),pos,false);
    }
}
