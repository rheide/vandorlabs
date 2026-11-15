package com.vandorlabs.neoforge.rack;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
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
import java.util.List;

/** Static chunk-rendered shell; only changed modules have temporary client animation. */
public final class NativeRackBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    private static final VoxelShape[] SUPPORT=new VoxelShape[6];
    static {for(var facing:Direction.values()){VoxelShape shell=Shapes.empty();for(var side:Direction.values())if(ModuleRackGeometry.hasClosedBoundary(facing.get3DDataValue(),side.get3DDataValue()))shell=Shapes.or(shell,switch(side){case DOWN->Block.box(0,0,0,16,1,16);case UP->Block.box(0,15,0,16,16,16);case NORTH->Block.box(0,0,0,16,16,1);case SOUTH->Block.box(0,0,15,16,16,16);case WEST->Block.box(0,0,0,1,16,16);case EAST->Block.box(15,0,0,16,16,16);});SUPPORT[facing.get3DDataValue()]=shell.optimize();}}
    public NativeRackBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,com.vandorlabs.neoforge.NativeDirectionalPlacement.towardView(context));}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return SUPPORT[state.getValue(FACING).get3DDataValue()];}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeRackEntity(NativeRackBindings.ENTITY,pos,state);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeRackEntity owner)owner.neighborChanged();}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof NativeRackEntity owner&&owner.transmission().local()?owner.output.localSignalLevel(0):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    private int slot(BlockState state,BlockPos pos,BlockHitResult hit){var point=hit.getLocation();return ModuleRackGeometry.hitSlot(state.getValue(FACING).get3DDataValue(),hit.getDirection().get3DDataValue(),point.x-pos.getX(),point.y-pos.getY(),point.z-pos.getZ());}
    private static boolean accepts(ItemStack stack){try{if(NativeRackData.kind(stack)==0||com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(stack))return false;if(NativeRackData.kind(stack)==2)NativeRackData.settings(stack);return true;}catch(RuntimeException invalid){return false;}}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!NativeConfigurationAccess.canEditDialog(player,level,pos,hit.getDirection())||!(level.getBlockEntity(pos) instanceof NativeRackEntity owner)||owner.inert())return ItemInteractionResult.FAIL;
        if(hand==InteractionHand.MAIN_HAND&&stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeRackBindings.open(player,level,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;
        int slot=slot(state,pos,hit);if(slot<0||!accepts(stack))return stack.isEmpty()?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if(!level.isClientSide&&owner.insert(slot,stack,player.isCreative())){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();level.playSound(null,pos,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.25F,1.3F);}return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!NativeConfigurationAccess.canEditDialog(player,level,pos,hit.getDirection())||!(level.getBlockEntity(pos) instanceof NativeRackEntity owner)||owner.inert())return InteractionResult.FAIL;
        int slot=slot(state,pos,hit);if(slot<0)return NativeRackBindings.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;
        if(owner.kind(slot)==0&&accepts(player.getOffhandItem()))return InteractionResult.PASS;
        if(!level.isClientSide&&owner.kind(slot)!=0){
            var preview=owner.module(slot);if(!room(player,preview)){player.displayClientMessage(Component.translatable("message.vandorlabs.module_rack.inventory_full"),true);return InteractionResult.CONSUME;}
            var removed=owner.extract(slot);if(!removed.isEmpty()){
                player.getInventory().add(removed);
                if(!removed.isEmpty()&&!owner.insert(slot,removed,false))player.drop(removed,false);
                player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();level.playSound(null,pos,SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.BLOCKS,.25F,1.3F);
            }
        }return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static boolean room(Player player,ItemStack module){for(var stack:player.getInventory().items)if(stack.isEmpty()||ItemStack.isSameItemSameComponents(stack,module)&&stack.getCount()<Math.min(stack.getMaxStackSize(),player.getInventory().getMaxStackSize()))return true;for(var stack:player.getInventory().offhand)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,module)&&stack.getCount()<stack.getMaxStackSize())return true;return false;}
    private ItemStack shell(BlockEntity raw){var result=new ItemStack(asItem());if(raw instanceof NativeRackEntity owner&&owner.inert())result.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(owner.saveWithId(owner.getLevel().registryAccess())));return result;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){return shell(level.getBlockEntity(pos));}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){return List.of(shell(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeRackEntity owner)owner.forceRecovery(owner.inert()&&(player.isCreative()||!state.canHarvestBlock(level,pos,player)));return super.playerWillDestroy(level,pos,state,player);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&!level.isClientSide&&!com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,pos)&&level.getBlockEntity(pos) instanceof NativeRackEntity owner){if(owner.inert()){if(owner.takeRecovery())Block.popResource(level,pos,shell(owner));}else if(!owner.transferred())for(var module:owner.drain())Block.popResource(level,pos,module);}
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
