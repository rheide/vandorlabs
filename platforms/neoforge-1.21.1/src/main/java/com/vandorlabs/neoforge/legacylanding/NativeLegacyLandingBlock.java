package com.vandorlabs.neoforge.legacylanding;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/** Exact authored collision belongs to the permanent physical mount, clipped into its owned cells. */
public final class NativeLegacyLandingBlock extends HorizontalDirectionalBlock implements EntityBlock,NativeAuthoredBlockRay {
 public static final BooleanProperty LOWER=BooleanProperty.create("lower"),EXTENDED=BooleanProperty.create("extended");
 public final NativeLegacyLandingKind kind;
 public NativeLegacyLandingBlock(NativeLegacyLandingKind kind,BlockBehaviour.Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LOWER,false).setValue(EXTENDED,false));}
 @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(p->new NativeLegacyLandingBlock(kind,p));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LOWER,EXTENDED);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeLegacyLandingEntity(pos,state);}
 public static boolean loaded(BlockGetter world,BlockPos pos){if(world==null)return false;if(world instanceof Level level)return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);return !(world instanceof LevelReader reader)||reader.hasChunkAt(pos);}
 static NativeLegacyLandingEntity at(BlockGetter world,BlockPos pos){return loaded(world,pos)&&world.getBlockState(pos).getBlock() instanceof NativeLegacyLandingBlock&&world.getBlockEntity(pos) instanceof NativeLegacyLandingEntity tile&&!tile.inert()?tile:null;}
 public static NativeLegacyLandingEntity owner(BlockGetter world,BlockPos pos){var tile=at(world,pos);if(tile==null)return null;if(tile.physicalRoot())return tile;if(!tile.owned())return null;var root=at(world,tile.ownerPos());return root!=null&&root.physicalRoot()&&root.ownHelper(tile)&&NativeLegacyFootprints.declared(root,pos)?root:null;}
 static int localPower(ServerLevel world,BlockPos pos){int level=0;for(var side:Direction.values()){var next=pos.relative(side);if(loaded(world,next))level=Math.max(level,world.getSignal(next,side));}return level;}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
 @Override public void setPlacedBy(Level world,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity actor,ItemStack stack){super.setPlacedBy(world,pos,state,actor,stack);if(!world.isClientSide&&at(world,pos)!=null)at(world,pos).placed();}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world,BlockState state,BlockEntityType<T> type){return world.isClientSide?null:(level,pos,current,tile)->{if(tile instanceof NativeLegacyLandingEntity gear)gear.tick();};}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
 @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
 @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var root=owner(world,pos);return root==null?Shapes.empty():root.shape().cell(pos.subtract(root.getBlockPos()));}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
 @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
 @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
 @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,Vec3 start,Vec3 end){var root=owner(world,pos);if(root==null)return null;return root.shape().cell(pos.subtract(root.getBlockPos())).clip(start,end,pos);}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){if(!actor.getMainHandItem().isEmpty())return InteractionResult.PASS;var root=owner(world,pos);if(root==null)return InteractionResult.FAIL;var controller=root.controller();if(actor.isShiftKeyDown())return NativeLegacyLandingConfiguration.open(actor,world,pos)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.PASS;if(!NativeConfigurationAccess.canEditDialog(actor,controller))return InteractionResult.FAIL;if(!world.isClientSide)controller.target(actor,!controller.extended());return InteractionResult.sidedSuccess(world.isClientSide);}
 @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player actor,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.MAIN_HAND&&stack.is(VandorLabsBootstrap.CONFIGURIZER.get()))return NativeLegacyLandingConfiguration.open(actor,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
 @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player){var root=owner(world,pos);if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeLegacyLandingEntity clicked&&root!=null)clicked.prepareRemoval(player.isCreative()?ItemStack.EMPTY:configured(root));return super.playerWillDestroy(world,pos,state,player);}
 @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){if(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof NativeLegacyLandingEntity tile){var prepared=tile.removalDrop();if(prepared!=null)return prepared.isEmpty()?List.of():List.of(prepared);if(tile.physicalRoot()&&!tile.inert())return List.of(configured(tile));}return List.of();}
 private ItemStack configured(NativeLegacyLandingEntity root){var item=new ItemStack(NativeLegacyLandingBindings.ITEMS.get(root.kind()));root.writeItem(item);return item;}
 @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader world,BlockPos pos,Player actor){var root=owner(world,pos);return root==null?ItemStack.EMPTY:configured(root);}
 @Override protected void neighborChanged(BlockState state,Level world,BlockPos pos,Block changed,BlockPos from,boolean moving){if(world.isClientSide||NativeLegacyFootprints.mutating(world,pos))return;if(world.getBlockEntity(pos) instanceof NativeLegacyLandingEntity tile){tile.inputChanged();NativeLegacyUpdates.topology(tile);var root=tile.physical();if(root!=null)NativeLegacyUpdates.queue(root);}}
 @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&!world.isClientSide&&!NativeLegacyFootprints.mutating(world,pos)&&world.getBlockEntity(pos) instanceof NativeLegacyLandingEntity tile){var root=tile.physicalRoot()?tile:at(world,tile.ownerPos());if(root!=null&&root.kind()==kind&&(root==tile||root.ownHelper(tile)))NativeLegacyFootprints.remove(root);}super.onRemove(state,world,pos,next,moving);}
}
