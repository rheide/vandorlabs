package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.programmer.ProgrammerStatus;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Private unregistered root host. Platform load, inventory adoption and safe publication are separate. */
public class NativeProgrammerEntity extends BlockEntity implements Container {
 public record SlotVisual(int kind,int company){}
 public record Visual(Direction facing,ProgrammerStatus status,boolean inert,List<SlotVisual> slots,long sequence){public Visual{slots=List.copyOf(slots);}}
 private static final AtomicLong SEQUENCES=new AtomicLong();
 private final NativeProgrammerService service;
 private CompoundTag original=new CompoundTag(),retained=new CompoundTag();
 private boolean inert,projectionOnly,removing,pendingLoad;
 private volatile Visual visual;
 public NativeProgrammerEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);service=new NativeProgrammerService(new NativeProgrammerService.Host(){public boolean current(){return NativeProgrammerEntity.this.current();}public boolean removing(){return removalAccess();}public void changed(boolean full){publish();}});visual=emptyVisual(false,0);}
 private Direction facing(){return getBlockState().hasProperty(NativeProgrammerBlock.FACING)?getBlockState().getValue(NativeProgrammerBlock.FACING):Direction.NORTH;}
 private Visual emptyVisual(boolean failed,long sequence){return new Visual(facing(),failed?ProgrammerStatus.BLANK:ProgrammerStatus.READY,failed,List.of(new SlotVisual(0,-1),new SlotVisual(0,-1),new SlotVisual(0,-1)),sequence);}
 private boolean serverThread(){return level instanceof ServerLevel server&&server.getServer().isSameThread();}
 private boolean current(){return serverThread()&&!NativeProgrammerItem.mutating(level,worldPosition)&&!inert&&!projectionOnly&&!removing&&!isRemoved()&&NativeRedstoneChannels.open((ServerLevel)level)&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&getBlockState().getBlock() instanceof NativeProgrammerBlock&&getBlockState().getValue(NativeProgrammerBlock.PART)==0;}
 private boolean removalAccess(){return serverThread()&&removing&&!isRemoved();}
 public boolean inert(){return inert||projectionOnly&&level instanceof ServerLevel;}
 public boolean usable(Player player){return current()&&NativeConfigurationAccess.canEditDialog(player,level,worldPosition);}
 public Visual visual(){return visual;}
 public NativeProgrammerDraft draft(){return service.draft();}
 public ProgrammerStatus status(){return service.status();}
 public int writing(){return service.writing();}
 public int complete(){return service.complete();}
 public String problem(){return current()?service.problem():"Programmer is unavailable";}
 public String copyProblem(){return current()?service.copyProblem():"Programmer is unavailable";}
 public boolean configure(ServerPlayer player,NativeProgrammerDraft draft){return usable(player)&&service.configure(draft);}
 public boolean program(ServerPlayer player){return usable(player)&&service.program();}
 public boolean copy(ServerPlayer player){return usable(player)&&service.copy();}
 public boolean socket(ServerPlayer player,InteractionHand hand,int slot){if(!usable(player))return false;boolean result=service.socket(slot,player.getItemInHand(hand),stack->player.setItemInHand(hand,stack));if(result)player.getInventory().setChanged();return result;}
 @Override public void onLoad(){super.onLoad();pendingLoad=true;if(projectionOnly&&level instanceof ServerLevel){inert=true;visual=emptyVisual(true,SEQUENCES.incrementAndGet());}}
 public void tick(){if(!serverThread())return;if(pendingLoad){pendingLoad=false;if(current())publish();}if(current())service.tick();}
 private void rebuild(){if(inert||projectionOnly){visual=emptyVisual(true,SEQUENCES.incrementAndGet());return;}var slots=new ArrayList<SlotVisual>(3);for(int slot=0;slot<3;slot++){var item=service.localItem(slot);slots.add(new SlotVisual(NativeProgrammerRules.kind(item),item.isEmpty()?-1:NativeCredentialItems.company(item)));}visual=new Visual(facing(),service.status(),false,slots,SEQUENCES.incrementAndGet());}
 private void publish(){if(!current())return;rebuild();super.setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
 @Override public void setChanged(){if(!current())return;super.setChanged();rebuild();}
 @Override protected void loadAdditional(CompoundTag raw,HolderLookup.Provider registries){
  if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Programmer live load requires server thread");
  var incoming=raw.copy();if(incoming.getBoolean("ProgrammerProjection")){readProjection(incoming);return;}original=incoming;inert=true;projectionOnly=false;service.reset();
  try{if(!(getBlockState().getBlock() instanceof NativeProgrammerBlock)||getBlockState().getValue(NativeProgrammerBlock.PART)!=0)throw new IllegalArgumentException("Programmer inventory requires controller part");var prepared=NativeProgrammerSnapshot.prepare(incoming,registries);try(var scope=NativePlatformComponents.guardLoad(this)){super.loadAdditional(incoming,registries);scope.requireSuccessful();}retained=incoming.copy();NativePlatformComponents.forgetLoadedFields(retained);service.install(prepared);inert=false;rebuild();}
  catch(RuntimeException failure){inert=true;retained=new CompoundTag();service.reset();rebuild();}
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){if(inert||projectionOnly){NativePlatformComponents.clearForInertSave(this);tag.merge(original.copy());return;}super.saveAdditional(tag,registries);tag.merge(retained.copy());service.writeKnownInto(tag,registries);}
 private void readProjection(CompoundTag tag){var kinds=tag.getIntArray("Kinds");var companies=tag.getIntArray("Companies");int status=tag.getInt("Status");long sequence=tag.getLong("Sequence");if(sequence<visual.sequence())return;try{if(tag.getInt("ProgrammerProjectionVersion")!=1||kinds.length!=3||companies.length!=3||status<0||status>=ProgrammerStatus.values().length)throw new IllegalArgumentException("Programmer projection bounds");var slots=new ArrayList<SlotVisual>(3);boolean failed=tag.getBoolean("Inert")||level instanceof ServerLevel;for(int slot=0;slot<3;slot++){if(kinds[slot]<0||kinds[slot]>2||kinds[slot]==0&&companies[slot]!=-1||kinds[slot]!=0&&(companies[slot]<0||companies[slot]>6)||failed&&kinds[slot]!=0)throw new IllegalArgumentException("Programmer projection credential");slots.add(new SlotVisual(kinds[slot],companies[slot]));}service.reset();projectionOnly=true;inert=failed;original=tag.copy();visual=new Visual(facing(),ProgrammerStatus.values()[status],failed,slots,sequence);}catch(RuntimeException failure){projectionOnly=true;inert=true;original=tag.copy();service.reset();visual=emptyVisual(true,sequence);}}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var current=visual;var tag=new CompoundTag();tag.putBoolean("ProgrammerProjection",true);tag.putInt("ProgrammerProjectionVersion",1);tag.putBoolean("Inert",current.inert());tag.putInt("Status",current.status().ordinal());tag.putLong("Sequence",current.sequence());var kinds=new int[3];var companies=new int[3];for(int slot=0;slot<3;slot++){kinds[slot]=current.slots().get(slot).kind();companies[slot]=current.slots().get(slot).company();}tag.putIntArray("Kinds",kinds);tag.putIntArray("Companies",companies);return tag;}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
 @Override public void saveToItem(ItemStack stack,HolderLookup.Provider registries){stack.remove(DataComponents.BLOCK_ENTITY_DATA);}
 void beginRemoval(){if(serverThread())removing=true;}
 List<ItemStack> takeForDrop(){return service.takeForDrop();}
 CompoundTag recovery(){return removalAccess()&&(inert||projectionOnly)&&!original.isEmpty()?original.copy():null;}
 @Override public int getContainerSize(){return 3;}
 @Override public int getMaxStackSize(){return 1;}
 @Override public boolean isEmpty(){for(int slot=0;slot<3;slot++)if(!getItem(slot).isEmpty())return false;return true;}
 @Override public ItemStack getItem(int slot){return service.item(slot);}
 @Override public ItemStack removeItem(int slot,int count){return service.remove(slot,count);}
 @Override public ItemStack removeItemNoUpdate(int slot){return service.removeWhole(slot);}
 @Override public void setItem(int slot,ItemStack stack){service.set(slot,stack);}
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return current()&&service.valid(slot,stack);}
 @Override public void clearContent(){service.clear();}
 @Override public boolean stillValid(Player player){return usable(player);}
}
