package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Permanent physical mount; invisible owned cells never become separate usable drops. */
public final class NativeLegacyLandingEntity extends BlockEntity implements NativeChannelMember {
 private NativeLegacyLandingData.Configuration settings=NativeLegacyLandingData.defaults();
 private UUID identity=UUID.randomUUID();private long revision;private float progress;private boolean lastSignal,owned,inert,legacyProxies,detached,restoring=true,inputDirty=true,forceInput,blocked,clientInitialized;
 private int ownerX,ownerY,ownerZ,received;private CompoundTag retained=new CompoundTag(),recovery;private boolean pristine=true;private String error="";
 private BlockPos clientControllerPos;private UUID clientControllerIdentity;
 private NativeLegacyLandingEntity controller;long columnEpoch=-1;private NativeLegacyShapes.Pose shape;private ItemStack removalDrop;
 public NativeLegacyLandingEntity(BlockPos pos,BlockState state){super(NativeLegacyLandingBindings.type(state),pos,state);}
 public NativeLegacyLandingKind kind(){return ((NativeLegacyLandingBlock)getBlockState().getBlock()).kind;}
 public UUID identity(){return identity;}public boolean inert(){return inert;}public boolean owned(){return owned;}public boolean legacyProxy(){return legacyProxies;}public float progress(){return progress;}public String error(){return error;}public NativeLegacyLandingData.Configuration settings(){return settings;}
 public BlockPos ownerPos(){return owned?worldPosition.offset(ownerX,ownerY,ownerZ):worldPosition;}
 public boolean physicalRoot(){return !owned&&!getBlockState().getValue(NativeLegacyLandingBlock.LOWER);}
 public boolean extended(){return getBlockState().getValue(NativeLegacyLandingBlock.EXTENDED);}
 public float targetDistance(){return LegacyLandingSettings.target(extended(),settings.pixels());}
 public NativeLegacyLandingEntity physical(){return NativeLegacyLandingBlock.owner(level,worldPosition);}
 public NativeLegacyLandingEntity controller(){var root=physical();if(root==null)return null;if(root.level.isClientSide&&root.clientControllerPos!=null){var selected=NativeLegacyLandingBlock.owner(root.level,root.clientControllerPos);return selected!=null&&selected.kind()==root.kind()&&selected.identity.equals(root.clientControllerIdentity)?selected:null;}return root.controller==null?root:root.controller;}
 void controller(NativeLegacyLandingEntity next){if(controller==next)return;NativeRedstoneChannels.unregister(this);controller=next;if(next!=this&&progress!=0){progress=0;shape=null;setChanged();}if(next==this)NativeRedstoneChannels.queueRegistration(this);changed(false);}
 boolean current(){return !inert&&!detached&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&NativeLegacyLandingBlock.loaded(server,worldPosition)&&server.getBlockEntity(worldPosition)==this&&server.getBlockState(worldPosition)==getBlockState();}
 public NativeLegacyShapes.Pose shape(){if(shape==null)shape=NativeLegacyShapes.pose(this);return shape;}
 void helperOf(NativeLegacyLandingEntity root){identity=root.identity;owned=true;ownerX=root.worldPosition.getX()-worldPosition.getX();ownerY=root.worldPosition.getY()-worldPosition.getY();ownerZ=root.worldPosition.getZ()-worldPosition.getZ();legacyProxies=false;retained=new CompoundTag();pristine=true;setChanged();}
 boolean ownHelper(NativeLegacyLandingEntity helper){return helper!=null&&!helper.inert&&helper.owned&&helper.ownerPos().equals(worldPosition)&&helper.kind()==kind()&&(identity.equals(helper.identity)||legacyProxies&&helper.legacyProxies);}
 boolean pristine(){return pristine&&!hasAttachments()&&components().isEmpty()&&getPersistentData().isEmpty();}
 public void prepareRemoval(ItemStack stack){removalDrop=stack.copy();}public ItemStack removalDrop(){return removalDrop==null?null:removalDrop.copy();}
 public boolean configure(Player actor,NativeLegacyLandingData.Configuration next){if(!current()||!physicalRoot()||!NativeConfigurationAccess.canEditDialog(actor,this)||!NativeLegacyLandingData.configurable(kind(),next))return false;return change(next,extended(),true);}
 public boolean target(Player actor,boolean next){if(!current()||!physicalRoot()||actor!=null&&!NativeConfigurationAccess.canEditDialog(actor,this))return false;return change(settings,next,false);}
 private boolean change(NativeLegacyLandingData.Configuration next,boolean target,boolean configure){
  if(NativeLegacyFootprints.mutating(level,worldPosition))return false;float distance=Math.max(progress,LegacyLandingSettings.target(target,next.pixels()));
  NativeLegacyFootprints.Plan plan=null;var previous=settings;var originalState=getBlockState();var desired=originalState.setValue(NativeLegacyLandingBlock.EXTENDED,target);
  try{plan=NativeLegacyFootprints.prepare(this,next,distance);plan.install();if(!current())throw new IllegalStateException("Telescopic owner changed during preparation");
   if(desired!=originalState&&(!level.setBlock(worldPosition,desired,3)||level.getBlockEntity(worldPosition)!=this||level.getBlockState(worldPosition)!=desired))throw new IllegalStateException("Telescopic target publication refused");
   boolean automation=previous.mode()!=next.mode()||!previous.channels().equals(next.channels());if(!previous.channels().equals(next.channels()))NativeRedstoneChannels.unregister(this);settings=next;restoring=false;blocked=false;shape=null;error="";changed(true);NativeLegacyUpdates.queue(this);if(configure){inputDirty=true;forceInput|=automation;}return true;
  }catch(RuntimeException refused){settings=previous;if(level.getBlockEntity(worldPosition)==this&&level.getBlockState(worldPosition)==desired&&desired!=originalState)try{if(!level.setBlock(worldPosition,originalState,3)||level.getBlockEntity(worldPosition)!=this)throw new IllegalStateException("Telescopic root rollback refused");}catch(RuntimeException rollback){if(plan!=null)failRecovery(plan.original,new CompoundTag());refused.addSuppressed(rollback);}if(plan!=null)try{plan.undo();}catch(RuntimeException rollback){refused.addSuppressed(rollback);}blocked=true;error=refused.getMessage();return false;}
 }
 void queueOverflow(){blocked=true;error="Too many pending telescopic updates; retry after loading settles";}
 public void placed(){restoring=false;NativeLegacyUpdates.queue(this);inputDirty=true;forceInput=true;}
 public void inputChanged(){inputDirty=true;NativeLegacyUpdates.queue(this);}
 void deferred(){if(!current())return;if(owned){var root=physical();if(root!=null)NativeLegacyUpdates.queue(root);else NativeLegacyFootprints.orphan(this);return;}if(!physicalRoot())return;
  if(!NativeLegacyUpdates.watch(this)){error="Too many loaded telescopic controllers";return;}NativeLegacyColumns.refresh(this);if(controller()!=this)return;
  try{NativeLegacyFootprints.prepare(this,settings,Math.max(progress,targetDistance())).install();blocked=false;error="";}catch(IllegalArgumentException refusal){blocked=true;error=refusal.getMessage();}
  NativeRedstoneChannels.queueRegistration(this);
 }
 private void evaluate(boolean force){if(!current()||!physicalRoot()||controller()!=this||restoring&&!force)return;boolean signal=received>0||NativeLegacyLandingBlock.localPower((ServerLevel)level,worldPosition)>0;if(settings.mode()!=0&&(force||signal!=lastSignal))target(null,LegacyLandingSettings.automaticTarget(settings.mode(),signal));if(signal!=lastSignal){lastSignal=signal;setChanged();}}
 public void tick(){if(!current()||!physicalRoot()||controller()!=this)return;if(inputDirty){inputDirty=false;boolean force=forceInput;forceInput=false;evaluate(force);}if(restoring||blocked)return;float next=LegacyLandingSettings.advance(progress,targetDistance());if(next==progress)return;
  if(!NativeLegacyFootprints.loaded(this)){error="Load the complete telescopic footprint";return;}int before=(int)Math.ceil(Math.max(progress,targetDistance()));progress=next;shape=null;setChanged();
  if((int)Math.ceil(Math.max(progress,targetDistance()))!=before||progress==targetDistance())NativeLegacyUpdates.queue(this);
  if(progress==targetDistance()||level.getGameTime()%4==0)changed(false);
 }
 void changed(boolean neighbors){revision++;setChanged();if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(neighbors)level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}}
 void failRecovery(CompoundTag original,CompoundTag details){retained=original.copy();for(String key:List.of("id","x","y","z"))retained.remove(key);recovery=details.copy();inert=true;error="Telescopic recovery retained; original data was not discarded";NativeRedstoneChannels.unregister(this);setChanged();}
 @Override public void onLoad(){super.onLoad();detached=false;restoring=true;NativeLegacyUpdates.topology(this);}
 @Override public void setRemoved(){detached=true;NativeRedstoneChannels.unregister(this);NativeLegacyUpdates.remove(this);super.setRemoved();}
 @Override public void onChunkUnloaded(){detached=true;NativeRedstoneChannels.unregister(this);NativeLegacyUpdates.remove(this);super.onChunkUnloaded();}
 @Override public BlockEntity channelBlockEntity(){return this;}@Override public ChannelList getRedstoneChannels(){return settings.channels();}
 @Override public boolean isChannelMemberActive(){return current()&&physicalRoot()&&controller()==this;}
 @Override public void beforeChannelRegistration(){if(current()){boolean was=restoring;restoring=false;if(was)inputDirty=true;}}
 @Override public void setChannelLevel(int value){if(!detached&&received!=value){received=value;inputDirty=true;}}
 @Override public int localSignalLevel(int channel){return current()&&physicalRoot()?NativeLegacyLandingBlock.localPower((ServerLevel)level,worldPosition):0;}
 private NativeLegacyLandingData.State state(){return new NativeLegacyLandingData.State(settings,progress,lastSignal,owned,ownerX,ownerY,ownerZ,false,identity,revision);}
 public CompoundTag itemSettings(){var tag=retained.copy();for(String key:List.of("NativeLegacyLandingIdentity","NativeLegacyLandingRevision","NativeLegacyLandingSchema","NativeLegacyProxyUpgrade","Progress","LastSignal","GearHasOwner","GearOwnerX","GearOwnerDistance","GearOwnerZ"))tag.remove(key);NativeLegacyLandingData.configuration(tag,settings);return tag;}
 public void writeItem(ItemStack stack){var data=new CompoundTag();data.put(inert?"LegacyLandingRecovery":"LegacyLandingSettings",inert?recoveryItem():itemSettings());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}
 private CompoundTag recoveryItem(){var tag=retained.copy();if(recovery!=null)tag.put("NativeLegacyLandingRecovery",recovery.copy());return tag;}
 void installItem(CompoundTag data){if(data.contains("LegacyLandingRecovery",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Retained telescopic recovery needs repair before placement");var raw=data.getCompound("LegacyLandingSettings").copy();var decoded=NativeLegacyLandingData.read(raw);if(!NativeLegacyLandingData.configurable(kind(),decoded.configuration()))throw new IllegalArgumentException("Unsupported telescopic size");for(String key:List.of("GearHasOwner","GearOwnerX","GearOwnerDistance","GearOwnerZ","NativeLegacyProxyUpgrade","NativeLegacyLandingRecovery"))raw.remove(key);NativeLegacyLandingData.write(raw,new NativeLegacyLandingData.State(decoded.configuration(),0,false,false,0,0,0,false,identity,0));var complete=saveWithFullMetadata(level.registryAccess());complete.merge(raw);complete.putInt("x",worldPosition.getX());complete.putInt("y",worldPosition.getY());complete.putInt("z",worldPosition.getZ());NativePlatformComponents.requireSupported(complete,level.registryAccess());try(var guard=NativePlatformComponents.guardLoad(this)){loadWithComponents(complete,level.registryAccess());guard.requireSuccessful();}if(inert||owned||progress!=0)throw new IllegalArgumentException("Configured telescopic placement refused");shape=null;}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);retained=tag.copy();for(String key:List.of("id","x","y","z"))retained.remove(key);try{if(tag.sizeInBytes()>2*1024*1024||tag.contains("NativeLegacyLandingRecovery"))throw new IllegalArgumentException("Retained telescopic recovery or oversized own data");NativePlatformComponents.requireSupported(tag,registries);var data=NativeLegacyLandingData.read(tag);settings=data.configuration();progress=data.progress();lastSignal=data.lastSignal();owned=data.owned();ownerX=data.ownerX();ownerY=data.ownerY();ownerZ=data.ownerZ();if(data.identity()!=null)identity=data.identity();revision=data.revision();legacyProxies=data.legacy()||tag.getBoolean("NativeLegacyProxyUpgrade");pristine=knownProxy(tag);inert=false;if(level!=null&&level.isClientSide)readClientController(tag);NativePlatformComponents.forgetLoadedFields(retained);}catch(IllegalArgumentException unsupported){inert=true;}shape=null;}
 private static boolean knownProxy(CompoundTag tag){for(String key:tag.getAllKeys())if(!Set.of("id","x","y","z","Progress","ExtensionPixels","GearSize","RedstoneMode","RedstoneChannel","RedstoneChannels","LastSignal","GearHasOwner","GearOwnerDistance","GearOwnerX","GearOwnerZ","NativeLegacyLandingSchema","NativeLegacyLandingIdentity","NativeLegacyLandingRevision","NativeLegacyProxyUpgrade").contains(key)&&!(Set.of("components","NeoForgeData","neoforge:attachments","ForgeCaps").contains(key)&&tag.contains(key,Tag.TAG_COMPOUND)&&tag.getCompound(key).isEmpty()))return false;return true;}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){if(inert){NativePlatformComponents.clearForInertSave(this);tag.merge(retained);if(recovery!=null)tag.put("NativeLegacyLandingRecovery",recovery.copy());return;}super.saveAdditional(tag,registries);tag.merge(retained);NativeLegacyLandingData.write(tag,state());if(legacyProxies)tag.putBoolean("NativeLegacyProxyUpgrade",true);}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();NativeLegacyLandingData.write(tag,state());tag.putBoolean("NativeLegacyInert",inert);if(controller!=null&&controller!=this){tag.putLong("NativeLegacyController",controller.worldPosition.asLong());tag.putUUID("NativeLegacyControllerIdentity",controller.identity);}return tag;}
 @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){try{var data=NativeLegacyLandingData.read(tag);if(clientInitialized&&!identity.equals(data.identity()))return;if(revision>data.revision()&&identity.equals(data.identity()))return;settings=data.configuration();progress=data.progress();owned=data.owned();ownerX=data.ownerX();ownerY=data.ownerY();ownerZ=data.ownerZ();identity=data.identity();revision=data.revision();clientInitialized=true;inert=tag.getBoolean("NativeLegacyInert");readClientController(tag);shape=null;}catch(IllegalArgumentException invalid){inert=true;}}
 private void readClientController(CompoundTag tag){if(tag.contains("NativeLegacyController")||tag.contains("NativeLegacyControllerIdentity")){if(!tag.contains("NativeLegacyController",Tag.TAG_LONG)||!tag.hasUUID("NativeLegacyControllerIdentity"))throw new IllegalArgumentException("Malformed Ski controller hint");var at=BlockPos.of(tag.getLong("NativeLegacyController"));if(Math.abs((long)at.getX()-worldPosition.getX())>256||Math.abs((long)at.getZ()-worldPosition.getZ())>256||at.getY()>=worldPosition.getY()||worldPosition.getY()-at.getY()>256)throw new IllegalArgumentException("Ski controller hint outside column");clientControllerPos=at;clientControllerIdentity=tag.getUUID("NativeLegacyControllerIdentity");}else{clientControllerPos=null;clientControllerIdentity=null;}}
 @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
