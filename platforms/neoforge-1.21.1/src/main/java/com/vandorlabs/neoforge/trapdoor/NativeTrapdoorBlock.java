package com.vandorlabs.neoforge.trapdoor;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Programmable Trapdoor housing, interactions, collision and configured-item recovery. */
public abstract class NativeTrapdoorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    private final boolean diagonal;
    protected NativeTrapdoorBlock(Properties properties,boolean diagonal){super(properties);this.diagonal=diagonal;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(HALF,Half.BOTTOM));}
    public final boolean diagonal(){return diagonal;}protected abstract BlockEntityType<?> entityType();
    protected net.minecraft.world.inventory.MenuType<NativeTrapdoorMenu> menuType(){return null;}
    public final boolean openConfiguration(net.minecraft.world.entity.player.Player player,Level level,BlockPos pos){var type=menuType();return type!=null&&NativeTrapdoorConfiguration.open(player,level,pos,type);}
    /** Placement integration may complete a diagonal assembly before the ordinary toggle. */
    protected void prepareInteraction(NativeTrapdoorEntity tile,net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack held){if(tile.diagonal()&&tile.geometryPosition()!=1)NativeTrapdoorFormation.complete(tile,player,held);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return NativeTrapdoorPlacement.state(this,context);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){var raw=level.getBlockEntity(pos);if(!(raw instanceof NativeTrapdoorEntity tile)||tile.inert())return;if(!tile.itemInstalled()){var configuration=NativeTrapdoorItem.configuration(stack);int position=diagonal?NativeTrapdoorPlacement.diagonalMode(configuration,0):state.getValue(HALF)==Half.TOP?2:0;tile.installItemChoices(NativeTrapdoorItem.choices(configuration,diagonal,position,tile.itemSettings()).settings());}NativeTrapdoorFormation.placed(tile,placer,stack);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult hit,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){return NativeTrapdoorItem.pick(asItem(),level.getBlockEntity(pos) instanceof NativeTrapdoorEntity tile?tile:null);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeTrapdoorEntity tile)tile.prepareRecoveryDrop(tile.opaqueRecoveryRequired()&&(player.isCreative()||!state.canHarvestBlock(level,pos,player))?NativeTrapdoorItem.pick(asItem(),tile):net.minecraft.world.item.ItemStack.EMPTY);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){var raw=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);return java.util.List.of(NativeTrapdoorItem.pick(asItem(),raw instanceof NativeTrapdoorEntity tile?tile:null));}
    private net.minecraft.world.InteractionResult interact(Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,Direction face){
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return net.minecraft.world.InteractionResult.PASS;
        var held=player.getItemInHand(hand);
        if(player.isCreative()&&player.isShiftKeyDown()||held.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,level,pos)?net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide):net.minecraft.world.InteractionResult.FAIL;
        var tile=NativeTrapdoorEntity.at(level,pos);if(tile==null)return net.minecraft.world.InteractionResult.FAIL;
        if(!level.isClientSide&&tile.canUse(player,face)){prepareInteraction(tile,player,held);tile.requestOpen(player,!tile.open(),face);}
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){return interact(level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit.getDirection());}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return switch(interact(level,pos,player,hand,hit.getDirection())){case SUCCESS,CONSUME,CONSUME_PARTIAL->net.minecraft.world.ItemInteractionResult.SUCCESS;case FAIL->net.minecraft.world.ItemInteractionResult.FAIL;default->net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,OPEN,HALF);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeTrapdoorEntity(entityType(),pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:(world,pos,block,tile)->{if(tile instanceof NativeTrapdoorEntity trapdoor)trapdoor.tickServer();};}
    private static final NativeTrapdoorGeometry.Snapshot[] MISSING=new NativeTrapdoorGeometry.Snapshot[32];private static int missingBytes;
    private static synchronized NativeTrapdoorGeometry.Snapshot missing(boolean diagonal,Direction face,boolean top,boolean open){int turns=NativeTrapdoorGeometry.turns(face),index=(diagonal?16:0)|turns<<2|(top?2:0)|(open?1:0);var found=MISSING[index];if(found==null){found=NativeTrapdoorGeometry.snapshot(NativeTrapdoorGeometry.missing(diagonal,turns,top),open);int cost=found.retainedBytes();if((long)missingBytes+cost>2*1024*1024)throw new IllegalStateException("Trapdoor fallback geometry exceeds bounded budget");missingBytes+=cost;MISSING[index]=found;}return found;}
    private NativeTrapdoorGeometry.Snapshot geometry(BlockState state,BlockGetter level,BlockPos pos){var raw=level.getBlockEntity(pos);if(raw instanceof NativeTrapdoorEntity tile&&!tile.inert()&&tile.trapdoorVisual()!=null)return tile.geometry();return missing(diagonal,state.getValue(FACING),state.getValue(HALF)==Half.TOP,state.getValue(OPEN));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return geometry(state,level,pos).outlineShape();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var raw=level.getBlockEntity(pos);if(raw instanceof NativeTrapdoorEntity tile&&!tile.inert()&&tile.geometry()!=null)return tile.collisionShape();return NativeTrapdoorEntity.localCollisionShape(geometry(state,level,pos));}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);var tile=NativeTrapdoorEntity.at(level,pos);if(tile!=null)tile.scheduleRefresh();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){var raw=level.getBlockEntity(pos);if(!state.is(next.getBlock())&&!level.isClientSide&&!NativeTrapdoorItem.mutating(level,pos)&&raw instanceof NativeTrapdoorEntity old){if(!old.inert())NativeTrapdoorFormation.unpair(old,true);var recovery=old.takeRecoveryDrop();if(old.opaqueRecoveryRequired()&&!recovery.isEmpty())Block.popResource(level,pos,recovery);}super.onRemove(state,level,pos,next,moving);}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return Math.max(state.getLightEmission(),com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos));}
    @Override public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.entity.player.Player player,LevelAccessor level,BlockPos pos,BlockState state){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.pickupBlock(level,pos);}

}
