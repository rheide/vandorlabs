package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;

/** Specialized configured placement; choices transfer, saved world links and fluid ownership do not. */
public class NativeTrapdoorItem extends BlockItem {
 public static final String CONFIGURATION="BlockEntityTag",RECOVERY="TrapdoorPlacementRecovery";
 public NativeTrapdoorItem(NativeTrapdoorBlock block,Properties properties){super(block,properties);}
 public static boolean mutating(Level level,BlockPos pos){return NativeStationaryPlacement.mutating(level,pos);}
 /** Defensive raw configuration for resource-generation item models and placement normalization. */
 public static CompoundTag configuration(ItemStack stack){var nativeData=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(nativeData!=null)return nativeData.copyTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;}
 public static int housingTexture(ItemStack stack){var raw=configuration(stack);return raw!=null&&raw.contains("housingTexture",Tag.TAG_INT)?com.vandorlabs.neoforge.ModernFinishCatalog.storedChoice(raw.getInt("housingTexture")):NativeTrapdoorSettings.defaultTexture();}
 public static ItemStack pick(Item item,NativeTrapdoorEntity tile){ItemStack stack=new ItemStack(item);if(tile!=null){CompoundTag root=new CompoundTag();root.put(CONFIGURATION,tile.itemSettings());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;}
 private static final Set<String> KEYS=Set.of("housingTexture","TrapdoorPosition","TrapdoorCoverFacing","TrapdoorTileTexture","TrapdoorTextureLayout","TrapdoorCover","TrapdoorSliding","TrapdoorSlideIntoWall","TrapdoorSlideOverSurface","TrapdoorTrigger","RedstoneChannel","RedstoneChannels","DiagonalHingeSide","DiagonalRotationFlipped","DiagonalHalfHeight","DiagonalFullWidth");
 public record Choices(NativeTrapdoorSettings settings,Direction explicitFacing){}
 public static Choices choices(CompoundTag raw,boolean diagonal,int position){return choices(raw,diagonal,position,Direction.NORTH);}
 public static Choices choices(CompoundTag raw,boolean diagonal,int position,Direction freshFacing){
  var defaults=NativeTrapdoorSettings.read(new CompoundTag(),diagonal);var fresh=new NativeTrapdoorSettings(defaults.choices(),defaults.channels(),freshFacing.get2DDataValue(),0,false,diagonal).item(true);
  return choices(raw,diagonal,position,fresh);
 }
 public static Choices choices(CompoundTag raw,boolean diagonal,int position,CompoundTag freshBase){
  var fresh=freshBase.copy();if(raw!=null){var selected=new CompoundTag();for(String key:KEYS)if(raw.contains(key))selected.put(key,raw.get(key).copy());fresh.merge(selected);}else fresh.putInt("TrapdoorPosition",position);
  if(diagonal)fresh.putInt("TrapdoorPosition",position);
  var normalized=NativeTrapdoorSettings.read(fresh,diagonal);return new Choices(normalized,raw!=null&&raw.contains("TrapdoorCoverFacing",Tag.TAG_INT)?Direction.from2DDataValue(normalized.facing()):null);
 }
 protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
 protected boolean restore(Level level,BlockPos pos,BlockState old){return level.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
 private static NativeTrapdoorEntity recovery(Level level,BlockPos pos,NativeTrapdoorBlock block,BlockState state,Choices choices){if(level.getBlockState(pos)!=state)return null;var raw=level.getBlockEntity(pos);var owner=raw instanceof NativeTrapdoorEntity tile?tile:null;if(owner==null){owner=new NativeTrapdoorEntity(block.entityType(),pos,state);level.setBlockEntity(owner);if(level.getBlockEntity(pos)!=owner)return null;}owner.installItemChoices(choices.settings());owner.retainPlacementRecovery();owner.setChanged();return owner;}
 @Override public InteractionResult place(BlockPlaceContext context){
  var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var block=(NativeTrapdoorBlock)getBlock();var pos=context.getClickedPos();
  if(player==null||stack.isEmpty()||!context.canPlace()||!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
  if(level instanceof net.minecraft.server.level.ServerLevel server&&!NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
  var old=level.getBlockState(pos);if(old.hasBlockEntity()||level.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
  var initial=block.getStateForPlacement(context);if(initial==null)return InteractionResult.FAIL;var raw=configuration(stack);var hit=NativeTrapdoorPlacement.hit(context);int position=block.diagonal()?NativeTrapdoorPlacement.contextMode(context,raw):NativeTrapdoorPlacement.regularPosition(context.getClickedFace(),(float)hit.y);var choices=choices(raw,block.diagonal(),position,initial.getValue(NativeTrapdoorBlock.FACING));
  var prepared=initial;if(!block.diagonal()){prepared=prepared.setValue(NativeTrapdoorBlock.HALF,choices.settings().choices().position==2?Half.TOP:Half.BOTTOM);if(choices.explicitFacing()!=null)prepared=prepared.setValue(NativeTrapdoorBlock.FACING,choices.explicitFacing());if(choices.settings().choices().cover)prepared=prepared.setValue(NativeTrapdoorBlock.OPEN,true);}final BlockState state=prepared;
  if(!canPlace(context,state))return InteractionResult.FAIL;if(level.isClientSide)return InteractionResult.SUCCESS;
  var inherited=NativeTrapdoorFormation.captureInheritance(context,raw);var placedStack=stack.copy();boolean committed=NativeStationaryPlacement.commit(level,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativeTrapdoorEntity>(){
   public boolean stage(){return NativeTrapdoorItem.this.stage(level,pos,state);}public boolean restore(){return NativeTrapdoorItem.this.restore(level,pos,old);}public NativeTrapdoorEntity owner(){return level.getBlockEntity(pos) instanceof NativeTrapdoorEntity tile?tile:null;}
   public void install(NativeTrapdoorEntity owner){owner.installItemChoices(choices.settings());owner.capturePlacementFluids(old);}public NativeTrapdoorEntity retainRecovery(){return recovery(level,pos,block,state,choices);}public String label(){return "Trapdoor";}
  });if(!committed)return InteractionResult.FAIL;
  level.markAndNotifyBlock(pos,level.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(level,pos,state,player,placedStack);var owner=NativeTrapdoorEntity.at(level,pos);if(owner!=null)NativeTrapdoorFormation.inherit(owner,inherited);
  var sound=state.getSoundType(level,pos,player);level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,pos);if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placedStack);return InteractionResult.CONSUME;
 }
}
