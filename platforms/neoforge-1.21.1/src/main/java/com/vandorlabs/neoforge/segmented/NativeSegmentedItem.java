package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import java.util.*;
/** Single stationary segment placement. Configured items carry choices, never world ownership. */
public class NativeSegmentedItem extends BlockItem {
 public static final String CONFIGURATION="BlockEntityTag",RECOVERY="SegmentedPlacementRecovery";
 private record Scope(Level level,BlockPos pos){}private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 public static boolean mutating(Level level,BlockPos pos){var scope=ACTIVE.get();return scope!=null&&scope.level==level&&scope.pos.equals(pos);}
 public NativeSegmentedItem(NativeSegmentedDoorBlock block,Properties properties){super(block,properties);}
 public static ItemStack pick(Item item,NativeSegmentedDoorEntity tile){ItemStack stack=new ItemStack(item);if(tile!=null){CompoundTag root=new CompoundTag();root.put(CONFIGURATION,tile.itemSettings());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;}
 /** Placement-only normalization; callers rendering items should cache by immutable component identity. */
 public record Choices(NativeSegmentedDoorSettings settings,ChannelList channels,int ticks,boolean local,boolean opens,int texture,boolean configured){
  void install(NativeSegmentedDoorEntity tile){tile.settings=settings;tile.channels=channels;tile.ticksPerSection=ticks;tile.localRedstone=local;tile.signalOpens=opens;tile.texture=texture;tile.configured=configured;tile.publishLocal();tile.scheduleRefresh();}
 }
 private static final Set<String> KEYS=Set.of("SegmentedSettings","SegmentedPosition","SegmentedSlide","SegmentedStraight","SegmentedLayout","SegmentedWindow","SegmentedShape","SegmentedJoin","SegmentedGlassMaterial","SegmentedGlassShade","SegmentedGlassTint","SegmentedGlassLayout","SegmentedTexture","SegmentedSpeed","SegmentedLocalRedstone","SegmentedSignalOpens","SegmentedConfigured","RedstoneChannels");
 public static Choices choices(ItemStack stack){CompoundTag raw=null;var nativeData=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(nativeData!=null)raw=nativeData.copyTag();else{var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom!=null){var root=custom.copyTag();if(root.contains(CONFIGURATION,Tag.TAG_COMPOUND))raw=root.getCompound(CONFIGURATION);}}return choices(raw);}
 public static Choices choices(CompoundTag raw){
  CompoundTag merged=new CompoundTag();NativeSegmentedDoorSettings.DEFAULT.write(merged);merged.putInt("SegmentedSpeed",10);merged.putBoolean("SegmentedLocalRedstone",true);merged.putBoolean("SegmentedSignalOpens",true);merged.putBoolean("SegmentedConfigured",false);merged.putIntArray("RedstoneChannels",new int[0]);
  if(raw!=null){CompoundTag selected=new CompoundTag();for(String key:KEYS)if(raw.contains(key))selected.put(key,raw.get(key).copy());merged.merge(selected);}
  ChannelList channels=ChannelList.EMPTY;if(merged.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(merged.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){channels=ChannelList.EMPTY;}
  int texture=merged.contains("SegmentedTexture",Tag.TAG_INT)&&NativeSegmentedDoorEntity.validTexture(merged.getInt("SegmentedTexture"))?merged.getInt("SegmentedTexture"):-1;
  return new Choices(NativeSegmentedDoorSettings.read(merged),channels,Math.max(5,Math.min(30,merged.getInt("SegmentedSpeed"))),merged.getBoolean("SegmentedLocalRedstone"),merged.getBoolean("SegmentedSignalOpens"),texture,merged.getBoolean("SegmentedConfigured"));
 }
 protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level level,BlockPos pos,BlockState old){return level.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
 private static void transfer(net.minecraft.world.entity.player.Player player,ItemStack stack){if(!player.getAbilities().instabuild)stack.shrink(1);}
 private static NativeSegmentedDoorEntity recovery(Level level,BlockPos pos,NativeSegmentedDoorBlock block,BlockState state,Choices choices){
  if(level.getBlockState(pos)!=state)return null;var raw=level.getBlockEntity(pos);NativeSegmentedDoorEntity owner=raw instanceof NativeSegmentedDoorEntity tile?tile:null;
  if(owner==null){owner=new NativeSegmentedDoorEntity(block.entityType(),pos,state);level.setBlockEntity(owner);if(level.getBlockEntity(pos)!=owner)return null;}
  choices.install(owner);CompoundTag retained=owner.saveWithoutMetadata(level.registryAccess());retained.putBoolean(RECOVERY,true);owner.retained=retained;owner.inert=true;owner.publishLocal();owner.setChanged();return owner;
 }
 @Override public InteractionResult place(BlockPlaceContext context){
  Level level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var block=(NativeSegmentedDoorBlock)getBlock();BlockPos pos=context.getClickedPos();
  if(player==null||stack.isEmpty()||!context.canPlace()||!level.hasChunkAt(pos)||pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
  if(level instanceof net.minecraft.server.level.ServerLevel server&&!NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
  BlockState old=level.getBlockState(pos);if(old.hasBlockEntity()||level.getBlockEntity(pos)!=null||!old.canBeReplaced(context)||!block.canAdd(level,pos))return InteractionResult.FAIL;
  BlockState state=block.getStateForPlacement(context);if(state==null||!canPlace(context,state))return InteractionResult.FAIL;Choices choices=choices(stack);if(level.isClientSide)return InteractionResult.SUCCESS;
  ItemStack placedStack=stack.copy();Scope prior=ACTIVE.get();ACTIVE.set(new Scope(level,pos));NativeSegmentedDoorEntity owner=null;boolean committed=false,owned=false;Throwable original=null;
  try{
   if(!stage(level,pos,state)||level.getBlockState(pos)!=state||!(level.getBlockEntity(pos) instanceof NativeSegmentedDoorEntity tile))return InteractionResult.FAIL;owner=tile;choices.install(owner);owner.capturePlacementFluids(old);owner.setChanged();transfer(player,stack);owned=committed=true;
  }catch(RuntimeException|Error failure){original=failure;throw failure;}finally{
   Throwable rollback=null;try{if(!committed&&level.getBlockState(pos)==state&&(owner==null||level.getBlockEntity(pos)==owner)){
    try{restore(level,pos,old);if(level.getBlockState(pos)!=old)throw new IllegalStateException("Refused Segmented placement rollback");}catch(RuntimeException|Error failed){rollback=failed;}
    if(level.getBlockState(pos)==state){try{var retained=recovery(level,pos,block,state,choices);if(retained!=null){if(!owned){transfer(player,stack);owned=true;}level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}}catch(RuntimeException|Error failed){if(rollback==null)rollback=failed;else rollback.addSuppressed(failed);}}
   }}finally{if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}
   if(rollback!=null){if(original!=null)original.addSuppressed(rollback);else throw new IllegalStateException("Segmented placement rollback failed; native owner retained",rollback);}
  }
  if(!committed)return InteractionResult.FAIL;
  // All settings and owned fluid slots exist before vanilla observers can see the new segment.
  level.markAndNotifyBlock(pos,level.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(level,pos,state,player,placedStack);var sound=state.getSoundType(level,pos,player);level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,pos);if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placedStack);return InteractionResult.CONSUME;
 }
}
