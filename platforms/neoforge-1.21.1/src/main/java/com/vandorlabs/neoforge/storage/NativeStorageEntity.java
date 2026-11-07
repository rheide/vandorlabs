package com.vandorlabs.neoforge.storage;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.screen.NativeScreenOwnership;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Inactive independent 27-slot owner. Public/model projections contain no inventory or platform snapshot. */
public class NativeStorageEntity extends BlockEntity implements Container,MenuProvider {
 public static final String SCHEMA="NativeStorageSchema",RECOVERY="NativeStorageRecoverySnapshot",PLACEMENT_RECOVERY="NativeStoragePlacementRecovery",PREVIOUS="NativeStoragePreviousInventory";
 public record Visual(NativeStorageSettings settings,boolean inert,long revision,com.vandorlabs.ui.ProgrammableBlockDialog.State appearance){
  public Visual(NativeStorageSettings settings,boolean inert,long revision){this(settings,inert,revision,project(settings));}
  private static com.vandorlabs.ui.ProgrammableBlockDialog.State project(NativeStorageSettings settings){var h=settings.housing();return new com.vandorlabs.ui.ProgrammableBlockDialog.State(h.mainTexture,h.layout(),h.facesEnabled,h.faces(),-1);}
 }
 public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
 private NativeStorageSettings settings=NativeStorageSettings.fresh();private NonNullList<ItemStack> items=NativeStorageInventory.empty();private CompoundTag retained=new CompoundTag();private boolean inert,loading,unloading,forcedRecovery;private int comparator;private long revision,inventoryScans;private volatile Visual visual=new Visual(settings,false,0);private volatile ModelData model=ModelData.builder().with(VISUAL,visual).build();
 private final IItemHandlerModifiable handler=new InvWrapper(this){@Override public ItemStack getStackInSlot(int slot){return installed()?super.getStackInSlot(slot):ItemStack.EMPTY;}@Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return installed()?super.insertItem(slot,stack,simulate):stack;}@Override public ItemStack extractItem(int slot,int amount,boolean simulate){return installed()?super.extractItem(slot,amount,simulate):ItemStack.EMPTY;}@Override public void setStackInSlot(int slot,ItemStack stack){mutation();super.setStackInSlot(slot,stack);}};
 public NativeStorageEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
 public final NativeStorageSettings settings(){return settings;}public final Visual visual(){return visual;}public final boolean inert(){return inert;}public final boolean available(){return !inert&&!loading&&!unloading&&!isRemoved();}public final int comparator(){return inert?0:comparator;}public final long inventoryScans(){return inventoryScans;}public final IItemHandler itemHandler(){return handler;}
 @Override public ModelData getModelData(){return model;}
 public final boolean installed(){if(inert||loading||unloading||isRemoved()||!(level instanceof ServerLevel server)||!server.getServer().isSameThread())return false;var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this&&chunk.getBlockState(worldPosition)==getBlockState()&&getBlockState().getBlock() instanceof NativeStorageBlock&&!com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,worldPosition);}
 private void mutation(){if(inert||loading||unloading||isRemoved()||level!=null&&!installed())throw new IllegalStateException("Unavailable Storage inventory owner");}
 protected void published(Visual next){}
 private void publish(){visual=new Visual(settings,inert,++revision);model=ModelData.builder().with(VISUAL,visual).build();published(visual);if(level!=null&&level.isClientSide)requestModelDataUpdate();}
 private void signal(){inventoryScans++;comparator=inert?0:AbstractContainerMenu.getRedstoneSignalFromContainer(this);}
 @Override protected void loadAdditional(CompoundTag input,HolderLookup.Provider registries){
  forcedRecovery=false;if(level!=null&&level.isClientSide){settings=NativeStorageSettings.read(input);inert=input.getBoolean("NativeStorageInert");publish();return;}
  var original=input.copy();if(inert&&!retained.isEmpty()&&!input.contains("Items",Tag.TAG_LIST)){original=retained.copy();original.merge(input.copy());}var previous=items;loading=true;inert=true;
  try{NativePlatformComponents.requireSupported(original,registries);if(original.contains(RECOVERY)||original.contains(PLACEMENT_RECOVERY)||original.contains(PREVIOUS))throw new IllegalArgumentException("Storage recovery owner");if(original.contains(SCHEMA)&&(!original.contains(SCHEMA,Tag.TAG_INT)||original.getInt(SCHEMA)!=1))throw new IllegalArgumentException("Future Storage owner schema");if(original.contains("ForgeCaps")&&(!(original.get("ForgeCaps") instanceof CompoundTag caps)||!caps.isEmpty()))throw new IllegalArgumentException("Unsupported Storage block capabilities");if(!NativeScreenOwnership.installable(original))throw new IllegalArgumentException("Unsupported Storage owned module");var normalized=com.vandorlabs.neoforge.redstone.NativeTransmissionMigration.read(original).install();var next=NativeStorageSettings.read(normalized,settings);var staged=NativeStorageInventory.read(normalized,registries,previous);try(var platform=NativePlatformComponents.guardLoad(this)){super.loadAdditional(normalized.copy(),registries);platform.requireSuccessful();}settings=next;items=staged;retained=normalized.copy();for(String key:java.util.List.of("Items",NativeStorageInventory.SCHEMA,NativeStorageInventory.DATA_VERSION))retained.remove(key);NativePlatformComponents.forgetLoadedFields(retained);inert=false;
  }catch(RuntimeException failure){if(!input.contains("Items",Tag.TAG_LIST)&&previous.stream().anyMatch(stack->!stack.isEmpty())){var preserved=new CompoundTag();NativeStorageInventory.write(preserved,previous,registries);original.put(PREVIOUS,preserved);}retained=original;items=NativeStorageInventory.empty();settings=NativeStorageSettings.fresh();inert=true;}finally{loading=false;}signal();publish();
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);settings.write(tag);NativeStorageInventory.write(tag,items,registries);tag.putInt(SCHEMA,1);}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();var view=visual;view.settings.write(tag);tag.putBoolean("NativeStorageInert",view.inert);return tag;}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
 public final boolean configure(Player player,NativeStorageSettings next){if(next==null||!installed()||!NativeConfigurationAccess.canEdit(player,this))return false;var before=new CompoundTag();settings.write(before);var after=new CompoundTag();next.write(after);if(before.equals(after))return true;settings=next;publish();super.setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);return true;}
 public final CompoundTag copiedConfiguration(){if(!installed())throw new IllegalStateException("Unavailable Storage configuration owner");return NativeCopiedHousingMetadata.snapshot(settings.housing(),retained);}
 /** Typed copy commit. Its caller authenticates the held tool; no inventory or platform reload occurs. */
 public final boolean configureCopied(NativeStorageSettings next,com.vandorlabs.persistence.FrameSettings frames,com.vandorlabs.redstone.ChannelList channels,int range,boolean local,com.vandorlabs.redstone.ChannelList transmitted){
  if(next==null||!installed()||!NativeCopiedHousingMetadata.valid(frames,channels,range,transmitted))return false;
  final CompoundTag metadata;try{metadata=NativeCopiedHousingMetadata.edit(retained,frames,channels,range,local,transmitted);}catch(IllegalArgumentException unsupported){return false;}
  var before=new CompoundTag();settings.write(before);var after=new CompoundTag();next.write(after);if(before.equals(after)&&retained.equals(metadata))return true;
  settings=next;retained=metadata;publish();super.setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);return true;
 }
 @Override public int getContainerSize(){return NativeStorageSettings.SLOTS;}@Override public int getMaxStackSize(){return NativeStorageSettings.STACK_LIMIT;}
 @Override public boolean isEmpty(){if(inert||loading)return true;for(var stack:items)if(!stack.isEmpty())return false;return true;}
 @Override public ItemStack getItem(int slot){return inert||loading?ItemStack.EMPTY:items.get(slot);}
 @Override public ItemStack removeItem(int slot,int amount){mutation();var removed=ContainerHelper.removeItem(items,slot,amount);if(!removed.isEmpty())setChanged();return removed;}
 @Override public ItemStack removeItemNoUpdate(int slot){mutation();var removed=ContainerHelper.takeItem(items,slot);if(!removed.isEmpty())setChanged();return removed;}
 @Override public void setItem(int slot,ItemStack stack){mutation();items.set(slot,stack);if(!stack.isEmpty()&&stack.getCount()>Math.min(getMaxStackSize(),stack.getMaxStackSize()))stack.setCount(Math.min(getMaxStackSize(),stack.getMaxStackSize()));setChanged();}
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return !inert;}
 @Override public void clearContent(){mutation();items.clear();setChanged();}
 @Override public void setChanged(){if(loading)return;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Storage mutation off server thread");signal();super.setChanged();if(level instanceof ServerLevel&&com.vandorlabs.neoforge.placement.NativeStationaryPlacement.mutating(level,worldPosition))NativeStorageLifecycle.queue(this);else if(installed())NativeStorageLifecycle.comparators(this);}
 @Override public boolean stillValid(Player player){return installed()&&player.level()==level&&player.distanceToSqr(worldPosition.getCenter())<=64;}
 @Override public Component getDisplayName(){return Component.translatable("block.vandorlabs.programmable_storage");}
 @Override public AbstractContainerMenu createMenu(int id,Inventory player,Player actor){return stillValid(actor)?ChestMenu.threeRows(id,player,this):null;}
 @Override public void onLoad(){super.onLoad();unloading=false;NativeStorageLifecycle.queue(this);}
 @Override public void onChunkUnloaded(){unloading=true;NativeStorageLifecycle.retired(this);super.onChunkUnloaded();}
 @Override public void setRemoved(){unloading=true;NativeStorageLifecycle.retired(this);super.setRemoved();}
 final void droppedContents(){items.clear();comparator=0;super.setChanged();}
 final void forceRecovery(boolean value){forcedRecovery=value;}final boolean takeForcedRecovery(){boolean result=forcedRecovery;forcedRecovery=false;return result;}
}
