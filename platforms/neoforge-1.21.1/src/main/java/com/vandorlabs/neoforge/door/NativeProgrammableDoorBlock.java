package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Actual two-part lifecycle; concrete production blocks must supply authored geometry and credential services. */
public abstract class NativeProgrammableDoorBlock extends BaseEntityBlock implements NativeDoorLinks, SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DoorHingeSide> HINGE=BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN,POWERED=BlockStateProperties.POWERED,PAIRED=BooleanProperty.create("paired");
    private final Supplier<BlockEntityType<NativeProgrammableDoorEntity>> entities;
    private final Supplier<MenuType<NativeDoorMenu>> menus;
    private final Supplier<Item> canonicalItem;
    private final boolean slidingModel;
    protected NativeProgrammableDoorBlock(Properties properties,Supplier<BlockEntityType<NativeProgrammableDoorEntity>> entities,Supplier<MenuType<NativeDoorMenu>> menus,Supplier<Item> canonicalItem,boolean slidingModel){
        super(properties.noOcclusion().dynamicShape().isRedstoneConductor((state,level,pos)->false).isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false));this.entities=entities;this.menus=menus;this.canonicalItem=canonicalItem;this.slidingModel=slidingModel;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER).setValue(HINGE,DoorHingeSide.LEFT).setValue(OPEN,false).setValue(POWERED,false).setValue(PAIRED,false));
    }
    public final boolean slidingModel(){return slidingModel;}
    protected boolean stagePart(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restorePart(Level level,BlockPos pos,BlockState previous){return level.setBlock(pos,previous,Block.UPDATE_KNOWN_SHAPE);}
    protected abstract VoxelShape orphanShape(BlockState resolved,boolean collision);
    protected abstract VoxelShape doorShape(NativeDoorSettings settings,BlockState resolved,boolean collision);
    protected abstract boolean controlPanelHit(NativeDoorSettings settings,BlockState resolved,com.vandorlabs.render.SpaceDoorControlPanel.Side side,BlockHitResult hit,BlockPos lower);
    protected abstract boolean securityOpen(NativeProgrammableDoorEntity host);
    protected abstract InteractionResult credentialInteraction(NativeProgrammableDoorEntity host,Player player,InteractionHand hand,BlockHitResult hit,boolean panelHit);
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,HALF,OPEN,HINGE,POWERED,PAIRED);}
    /** Current Forge doors explicitly use lightOpacity0; lighting/AO workers never need tile-dependent shapes. */
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
    @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    @Override protected float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos){return 1;}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeProgrammableDoorEntity(entities.get(),pos,state,false,slidingModel);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,entities.get(),(world,pos,current,host)->{host.tickDoor();host.tickCredentialReaders();host.tickWaterGush();});}
    private NativeDoorSettings settings(BlockGetter world,BlockPos pos,BlockState state){return (!(world instanceof ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&world.getBlockEntity(NativeDoorLifecycle.lower(state,pos)) instanceof NativeProgrammableDoorEntity host?host.settings():null;}
    public final BlockState resolved(LevelReader level,BlockPos pos,BlockState state){BlockState actual=NativeDoorLifecycle.resolved(level,pos,state);return actual.setValue(PAIRED,level instanceof Level world&&NativeDoorLifecycle.mate(world,NativeDoorLifecycle.lower(state,pos),this)!=null);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var data=settings(level,pos,state);var resolved=level instanceof LevelReader reader?resolved(reader,pos,state):state;return data==null?orphanShape(resolved,false):doorShape(data,resolved,false);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var data=settings(level,pos,state);var resolved=level instanceof LevelReader reader?resolved(reader,pos,state):state;return data==null?orphanShape(resolved,true):doorShape(data,resolved,true);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var pos=context.getClickedPos();var facing=context.getHorizontalDirection().getOpposite();var hit=context.getClickLocation().subtract(pos.getX(),pos.getY(),pos.getZ());return defaultBlockState().setValue(FACING,facing).setValue(HINGE,NativeDoorLifecycle.placementHinge(context.getLevel(),pos,this,facing,hit.x,hit.z));}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return state.getValue(HALF)==DoubleBlockHalf.UPPER?NativeDoorLifecycle.complete(level,pos.below(),this):NativeDoorLifecycle.supported(level,pos);}
    @Override public boolean configurationLinksLoaded(NativeProgrammableDoorEntity root){
        Level level=root.getLevel();if(level==null||!NativeDoorLifecycle.complete(level,root.getBlockPos(),this)||root.getBlockState().getValue(HALF)!=DoubleBlockHalf.LOWER||level.getBlockEntity(root.getBlockPos())!=root)return false;
        var state=resolved(level,root.getBlockPos(),root.getBlockState());Direction facing=state.getValue(FACING);BlockPos candidate=root.getBlockPos().relative(state.getValue(HINGE)==DoorHingeSide.LEFT?facing.getClockWise():facing.getCounterClockWise());return level.hasChunkAt(candidate)&&level.hasChunkAt(candidate.above());
    }
    @Override public NativeProgrammableDoorEntity configurationMate(NativeProgrammableDoorEntity root){if(!configurationLinksLoaded(root))return null;BlockPos pos=NativeDoorLifecycle.mate(root.getLevel(),root.getBlockPos(),this);return pos==null?null:NativeDoorLifecycle.root(root.getLevel(),pos);}
    public final boolean openConfiguration(Player player,Level level,BlockPos clicked){
        var root=NativeDoorLifecycle.root(level,clicked);if(root==null||!NativeConfigurationAccess.canEdit(player,root)||!configurationLinksLoaded(root))return false;var mate=configurationMate(root);if(mate!=null&&!NativeConfigurationAccess.canEditDialog(player,mate))return false;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new NativeDoorMenu(menus.get(),id,root),getName()),b->b.writeBlockPos(root.getBlockPos()));return true;
    }
    private InteractionResult interact(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var root=NativeDoorLifecycle.root(level,pos);if(root==null)return InteractionResult.FAIL;var actual=resolved(level,pos,state);boolean tool=player.getItemInHand(hand).is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get());boolean panelHit=controlPanelHit(root.settings(),actual,NativeDoorLifecycle.panelSide(root,actual),hit,root.getBlockPos());
        if(root.settings().securityMode()>0&&!player.isShiftKeyDown()&&!tool)return credentialInteraction(root,player,hand,hit,panelHit);
        if(hand==InteractionHand.MAIN_HAND&&(tool||player.isCreative()&&(player.isShiftKeyDown()||panelHit)))return openConfiguration(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;
        if(player.isShiftKeyDown())return InteractionResult.FAIL;
        if(root.settings().securityMode()>0)return credentialInteraction(root,player,hand,hit,panelHit);
        if(!level.isClientSide)NativeDoorLifecycle.toggle(level,pos);return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Called only after the moved-target transport has recomputed the authoritative ray and permissions. */
    public final InteractionResult useTarget(Player player,InteractionHand hand,BlockHitResult hit){
        var level=player.level();var pos=hit.getBlockPos();if(!NativeDoorLifecycle.loaded(level,pos)||!level.getBlockState(pos).is(this))return InteractionResult.FAIL;
        return interact(level.getBlockState(pos),level,pos,player,hand,hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return switch(interact(state,level,pos,player,hand,hit)){case SUCCESS,CONSUME,CONSUME_PARTIAL->ItemInteractionResult.SUCCESS;case FAIL->ItemInteractionResult.FAIL;default->ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,BlockPos from,boolean moving){
        com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);
        if(level.isClientSide){var root=NativeDoorLifecycle.root(level,pos);if(root!=null)root.markVisualDirty();return;}BlockPos lower=NativeDoorLifecycle.lower(state,pos);
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER&&level.hasChunkAt(lower)&&(!level.getBlockState(lower).is(this)||level.getBlockState(lower).getValue(HALF)!=DoubleBlockHalf.LOWER)){level.removeBlock(pos,false);return;}
        if(state.getValue(HALF)==DoubleBlockHalf.LOWER&&level.hasChunkAt(pos.below())&&!NativeDoorLifecycle.supported(level,pos)){level.destroyBlock(pos,true);return;}
        var root=NativeDoorLifecycle.root(level,pos);if(root!=null)root.localInputChanged();
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide){var lower=NativeDoorLifecycle.lower(state,pos);if(NativeDoorLifecycle.loaded(level,lower)&&level.getBlockEntity(lower) instanceof NativeProgrammableDoorEntity owner){var drop=owner.attachOpaqueCredentialRecovery(new ItemStack(canonicalItem.get()));if(level.getBlockEntity(pos) instanceof NativeProgrammableDoorEntity clicked)clicked.prepareRemovalDrop(player.isCreative()?ItemStack.EMPTY:drop);owner.prepareCreativeRecoveryDrop(player.isCreative()&&!owner.opaqueCredentialRecovery().isEmpty()?drop:ItemStack.EMPTY);}}
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&!NativeDoorPlacement.mutating(level,pos)){
            if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeProgrammableDoorEntity owner){
                var recovery=owner.takeCreativeRecoveryDrop();if(!recovery.isEmpty())Block.popResource(level,pos,recovery);
                for(int side=0;side<2;side++){var token=owner.takeCredentialForDrop(side);if(!token.isEmpty())Block.popResource(level,pos,token);}
            }
            BlockPos lower=NativeDoorLifecycle.lower(state,pos);
            if(level.hasChunkAt(lower)&&level.getBlockEntity(lower) instanceof NativeProgrammableDoorEntity owner)NativeDoorWaterGush.update(owner,true);
            BlockPos other=state.getValue(HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below();
            if(level.hasChunkAt(other)&&level.getBlockState(other).is(this))level.removeBlock(other,moving);
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){
        var captured=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);if(captured instanceof NativeProgrammableDoorEntity host){var prepared=host.removalDropCopy();if(!prepared.isEmpty())return List.of(prepared);}
        var origin=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);var level=context.getLevel();NativeProgrammableDoorEntity owner=null;
        if(origin!=null){var lower=NativeDoorLifecycle.lower(state,BlockPos.containing(origin));if(NativeDoorLifecycle.loaded(level,lower)&&level.getBlockState(lower).is(this)&&level.getBlockEntity(lower) instanceof NativeProgrammableDoorEntity host)owner=host;}
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER&&owner==null)return List.of();
        ItemStack drop=new ItemStack(canonicalItem.get());if(owner!=null)owner.attachOpaqueCredentialRecovery(drop);return List.of(drop);
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack stack=new ItemStack(canonicalItem.get());BlockPos lower=NativeDoorLifecycle.lower(state,pos);if(level.getBlockEntity(lower) instanceof NativeProgrammableDoorEntity root){var custom=new net.minecraft.nbt.CompoundTag();custom.put("SpaceDoorSettings",root.itemSettings());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));}return stack;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.pickupBlock(level,pos);}

}
