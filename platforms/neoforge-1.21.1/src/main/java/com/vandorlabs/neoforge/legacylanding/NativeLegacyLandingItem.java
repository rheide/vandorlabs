package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
/** One configured item remains in its slot until the mount and its owned empty reservations are installed. */
public final class NativeLegacyLandingItem extends BlockItem {
 public NativeLegacyLandingItem(NativeLegacyLandingBlock block,Item.Properties properties){super(block,properties);}
 static CompoundTag configuration(ItemStack stack){var result=new CompoundTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom!=null){var raw=custom.copyTag();if(raw.contains("LegacyLandingRecovery",Tag.TAG_COMPOUND))return raw;if(raw.contains("LegacyLandingSettings",Tag.TAG_COMPOUND))return raw;if(raw.contains("BlockEntityTag",Tag.TAG_COMPOUND))result.put("LegacyLandingSettings",raw.getCompound("BlockEntityTag").copy());}var legacy=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(legacy!=null)result.put("LegacyLandingSettings",legacy.copyTag());return result;}
 @Override public InteractionResult place(BlockPlaceContext context){var world=context.getLevel();var actor=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();if(actor==null||stack.isEmpty()||!context.canPlace()||!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,context.getClickedFace(),stack)||!NativeLegacyLandingBlock.loaded(world,pos)||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,pos))return InteractionResult.FAIL;var state=getPlacementState(context);if(state==null||world.getBlockEntity(pos)!=null)return InteractionResult.FAIL;var old=world.getBlockState(pos);var block=(NativeLegacyLandingBlock)getBlock();final CompoundTag choices=configuration(stack);final NativeLegacyLandingData.Configuration settings;try{settings=NativeLegacyLandingData.read(choices.getCompound("LegacyLandingSettings")).configuration();if(!NativeLegacyLandingData.configurable(block.kind,settings))return InteractionResult.FAIL;}catch(IllegalArgumentException malformed){return InteractionResult.FAIL;}
  for(var offset:com.vandorlabs.gear.core.LegacyLandingLayout.reserved(0,settings.size())){var at=pos.offset(offset.x,offset.y,offset.z);if(world.isOutsideBuildHeight(at)||!NativeLegacyLandingBlock.loaded(world,at)||!world.getBlockState(at).isAir()||world.getBlockEntity(at)!=null||!world.mayInteract(actor,at)||!actor.mayUseItemAt(at,context.getClickedFace(),stack)||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,at))return InteractionResult.FAIL;}
  var preview=new NativeLegacyShapes.Pose(block.kind,settings.size(),0,state.getValue(NativeLegacyLandingBlock.FACING));if(!world.isUnobstructed(actor,preview.cell(BlockPos.ZERO).move(pos.getX(),pos.getY(),pos.getZ())))return InteractionResult.FAIL;if(world.isClientSide)return InteractionResult.SUCCESS;if(!(world instanceof ServerLevel server)||!server.getServer().isSameThread())return InteractionResult.FAIL;
  final CompoundTag originalItem;try{originalItem=com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(stack.copyWithCount(1),world.registryAccess());}catch(IllegalArgumentException unsupported){return InteractionResult.FAIL;}
  final NativeLegacyFootprints.Plan[] reservation=new NativeLegacyFootprints.Plan[1];
  try{boolean committed=NativeStationaryPlacement.commit(world,pos,old,state,actor,stack,new NativeStationaryPlacement.Hooks<NativeLegacyLandingEntity>(){
   public boolean stage(){return world.setBlock(pos,state,18);}
   public NativeLegacyLandingEntity owner(){return world.getBlockEntity(pos) instanceof NativeLegacyLandingEntity tile?tile:null;}
   public void install(NativeLegacyLandingEntity tile){tile.installItem(choices);reservation[0]=NativeLegacyFootprints.prepare(tile,settings,0);reservation[0].install();}
   public boolean restore(){if(reservation[0]!=null)reservation[0].undo();return world.setBlock(pos,old,18)||world.getBlockState(pos)==old;}
   public NativeLegacyLandingEntity retainRecovery(){var tile=owner();if(tile==null)return null;var details=new CompoundTag();details.put("OriginalItem",originalItem.copy());tile.failRecovery(choices,details);return tile;}
   public String label(){return "Telescopic landing gear";}
  });if(!committed)return InteractionResult.FAIL;}catch(RuntimeException failed){return InteractionResult.FAIL;}
  var root=NativeLegacyLandingBlock.at(world,pos);if(root==null)return InteractionResult.FAIL;root.placed();world.markAndNotifyBlock(pos,world.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);world.updateNeighborsAt(pos,block);var sound=state.getSoundType(world,pos,actor);world.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);world.gameEvent(actor,GameEvent.BLOCK_PLACE,pos);return InteractionResult.CONSUME;
 }
}
