package com.vandorlabs.neoforge.light;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.light.ProgrammableLightPolicy;
import com.vandorlabs.neoforge.placement.NativePlacementHit;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Unregistered Light housing and lifecycle. Renderer, item ownership and menu binding remain separate. */
public class NativeLightBlock extends BaseEntityBlock {
    public enum Kind {FULL,FRAME,SLAB}
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    private static final java.util.concurrent.atomic.AtomicLong IDS=new java.util.concurrent.atomic.AtomicLong();
    private final String joiningIdentity="native-light-block-"+IDS.incrementAndGet();
    private final Kind kind;private final Supplier<BlockEntityType<NativeLightEntity>> entities;
    protected NativeLightBlock(Kind kind,Properties properties,Supplier<BlockEntityType<NativeLightEntity>> entities){super(properties.noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false));this.kind=java.util.Objects.requireNonNull(kind);this.entities=java.util.Objects.requireNonNull(entities);BlockState state=stateDefinition.any().setValue(FACING,Direction.NORTH);if(kind==Kind.SLAB)state=state.setValue(HALF,Half.BOTTOM);registerDefaultState(state);}
    public static NativeLightBlock create(Kind kind,Properties properties,Supplier<BlockEntityType<NativeLightEntity>> entities){return kind==Kind.SLAB?new Slab(properties,entities):new NativeLightBlock(kind,properties,entities);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    final BlockEntityType<NativeLightEntity> entityType(){return entities.get();}
    public final Kind kind(){return kind;}final String joiningIdentity(){return joiningIdentity;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Player player=context.getPlayer();Direction facing=player==null?context.getHorizontalDirection().getOpposite():player.getXRot()>45?Direction.UP:player.getXRot()< -45?Direction.DOWN:context.getHorizontalDirection().getOpposite();BlockState state=defaultBlockState().setValue(FACING,facing);if(kind==Kind.SLAB){float y=(float)NativePlacementHit.hit(context).y;boolean upper=context.getClickedFace()==Direction.DOWN||context.getClickedFace()!=Direction.UP&&y>.5F;state=state.setValue(HALF,upper?Half.TOP:Half.BOTTOM);}return state;}
    private VoxelShape shape(BlockState state,BlockGetter world,BlockPos pos){boolean small=kind==Kind.FRAME&&world.getBlockEntity(pos) instanceof NativeLightEntity owner&&owner.visual().small();return NativeLightGeometry.shape(kind.ordinal(),state.getValue(FACING),kind==Kind.SLAB&&state.getValue(HALF)==Half.TOP,small);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state,world,pos);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state,world,pos);}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return kind==Kind.SLAB?shape(state,world,pos):Shapes.empty();}
    @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
    @Override protected float getShadeBrightness(BlockState state,BlockGetter world,BlockPos pos){return 1;}
    @Override public int getLightEmission(BlockState state,BlockGetter world,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(world,pos);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.OFF_HAND)return ItemInteractionResult.SUCCESS;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return openConfiguration(player,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,BlockHitResult hit){boolean sneaking=player.isShiftKeyDown(),creative=player.isCreative();if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeLightEntity owner){if(ProgrammableLightPolicy.opensConfiguration(true,true,sneaking,creative))openConfiguration(owner,player);else if(ProgrammableLightPolicy.togglesInteraction(true,true,sneaking,owner.settings().light().trigger))owner.manualUse(player,hit.getDirection());}return ProgrammableLightPolicy.consumesInteraction(true,sneaking,creative)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.PASS;}
    protected net.minecraft.world.inventory.MenuType<NativeLightMenu> menuType(){return null;}
    public final boolean openConfiguration(Player player,Level world,BlockPos pos){return NativeLightConfiguration.open(player,world,pos,menuType());}
    protected void openConfiguration(NativeLightEntity owner,Player player){openConfiguration(player,owner.getLevel(),owner.getBlockPos());}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader world,BlockPos pos,Player player){return NativeLightItem.pick(asItem(),world.getBlockEntity(pos) instanceof NativeLightEntity owner?owner:null);}
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var raw=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);return java.util.List.of(NativeLightItem.pick(asItem(),raw instanceof NativeLightEntity owner?owner:null));}
    @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player){if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeLightEntity owner)owner.prepareRecoveryDrop(owner.recoveryRequired()&&(player.isCreative()||!state.canHarvestBlock(world,pos,player))?NativeLightItem.pick(asItem(),owner):ItemStack.EMPTY);return super.playerWillDestroy(world,pos,state,player);}
    @Override protected void onPlace(BlockState state,Level world,BlockPos pos,BlockState previous,boolean moving){super.onPlace(state,world,pos,previous,moving);NativeLightService.queue(world,pos);}
    @Override protected void neighborChanged(BlockState state,Level world,BlockPos pos,Block block,BlockPos from,boolean moving){NativeLightService.queue(world,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos other){if(world instanceof Level level)NativeLightService.queue(level,pos);return state;}
    @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!world.isClientSide&&!NativeLightItem.mutating(world,pos)&&world.getBlockEntity(pos) instanceof NativeLightEntity owner){var recovery=owner.takeRecoveryDrop();if(owner.recoveryRequired()&&!recovery.isEmpty())Block.popResource(world,pos,recovery);}super.onRemove(state,world,pos,next,moving);if(state.getBlock()!=next.getBlock())NativeLightService.queue(world,pos);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    private static final class Slab extends NativeLightBlock {Slab(Properties properties,Supplier<BlockEntityType<NativeLightEntity>> entities){super(Kind.SLAB,properties,entities);}@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,HALF);}}
}
