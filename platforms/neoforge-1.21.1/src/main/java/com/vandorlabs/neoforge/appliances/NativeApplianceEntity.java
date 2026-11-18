package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.appliance.ApplianceAutomation;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.redstone.ChannelList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.screen.NativeEmbeddedChipReader;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Physical appliance ownership, with bounded public projections and inert failed-load retention. */
public final class NativeApplianceEntity extends BlockEntity implements Container,NativeChannelMember {
 private NativeEmbeddedChipReader reader;
 private ItemStack ink=ItemStack.EMPTY,paper=ItemStack.EMPTY,frame=ItemStack.EMPTY;
 private ItemStack output=ItemStack.EMPTY;
 private final IItemHandler[] ports=new IItemHandler[6];
 private int activation,channelLevel;
 private ChannelList channels=ChannelList.EMPTY;
 private boolean ready,signalDirty=true,signalKnown,lastSignal,exporting;
 private long nextExport;
 private boolean inert,projectionOnly;private CompoundTag publicOriginal;private CompoundTag retained=new CompoundTag();private int revision;private long buttonSequence,pressedUntil,lastAction=-100;
 private NativeTokenReader.Visual chipVisual=new NativeTokenReader.Visual(true,0,-1,false,0,false);
 private NativeTokenReader.Motion chipMotion=new NativeTokenReader.Motion(0,20,false,0);
 public NativeApplianceEntity(BlockPos pos,BlockState state){super(NativeApplianceBindings.ENTITY,pos,state);reader=newReader();}
 private NativeEmbeddedChipReader newReader(){return new NativeEmbeddedChipReader(new NativeTokenReader.Host(){public BlockEntity owner(){return NativeApplianceEntity.this;}public boolean usable(ServerPlayer p){return NativeApplianceEntity.this.usable(p);}public void changed(){NativeApplianceEntity.this.changed();}public void schedule(int ticks){}public boolean chip(boolean saved){return true;}});}
 /** Chunk packets can decode before a client level is installed. Public visuals never become server owners. */
 @Override public void setLevel(net.minecraft.world.level.Level next){super.setLevel(next);if(projectionOnly&&!next.isClientSide){retained=publicOriginal==null?new CompoundTag():publicOriginal.copy();inert=true;projectionOnly=false;publicOriginal=null;reader=newReader();ink=paper=frame=output=ItemStack.EMPTY;}}
 public boolean scanner(){return ((NativeApplianceBlock)getBlockState().getBlock()).scanner();}public boolean printer(){return !scanner();}public boolean inert(){return inert;}public boolean publicVisual(){return projectionOnly;}
 public boolean mutable(){return !inert&&!projectionOnly&&!isRemoved()&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&!com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(level,worldPosition)&&!com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(level,worldPosition)&&!com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition);}
 public boolean usable(ServerPlayer player){return mutable()&&NativeConfigurationAccess.canEditDialog(player,this);}
 public int activation(){return activation;}
 @Override public ChannelList getRedstoneChannels(){return channels;}
 @Override public BlockEntity channelBlockEntity(){return this;}
 @Override public boolean isChannelMemberActive(){return !inert&&NativeChannelMember.super.isChannelMemberActive();}
 @Override public void setChannelLevel(int value){channelLevel=value;signalDirty=true;}
 @Override public void beforeChannelRegistration(){ready=true;signalDirty=true;}
 @Override public void onLoad(){
  super.onLoad();
  ready=false;
  signalKnown=false;
  if(level instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(this);
 }
 @Override public void setRemoved(){
  if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);
  super.setRemoved();
 }
 @Override public void onChunkUnloaded(){
  if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);
  super.onChunkUnloaded();
 }
 public void configureRedstone(int mode,ChannelList bank){
  if(!mutable()||!ApplianceAutomation.valid(mode)||bank==null||mode==activation&&bank.equals(channels))return;
  activation=mode;
  channels=bank;
  signalKnown=false;
  signalDirty=true;
  NativeRedstoneChannels.channelsChanged(this);
  changed();
 }
 public void redstoneChanged(){signalDirty=true;}
 public void tick(){
  if(!ready||(!signalDirty&&output.isEmpty())||!mutable())return;
  if(signalDirty){
   signalDirty=false;
   boolean powered=activation!=ApplianceAutomation.MANUAL&&(channelLevel>0||NativeLoadedRedstonePower.level(level,worldPosition)>0);
   boolean trigger=signalKnown&&ApplianceAutomation.triggers(activation,lastSignal,powered);
   lastSignal=powered;
   signalKnown=true;
   if(trigger)activate(null);
  }
  if(output.isEmpty()||level.getGameTime()<nextExport)return;
  nextExport=level.getGameTime()+8;
  var target=outputTarget();
  if(target==null)return;
  exporting=true;
  try{
   var remainder=ItemHandlerHelper.insertItemStacked(target,output.copy(),false);
   if(remainder.getCount()!=output.getCount()){output=remainder;changed();}
  }finally{exporting=false;}
 }
 public int outputSlot(){return scanner()?2:3;}
 private Direction facing(){return getBlockState().getValue(NativeApplianceBlock.FACING);}
 private Direction outputSide(){return scanner()?facing().getOpposite():Direction.UP;}
 private IItemHandler outputTarget(){
  Direction side=outputSide();
  var neighbor=worldPosition.relative(side);
  return level.hasChunkAt(neighbor)?level.getCapability(Capabilities.ItemHandler.BLOCK,neighbor,side.getOpposite()):null;
 }
 public boolean connectedOutput(){return outputTarget()!=null;}
 public boolean canActivate(ServerPlayer player){return mutable()&&(player==null||usable(player));}
 public boolean canProduce(){return output.isEmpty();}
 private boolean acceptsInput(int slot,ItemStack stack){
  return slot==0?NativeCredentialItems.accepts(stack,true):canPlaceItem(slot,stack);
 }
 public IItemHandler itemPort(Direction side){
  if(side==null||side!=outputSide()&&side.getAxis()!=facing().getClockWise().getAxis())return null;
  int index=side.ordinal();
  if(ports[index]==null)ports[index]=createPort(side);
  return ports[index];
 }
 private IItemHandler createPort(Direction side){
  return new IItemHandler(){
   private boolean outlet(){return side==outputSide();}
   private boolean inlet(){return side.getAxis()==facing().getClockWise().getAxis();}
   private boolean valid(int slot){return slot>=0&&slot<getSlots();}
   @Override public int getSlots(){return outlet()?1:inlet()?outputSlot():0;}
   @Override public ItemStack getStackInSlot(int slot){return valid(slot)?getItem(outlet()?outputSlot():slot).copy():ItemStack.EMPTY;}
   @Override public int getSlotLimit(int slot){return outlet()||scanner()||slot==0?1:64;}
   @Override public boolean isItemValid(int slot,ItemStack stack){return !outlet()&&valid(slot)&&acceptsInput(slot,stack);}
   @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
    if(stack.isEmpty()||exporting||!mutable()||!isItemValid(slot,stack)||!bounded(stack))return stack;
    var existing=getItem(slot);
    if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,stack))return stack;
    int amount=Math.min(stack.getCount(),Math.min(getSlotLimit(slot),stack.getMaxStackSize())-existing.getCount());
    if(amount<=0)return stack;
    if(!simulate){
     var inserted=stack.copyWithCount(existing.getCount()+amount);
     if(slot==0){if(!replaceChip(existing,inserted))return stack;}
     else setItem(slot,inserted);
    }
    return stack.copyWithCount(stack.getCount()-amount);
   }
   @Override public ItemStack extractItem(int slot,int count,boolean simulate){
    if(exporting||!mutable()||!outlet()||!valid(slot)||count<=0||output.isEmpty())return ItemStack.EMPTY;
    return simulate?output.copyWithCount(Math.min(count,output.getCount())):removeItem(outputSlot(),count);
   }
  };
 }
 public boolean publishScan(ItemStack expected,ItemStack result){
  if(!canProduce())return false;
  if(!connectedOutput())return replaceChip(expected,result);
  output=result;
  nextExport=0;
  if(replaceChip(expected,ItemStack.EMPTY))return true;
  output=ItemStack.EMPTY;
  return false;
 }
 public ItemStack chipStack(){return reader.tokenCopy();}public ItemStack inkStack(){return ink.copy();}public ItemStack paperStack(){return paper.copy();}public ItemStack frameStack(){return frame.copy();}
 public NativeTokenReader.Visual chipVisual(){return projectionOnly?chipVisual:reader.visual(level==null?0:level.getGameTime());}public NativeTokenReader.Motion chipMotion(){return projectionOnly?chipMotion:reader.motion(level==null?0:level.getGameTime());}
 public long buttonSequence(){return buttonSequence;}public boolean pressed(){return level!=null&&level.getGameTime()<pressedUntil;}
 public boolean interactChip(ServerPlayer player,InteractionHand hand){return usable(player)&&reader.interact(player,hand);}
 public boolean replaceChip(ItemStack expected,ItemStack replacement){if(!mutable()||!ItemStack.matches(chipStack(),expected)||!replacement.isEmpty()&&(replacement.getCount()!=1||!NativeCredentialItems.accepts(replacement,true)))return false;var tag=reader.write(level.registryAccess());if(replacement.isEmpty())tag.remove("InsertedToken");else tag.put("InsertedToken",NativeCredentialItems.writeStack(replacement,level.registryAccess()));var next=newReader();try{next.read(tag,level.registryAccess());}catch(RuntimeException invalid){return false;}reader=next;changed();return true;}
 public boolean consumePrintInputs(ItemStack expectedChip,ItemStack expectedInk,ItemStack expectedPaper,int inkCount,int paperCount){
  return finishPrint(expectedChip,expectedInk,expectedPaper,inkCount,paperCount,()->ItemStack.EMPTY);
 }
 public boolean finishPrint(ItemStack expectedChip,ItemStack expectedInk,ItemStack expectedPaper,int inkCount,int paperCount,java.util.function.Supplier<ItemStack> product){if(!mutable()||!canProduce()||!printer()||inkCount<0||paperCount<0||inkCount>64||paperCount>64||!ItemStack.matches(chipStack(),expectedChip)||!ItemStack.matches(ink,expectedInk)||!ItemStack.matches(paper,expectedPaper)||ink.getCount()<inkCount||paper.getCount()<paperCount||inkCount>0&&!ink.is(Items.INK_SAC)||paperCount>0&&!paper.is(Items.PAPER))return false;output=product.get();nextExport=0;ink.shrink(inkCount);paper.shrink(paperCount);changed();return true;}
 public boolean activate(ServerPlayer player){if(!canActivate(player)||!canProduce()||player!=null&&level.getGameTime()-lastAction<4)return false;lastAction=level.getGameTime();buttonSequence++;pressedUntil=level.getGameTime()+8;changed();if(player!=null)NativeApplianceHooks.display(player,this);return NativeApplianceHooks.PRODUCT.activate(player,this);}
 public static boolean acceptedFrame(ItemStack stack){return stack.is(Items.FILLED_MAP)||stack.is(Items.WRITTEN_BOOK)||stack.is(Items.ENCHANTED_BOOK);}
 public boolean interactFrame(ServerPlayer player,InteractionHand hand){if(!scanner()||!usable(player))return false;var held=player.getItemInHand(hand);if(frame.isEmpty()){if(held.isEmpty()||!acceptedFrame(held)||!bounded(held))return false;frame=held.copyWithCount(1);if(!player.isCreative())held.shrink(1);}else{if(!held.isEmpty())return false;var taken=frame;frame=ItemStack.EMPTY;if(!player.getInventory().add(taken))player.drop(taken,false);}changed();NativeApplianceHooks.display(player,this);return true;}
 private boolean bounded(ItemStack stack){try{return stack.isEmpty()||stack.save(level.registryAccess()).sizeInBytes()<=1024*1024;}catch(RuntimeException invalid){return false;}}
 public void changed(){setChanged();}
 @Override public void setChanged(){super.setChanged();if(mutable()&&level instanceof ServerLevel server){revision++;server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);for(var player:server.players())if(player.distanceToSqr(worldPosition.getCenter())<=96*96)NativeApplianceHooks.display(player,this);}}
 @Override public int getContainerSize(){return outputSlot()+1;}@Override public boolean isEmpty(){return chipStack().isEmpty()&&ink.isEmpty()&&paper.isEmpty()&&frame.isEmpty()&&output.isEmpty();}
 @Override public ItemStack getItem(int slot){return slot==outputSlot()?output:slot==0?chipStack():scanner()?slot==1?frame:ItemStack.EMPTY:slot==1?ink:slot==2?paper:ItemStack.EMPTY;}
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot>0&&slot<outputSlot()&&(scanner()?acceptedFrame(stack):slot==1?stack.is(Items.INK_SAC):stack.is(Items.PAPER));}
 @Override public void setItem(int slot,ItemStack stack){if(!mutable()||slot<=0||slot>=getContainerSize()||!stack.isEmpty()&&(!canPlaceItem(slot,stack)||!bounded(stack)))return;if(slot==outputSlot()){if(stack.isEmpty()){output=ItemStack.EMPTY;changed();}return;}var copy=stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(Math.min(stack.getCount(),scanner()?1:64));if(scanner())frame=copy;else if(slot==1)ink=copy;else paper=copy;changed();}
 @Override public ItemStack removeItem(int slot,int count){if(!mutable()||slot<0||slot>=getContainerSize()||count<=0)return ItemStack.EMPTY;if(slot==0){var result=chipStack();if(!result.isEmpty())replaceChip(result,ItemStack.EMPTY);return result;}var stack=getItem(slot);var result=stack.split(count);if(!result.isEmpty())changed();return result;}
 @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,64);}@Override public boolean stillValid(Player player){return player instanceof ServerPlayer server?usable(server):!inert&&!isRemoved()&&level!=null&&NativeConfigurationAccess.canEditDialog(player,this);}
 @Override public void clearContent(){if(!mutable())return;var chip=chipStack();if(!chip.isEmpty())replaceChip(chip,ItemStack.EMPTY);ink=paper=frame=output=ItemStack.EMPTY;changed();}
 public java.util.List<ItemStack> ownedDrops(){if(inert)return java.util.List.of();var list=new java.util.ArrayList<ItemStack>();for(int slot=0;slot<getContainerSize();slot++){var stack=getItem(slot);if(!stack.isEmpty())list.add(stack.copy());}return java.util.List.copyOf(list);}
 private static void typed(CompoundTag tag,String key,int type){if(tag.contains(key)&&!tag.contains(key,type))throw new IllegalArgumentException("Appliance field type: "+key);}
 private static ItemStack publicFrame(ItemStack source){if(source.isEmpty())return ItemStack.EMPTY;var result=new ItemStack(source.getItem(),1);var id=source.get(net.minecraft.core.component.DataComponents.MAP_ID);if(id!=null)result.set(net.minecraft.core.component.DataComponents.MAP_ID,id);return result;}
 private void settings(CompoundTag tag){
  tag.putInt("ApplianceActivation",activation);
  tag.putIntArray("ApplianceChannels",channels.toArray());
 }
 private void descriptors(CompoundTag tag){tag.putInt("ApplianceRevision",revision);tag.putLong("ApplianceButtonSequence",buttonSequence);tag.putLong("AppliancePressedUntil",pressedUntil);var visual=chipVisual();var motion=chipMotion();tag.putInt("ApplianceChipCompany",visual.company());tag.putInt("ApplianceChipLogo",visual.logo());tag.putBoolean("ApplianceChipIcon",visual.icon());tag.putBoolean("ApplianceChipOccupied",visual.occupied());tag.putLong("ApplianceChipSequence",motion.sequence());tag.putInt("ApplianceChipAge",motion.age());}
 private static void readerSchema(CompoundTag reader){if(reader.contains("ChipSlot")&&!reader.getBoolean("ChipSlot")||reader.contains("AnyToken")&&!reader.getBoolean("AnyToken")||reader.contains("AllowedTokens")&&reader.getInt("AllowedTokens")!=127||reader.getInt("TokenBehavior")!=0||reader.getLong("TokenPulseUntil")!=0||reader.getBoolean("TokenToggled")||reader.getBoolean("TokenToggleApplied")||reader.contains("TokenReturnToMost")||reader.contains("TokenReturnToLeast")||reader.contains("TokenReturnTo"))throw new IllegalArgumentException("Appliance reader must remain passive");for(String key:java.util.List.of("AnyToken","ChipSlot","TokenToggled","TokenToggleApplied")){typed(reader,key,Tag.TAG_BYTE);if(reader.contains(key)&&(reader.getByte(key)<0||reader.getByte(key)>1))throw new IllegalArgumentException("Appliance reader flag");}for(String key:java.util.List.of("AllowedTokens","TokenLogo","TokenBehavior"))typed(reader,key,Tag.TAG_INT);if(reader.contains("AllowedTokens")&&(reader.getInt("AllowedTokens")<0||reader.getInt("AllowedTokens")>127)||reader.contains("TokenLogo")&&!com.vandorlabs.credentials.CredentialCatalog.validCompany(reader.getInt("TokenLogo"))||reader.contains("TokenBehavior")&&(reader.getInt("TokenBehavior")<0||reader.getInt("TokenBehavior")>3))throw new IllegalArgumentException("Appliance reader choice");for(String key:java.util.List.of("TokenPulseUntil","TokenInsertStarted","TokenRejectedUntil","TokenInsertionSequence")){typed(reader,key,Tag.TAG_LONG);if(reader.getLong(key)<0)throw new IllegalArgumentException("Appliance reader timeline");}for(String key:java.util.List.of("TokenReturnToMost","TokenReturnToLeast"))typed(reader,key,Tag.TAG_LONG);if(reader.contains("TokenReturnToMost")!=reader.contains("TokenReturnToLeast")||reader.contains("TokenReturnTo")&&!reader.hasUUID("TokenReturnTo"))throw new IllegalArgumentException("Appliance reader return UUID");}
 private static ItemStack readItem(CompoundTag tag,String key,HolderLookup.Provider r){if(!tag.contains(key))return ItemStack.EMPTY;if(!tag.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Appliance item type");var item=ItemStack.parse(r,tag.getCompound(key)).orElseThrow(()->new IllegalArgumentException("Unresolved appliance item"));if(item.getCount()<1||item.getCount()>item.getMaxStackSize()||item.save(r).sizeInBytes()>1024*1024)throw new IllegalArgumentException("Appliance item bound");return item;}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r){super.loadAdditional(tag,r);try{if(tag.sizeInBytes()>3*1024*1024+16*1024)throw new IllegalArgumentException("Appliance envelope");if(tag.contains("ApplianceVersion")&&(!tag.contains("ApplianceVersion",Tag.TAG_INT)||tag.getInt("ApplianceVersion")!=1))throw new IllegalArgumentException("Future appliance schema");for(String key:java.util.List.of("ApplianceRevision","ApplianceChipCompany","ApplianceChipLogo","ApplianceChipAge","ApplianceActivation"))typed(tag,key,Tag.TAG_INT);for(String key:java.util.List.of("ApplianceButtonSequence","AppliancePressedUntil","ApplianceChipSequence"))typed(tag,key,Tag.TAG_LONG);for(String key:java.util.List.of("AppliancePublic","ApplianceChipIcon","ApplianceChipOccupied")){typed(tag,key,Tag.TAG_BYTE);if(tag.contains(key)&&(tag.getByte(key)<0||tag.getByte(key)>1))throw new IllegalArgumentException("Appliance boolean");}typed(tag,"ApplianceReader",Tag.TAG_COMPOUND);
 typed(tag,"ApplianceChannels",Tag.TAG_INT_ARRAY);
 int mode=tag.getInt("ApplianceActivation");
 int[] rawChannels=tag.getIntArray("ApplianceChannels");
 if(!ApplianceAutomation.valid(mode)||rawChannels.length>ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Appliance activation settings");
 var bank=ChannelList.of(rawChannels);if(tag.contains("ApplianceReader")){var rawReader=tag.getCompound("ApplianceReader");readerSchema(rawReader);if(rawReader.contains("InsertedToken")){typed(rawReader,"InsertedToken",Tag.TAG_COMPOUND);var token=NativeCredentialItems.readStack(rawReader.getCompound("InsertedToken"),r);if(token.isEmpty()||token.getCount()!=1||!NativeCredentialItems.accepts(token,true))throw new IllegalArgumentException("Appliance chip count/type");}}if(!com.vandorlabs.credentials.CredentialCatalog.validCompany(tag.getInt("ApplianceChipCompany"))||tag.contains("ApplianceChipLogo")&&(tag.getInt("ApplianceChipLogo")< -1||tag.getInt("ApplianceChipLogo")>=com.vandorlabs.credentials.CredentialCatalog.COMPANY_COUNT))throw new IllegalArgumentException("Appliance chip appearance");if(tag.getInt("ApplianceChipAge")<0||tag.getInt("ApplianceChipAge")>20)throw new IllegalArgumentException("Appliance chip age");if(tag.getBoolean("AppliancePublic")&&level!=null&&!level.isClientSide)throw new IllegalArgumentException("Public appliance projection cannot own inventory");if(tag.getLong("ApplianceButtonSequence")<0||tag.getLong("AppliancePressedUntil")<0||tag.getLong("ApplianceChipSequence")<0||tag.getInt("ApplianceRevision")<0)throw new IllegalArgumentException("Appliance counter");projectionOnly=tag.getBoolean("AppliancePublic");if(projectionOnly&&(tag.contains("ApplianceReader")||tag.contains("ApplianceInk")||tag.contains("AppliancePaper")||tag.contains("ApplianceOutput")))throw new IllegalArgumentException("Private inventory in appliance projection");var next=newReader();var contents=tag.getCompound("ApplianceReader");next.read(contents,r);var nextInk=readItem(tag,"ApplianceInk",r);var nextPaper=readItem(tag,"AppliancePaper",r);var nextFrame=readItem(tag,"ApplianceFrame",r);
 var nextOutput=readItem(tag,"ApplianceOutput",r);
 if(!nextOutput.isEmpty()&&(nextOutput.getCount()!=1||(scanner()?!NativeCredentialItems.accepts(nextOutput,true):!nextOutput.is(Items.FILLED_MAP)&&!nextOutput.is(Items.WRITTEN_BOOK))))throw new IllegalArgumentException("Appliance output kind");if(!nextInk.isEmpty()&&(!nextInk.is(Items.INK_SAC)||scanner())||!nextPaper.isEmpty()&&(!nextPaper.is(Items.PAPER)||scanner())||!nextFrame.isEmpty()&&(!scanner()||nextFrame.getCount()!=1||!acceptedFrame(nextFrame)))throw new IllegalArgumentException("Appliance inventory kind");for(String key:tag.getAllKeys())if(key.startsWith("Appliance")&&!java.util.Set.of("ApplianceVersion","AppliancePublic","ApplianceReader","ApplianceInk","AppliancePaper","ApplianceFrame","ApplianceOutput","ApplianceActivation","ApplianceChannels","ApplianceRevision","ApplianceButtonSequence","AppliancePressedUntil","ApplianceChipCompany","ApplianceChipLogo","ApplianceChipIcon","ApplianceChipOccupied","ApplianceChipSequence","ApplianceChipAge").contains(key))throw new IllegalArgumentException("Future appliance field");if(projectionOnly&&!nextFrame.isEmpty()&&!ItemStack.matches(publicFrame(nextFrame),nextFrame))throw new IllegalArgumentException("Private frame components in appliance projection");reader=next;ink=nextInk;paper=nextPaper;frame=nextFrame;output=nextOutput;activation=mode;channels=bank;signalKnown=false;signalDirty=true;publicOriginal=projectionOnly?tag.copy():null;revision=tag.getInt("ApplianceRevision");buttonSequence=tag.getLong("ApplianceButtonSequence");pressedUntil=tag.getLong("AppliancePressedUntil");chipVisual=new NativeTokenReader.Visual(true,tag.getInt("ApplianceChipCompany"),tag.contains("ApplianceChipLogo")?tag.getInt("ApplianceChipLogo"):-1,tag.getBoolean("ApplianceChipIcon"),0,tag.getBoolean("ApplianceChipOccupied"));chipMotion=new NativeTokenReader.Motion(tag.getLong("ApplianceChipSequence"),Math.clamp(tag.getInt("ApplianceChipAge"),0,20),false,0);retained=tag.copy();for(String key:java.util.List.of("id","x","y","z","components","neoforge:attachments","NeoForgeData"))retained.remove(key);for(String key:java.util.List.copyOf(retained.getAllKeys()))if(key.startsWith("Appliance"))retained.remove(key);inert=false;}catch(RuntimeException failure){retained=tag.copy();inert=true;projectionOnly=false;publicOriginal=null;reader=newReader();ink=paper=frame=output=ItemStack.EMPTY;}}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r){if(inert){tag.merge(retained.copy());return;}super.saveAdditional(tag,r);tag.merge(retained);tag.putInt("ApplianceVersion",1);
 settings(tag);
 tag.remove("ApplianceOutput");
 if(!output.isEmpty())tag.put("ApplianceOutput",output.save(r));
 tag.put("ApplianceReader",reader.write(r));tag.remove("ApplianceInk");tag.remove("AppliancePaper");tag.remove("ApplianceFrame");if(!ink.isEmpty())tag.put("ApplianceInk",ink.save(r));if(!paper.isEmpty())tag.put("AppliancePaper",paper.save(r));if(!frame.isEmpty())tag.put("ApplianceFrame",frame.save(r));tag.putInt("ApplianceRevision",revision);tag.putLong("ApplianceButtonSequence",buttonSequence);tag.putLong("AppliancePressedUntil",pressedUntil);descriptors(tag);tag.putInt("ApplianceChipAge",20);}
 public static CompoundTag appearance(CompoundTag full,HolderLookup.Provider r){var result=new CompoundTag();result.putInt("ApplianceVersion",1);result.putBoolean("AppliancePublic",true);for(String key:java.util.List.of("ApplianceRevision","ApplianceButtonSequence","AppliancePressedUntil","ApplianceChipCompany","ApplianceChipLogo","ApplianceChipIcon","ApplianceChipOccupied","ApplianceChipSequence","ApplianceChipAge","ApplianceActivation","ApplianceChannels"))if(full.contains(key))result.put(key,full.get(key).copy());if(full.contains("ApplianceFrame",Tag.TAG_COMPOUND)){var original=ItemStack.parseOptional(r,full.getCompound("ApplianceFrame"));if(!original.isEmpty()){var frame=publicFrame(original);result.put("ApplianceFrame",frame.save(r));}}return result;}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){var tag=new CompoundTag();if(inert){tag.putInt("ApplianceVersion",-1);return tag;}tag.putInt("ApplianceVersion",1);tag.putBoolean("AppliancePublic",true);descriptors(tag);settings(tag);var visible=publicFrame(frame);if(!visible.isEmpty())tag.put("ApplianceFrame",visible.save(r));return tag;}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
