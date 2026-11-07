package com.vandorlabs.neoforge.canopy;

import com.mojang.logging.LogUtils;
import com.vandorlabs.canopy.CanopyPlacementPolicy;
import com.vandorlabs.neoforge.placement.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Specialized standalone item: original-cell hits and complete owner installation precede consumption. */
public class NativeCanopyItem extends BlockItem {
    private record Scope(Level level,Set<BlockPos> cells){}
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    public static boolean mutating(Level level,BlockPos pos){var scope=ACTIVE.get();return scope!=null&&scope.level()==level&&scope.cells().contains(pos);}
    public NativeCanopyItem(NativeCanopyBlock block,Properties properties){super(block,properties);}
    public static CompoundTag configuration(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data==null?null:data.copyTag();}
    public static CompoundTag genericConfiguration(ItemStack stack){var nativeData=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(nativeData!=null)return nativeData.copyTag();var outer=configuration(stack);return outer!=null&&outer.contains("BlockEntityTag",Tag.TAG_COMPOUND)?outer.getCompound("BlockEntityTag").copy():null;}
    private static boolean loaded(Level level,BlockPos pos){return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    private static final class Refused extends RuntimeException {Refused(String message){super(message);}}
    protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restore(Level level,BlockPos pos,BlockState old){return level.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
    protected NativeCanopyEntity ownerAt(Level level,BlockPos pos){return NativeCanopyLifecycle.at(level,pos);}
    private static boolean available(BlockPlaceContext context,BlockPos pos){var level=context.getLevel();var player=context.getPlayer();if(!loaded(level,pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return false;var state=level.getBlockState(pos);return !state.hasBlockEntity()&&level.getBlockEntity(pos)==null&&state.canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace()));}
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer()==null||!loaded(context.getLevel(),context.getClickedPos()))return InteractionResult.FAIL;return perform(new BlockPlaceContext(context),context.getClickedPos());}
    @Override public InteractionResult place(BlockPlaceContext context){var original=context.replacingClickedOnBlock()?context.getClickedPos():context.getClickedPos().relative(context.getClickedFace().getOpposite());return perform(context,original);}
    protected NativeCanopyEntity recover(Level level,BlockPos pos,NativeCanopyBlock block,NativeCanopyPlacement.Prepared prepared,CompoundTag originalItem){
        if(level.getBlockState(pos)!=prepared.state())return null;var host=NativeCanopyLifecycle.at(level,pos);if(host==null){host=(NativeCanopyEntity)block.newBlockEntity(pos,prepared.state());level.setBlockEntity(host);if(NativeCanopyLifecycle.at(level,pos)!=host)return null;}host.retainPlacementRecovery(prepared,originalItem);host.setChanged();return host;
    }
    public static ItemStack pick(Item item,NativeCanopyEntity host){if(host==null||!host.initialized())return ItemStack.EMPTY;if(host.inert()){var recovered=host.placementRecoveryItem();if(!recovered.isEmpty()||host.recoverySnapshot().contains(NativeCanopyEntity.PLACEMENT_ITEM))return recovered;var raw=host.recoverySnapshot();if(raw.isEmpty())return ItemStack.EMPTY;var stack=new ItemStack(item);var tag=new CompoundTag();tag.put(NativeCanopyEntity.RECOVERY,raw);stack.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return stack;}var stack=new ItemStack(item);var tag=new CompoundTag();host.settings().write(tag);stack.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return stack;}
    private InteractionResult perform(BlockPlaceContext context,BlockPos original){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var block=(NativeCanopyBlock)getBlock();if(player==null||stack.isEmpty()||!loaded(level,original))return InteractionResult.FAIL;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Canopy placement requires owning server thread");
        var side=context.getClickedFace();var hit=NativePlacementHit.hit(context);var cell=original;var clicked=NativeCanopyLifecycle.at(level,original);NativeCanopyEntity inherited=null;
        if(clicked!=null){var resolved=CanopyPlacementPolicy.existingCell(original.getX(),original.getY(),original.getZ(),side.get3DDataValue(),(float)hit.x,(float)hit.y,(float)hit.z);cell=new BlockPos(resolved.x,resolved.y,resolved.z);inherited=NativeCanopyLifecycle.owner(clicked);}if(!loaded(level,cell))return InteractionResult.FAIL;
        boolean replaceable=level.getBlockState(cell).canBeReplaced(BlockPlaceContext.at(context,cell,side));var size=NativeCanopyModels.builtin().dimensions(block.kind());var plan=inherited==null?CanopyPlacementPolicy.initial(block.kind(),size.width(),size.length(),size.height(),player.getDirection().get3DDataValue(),side.get3DDataValue(),replaceable):CanopyPlacementPolicy.existing(block.kind(),size.width(),size.length(),size.height(),inherited.facing().get3DDataValue(),inherited.settings().data().mount,side.get3DDataValue(),replaceable);
        final BlockPos root=cell.offset(plan.x,plan.y,plan.z).immutable();final NativeCanopyPlacement.Prepared prepared;try{prepared=NativeCanopyPlacement.prepare(block,root,block.defaultBlockState().setValue(NativeCanopyBlock.FACING,Direction.from3DDataValue(plan.facing)),genericConfiguration(stack),configuration(stack),plan.mount,level.registryAccess());}catch(IllegalArgumentException unsupported){return InteractionResult.FAIL;}
        if(com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(stack))return InteractionResult.FAIL;for(var pos:prepared.admission())if(!available(context,pos))return InteractionResult.FAIL;if(level.isClientSide)return InteractionResult.SUCCESS;
        final CompoundTag originalItem;try{var one=stack.copyWithCount(1);originalItem=com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(one,level.registryAccess());var restored=com.vandorlabs.neoforge.inventory.NativeInventoryItems.modern(originalItem,level.registryAccess());if(restored.getCount()!=1||!ItemStack.isSameItemSameComponents(one,restored))return InteractionResult.FAIL;}catch(RuntimeException unsupported){return InteractionResult.FAIL;}
        var before=new LinkedHashMap<BlockPos,BlockState>();before.put(root,level.getBlockState(root));for(var pos:prepared.cells())before.putIfAbsent(pos,level.getBlockState(pos));var added=new ArrayList<BlockPos>();var placed=stack.copy();boolean committed;var prior=ACTIVE.get();ACTIVE.set(new Scope(level,Set.copyOf(before.keySet())));
        try{committed=NativeStationaryPlacement.commit(level,root,before.get(root),prepared.state(),player,stack,new NativeStationaryPlacement.Hooks<NativeCanopyEntity>(){
            public boolean stage(){return NativeCanopyItem.this.stage(level,root,prepared.state());}
            public NativeCanopyEntity owner(){return ownerAt(level,root);}
            public void install(NativeCanopyEntity owner){if(!owner.kind().equals(block.kind()))throw new Refused("Canopy owner factory mismatch");owner.installItem(prepared);for(var pos:new LinkedHashSet<>(prepared.cells()))if(!pos.equals(root)){
                boolean written=NativeCanopyItem.this.stage(level,pos,prepared.state());if(level.getBlockState(pos)==prepared.state())added.add(pos);if(!written||level.getBlockState(pos)!=prepared.state())throw new Refused("Canopy helper placement refused");var helper=ownerAt(level,pos);if(helper==null||!helper.kind().equals(block.kind()))throw new Refused("Canopy helper factory missing");helper.helperOf(owner);
            }}
            public boolean restore(){boolean clean=true;for(var pos:added){var helper=NativeCanopyLifecycle.at(level,pos);if(level.getBlockState(pos)==prepared.state()&&(helper==null||root.equals(helper.links().anchor())||helper.links().anchor()==null&&!helper.links().savedFootprint())){if(helper!=null)helper.removing(true);NativeCanopyItem.this.restore(level,pos,before.get(pos));if(level.getBlockState(pos)==prepared.state())clean=false;}}if(!clean)throw new IllegalStateException("Canopy helper rollback refused; root retained");return NativeCanopyItem.this.restore(level,root,before.get(root));}
            public NativeCanopyEntity retainRecovery(){return recover(level,root,block,prepared,originalItem);}
            public String label(){return "Canopy";}
        });}catch(Refused refused){if(refused.getSuppressed().length>0)LogUtils.getLogger().warn("Canopy placement retained a recovery owner after rollback refusal",refused);return InteractionResult.FAIL;}finally{if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}
        if(!committed)return InteractionResult.FAIL;var owner=NativeCanopyLifecycle.at(level,root);NativeCanopyLifecycle.tryPair(owner);for(var pos:before.keySet()){var member=NativeCanopyLifecycle.at(level,pos);if(member!=null){NativeCanopyLifecycle.prepare(member);member.setChanged();}level.markAndNotifyBlock(pos,level.getChunkAt(pos),before.get(pos),level.getBlockState(pos),Block.UPDATE_CLIENTS,512);}level.updateNeighborsAt(root,block);
        var sound=prepared.state().getSoundType(level,root,player);level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,root);if(player instanceof net.minecraft.server.level.ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,root,placed);return InteractionResult.CONSUME;
    }
}
