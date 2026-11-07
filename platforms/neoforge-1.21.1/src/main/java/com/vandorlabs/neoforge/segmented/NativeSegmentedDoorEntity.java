package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
/** Native stationary segment owner. No lifecycle operation transfers a world block or inventory. */
public class NativeSegmentedDoorEntity extends BlockEntity implements NativeChannelMember,ContainedFluidHost {
 public static final int MAX_HEIGHT=32,MAX_COLUMNS=32;
 NativeSegmentedDoorSettings settings=NativeSegmentedDoorSettings.DEFAULT;
 CompoundTag retained=new CompoundTag();boolean inert,refreshPending=true,powerPending=true,channelSignal;
 ChannelList channels=ChannelList.EMPTY;
 int topOffset,height=1,column,columns=1,ticksPerSection=10,texture=-1,duration=1;
 boolean targetOpen,localRedstone=true,signalOpens=true,formed,configured;
 double from;long started,sequence;UUID identity=UUID.randomUUID(),motionIdentity;
 public record Publication(NativeSegmentedVisual visual,NativeSegmentedVisual.Motion motion){}
 private volatile Publication publication;
 private volatile NativeSegmentedTerrainSnapshot.ClientProjection clientTerrain=NativeSegmentedTerrainSnapshot.ClientProjection.EMPTY;
 private static volatile java.util.function.Consumer<NativeSegmentedDoorEntity> clientObserver=ignored->{};
 public static void clientObserver(java.util.function.Consumer<NativeSegmentedDoorEntity> observer){clientObserver=Objects.requireNonNull(observer);}
 public final NativeSegmentedTerrainSnapshot.ClientProjection clientTerrain(){return clientTerrain;}
 public final void clientTerrain(NativeSegmentedTerrainSnapshot.ClientProjection next){if(level!=null&&!level.isClientSide)throw new IllegalStateException("Segmented terrain flags are client-only");clientTerrain=Objects.requireNonNull(next);}
 private void notifyClientPublication(){if(level!=null&&level.isClientSide)clientObserver.accept(this);}

 final NativeSegmentedFluids fluidState=new NativeSegmentedFluids(this);
 public NativeSegmentedDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);publishLocal();}
 com.vandorlabs.segmented.SegmentedCellArtwork cellArtwork=com.vandorlabs.segmented.SegmentedCellArtwork.INHERITED;
 public final com.vandorlabs.segmented.SegmentedCellArtwork cellArtwork(){return cellArtwork;}
 public final int effectiveTexture(){return cellArtwork.effective(texture);}
 private void readCellArtwork(CompoundTag tag){String key=com.vandorlabs.segmented.SegmentedCellArtwork.FIELD;if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Invalid segment artwork type");cellArtwork=tag.contains(key)?com.vandorlabs.segmented.SegmentedCellArtwork.explicit(tag.getInt(key),NativeSegmentedDoorEntity::validTexture):com.vandorlabs.segmented.SegmentedCellArtwork.INHERITED;}
 private void writeCellArtwork(CompoundTag tag){String key=com.vandorlabs.segmented.SegmentedCellArtwork.FIELD;if(cellArtwork.overridden)tag.putInt(key,cellArtwork.choice);else tag.remove(key);}
 public final NativeSegmentedDoorSettings settings(){return settings;}public final int topOffset(){return topOffset;}public final int height(){return height;}public final int column(){return column;}public final int columnsCount(){return columns;}public final int ticksPerSection(){return ticksPerSection;}public final int texture(){return texture;}public final int duration(){return duration;}public final long sequence(){return sequence;}public final long started(){return started;}public final double initialPose(){return from;}public final boolean targetOpen(){return targetOpen;}public final boolean formed(){return formed;}public final boolean configured(){return configured;}public final boolean localRedstone(){return localRedstone;}public final boolean signalOpens(){return signalOpens;}public final boolean inert(){return inert;}public final Publication publication(){return publication;}
 public final void stopFluids(){fluidState.update(true);}
 public final void capturePlacementFluids(BlockState previous){fluidState.capturePlacement(previous);}
 @Override public boolean fluidActionsAllowed(){return !inert;}
 @Override public boolean openingFluidVolume(){return !inert;}
 @Override public Direction[] fluidPlacementFaces(){return new Direction[]{facing().getOpposite(),facing()};}
 @Override public BlockEntity fluidOwner(){return this;}
 @Override public NativeFluidSlots containedFluids(){return fluidState.slots();}
 @Override public NativeFluidRegions fluidSurfaceRegions(){return fluidState.surfaceRegions();}
 @Override public NativeFluidSlots.Snapshot effectiveFluidSnapshot(){return fluidState.effective();}
 @Override public NativeFluidVolume fluidVolumeSnapshot(){return fluidState.volume();}
 @Override public long fluidGeometryRevision(){return fluidState.revision();}
 @Override public long modelDataRevision(){return fluidState.modelRevision();}
 @Override public NativeFluidRenderSnapshot fluidRenderSnapshot(){return fluidState.render();}
 @Override public void publishFluidRenderSnapshot(NativeFluidRenderSnapshot snapshot){fluidState.render(snapshot);}
 @Override public void refreshFluidGeometry(){fluidState.refresh();}
 @Override public int containedFluidSide(double x,double y,double z){return fluidState.side(x,y,z);}
 @Override public void containedFluidsChanged(){fluidState.changed();}
 @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){
  var terrain=clientTerrain.terrain();
  if(terrain==NativeSegmentedTerrainSnapshot.EMPTY)terrain=NativeSegmentedTerrainSnapshot.unstaged(facing(),texture,topOffset,height,settings);
  return net.neoforged.neoforge.client.model.data.ModelData.builder().with(NativeFluidRenderSnapshot.DATA,fluidState.render()).with(NativeSegmentedTerrainSnapshot.DATA,terrain).build();
 }
 public Direction facing(){return getBlockState().hasProperty(NativeSegmentedDoorBlock.FACING)?getBlockState().getValue(NativeSegmentedDoorBlock.FACING):Direction.NORTH;}
 public static NativeSegmentedDoorEntity at(Level level,BlockPos pos){if(level==null||!level.hasChunkAt(pos))return null;var raw=level.getBlockEntity(pos);return raw instanceof NativeSegmentedDoorEntity tile&&!tile.isRemoved()&&!tile.inert?tile:null;}
 boolean installed(){if(level instanceof net.minecraft.server.level.ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Segmented world access requires server thread");return !inert&&level!=null&&!isRemoved()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this;}
 NativeSegmentedDoorEntity neighbor(Direction side){var other=at(level,worldPosition.relative(side));return other!=null&&other.getBlockState().getBlock()==getBlockState().getBlock()&&other.facing()==facing()?other:null;}
 public NativeSegmentedDoorEntity root(){var other=topOffset==0?this:at(level,worldPosition.above(topOffset));return other==null?this:other;}
 public double pose(double partial){double time=level==null?0:level.getGameTime();double t=Math.max(0,Math.min(1,(time-started+partial)/Math.max(1,duration)));return from+((targetOpen?1:0)-from)*t;}
 public void scheduleRefresh(){refreshPending=true;powerPending=true;}
 @Override public void onLoad(){super.onLoad();publishLocal();NativeSegmentedTargets.add(this);fluidState.loaded();scheduleRefresh();if(level!=null&&!level.isClientSide)NativeRedstoneChannels.queueRegistration(this);}
 @Override public void setRemoved(){NativeSegmentedTargets.remove(this);NativeFluidWorld.removed(this);NativeRedstoneChannels.unregister(this);super.setRemoved();notifyClientPublication();}
 @Override public void onChunkUnloaded(){NativeSegmentedTargets.remove(this);NativeFluidWorld.unloaded(this);NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();notifyClientPublication();}
 public void tickServer(){if(!installed()||level.isClientSide)return;if(refreshPending){refreshPending=false;refreshColumn();}if(powerPending){powerPending=false;NativeSegmentedDoorLifecycle.refreshPower(this);}if(topOffset==0&&from!=(targetOpen?1:0)&&level.getGameTime()-started>=duration){var all=NativeSegmentedDoorLifecycle.columnMembers(this);for(var t:all)t.from=targetOpen?1:0;publishMotion(all);}fluidState.tick();}
 public void refreshColumn(){NativeSegmentedDoorLifecycle.refreshColumn(this);}
 public List<NativeSegmentedDoorEntity> roots(){return NativeSegmentedDoorLifecycle.roots(this);}
 public void toggle(Player player){operate(player,!root().targetOpen);}
 public void operate(Player player,boolean open){NativeSegmentedDoorLifecycle.operate(this,player,open);}
 public void configureCellTexture(Player player,int choice,boolean inherit){var next=inherit?com.vandorlabs.segmented.SegmentedCellArtwork.INHERITED:com.vandorlabs.segmented.SegmentedCellArtwork.explicit(choice,NativeSegmentedDoorEntity::validTexture);var all=NativeSegmentedDoorLifecycle.members(NativeSegmentedDoorLifecycle.roots(this));NativeSegmentedDoorLifecycle.permissions(this,player,all);cellArtwork=next;configured=true;publish(all);}
 void clearCellArtwork(){cellArtwork=com.vandorlabs.segmented.SegmentedCellArtwork.INHERITED;}
 public void configureAll(Player player,ChannelList next,int ticks,boolean local,boolean opens,int texture,NativeSegmentedDoorSettings settings){NativeSegmentedDoorLifecycle.configure(this,player,next,ticks,local,opens,texture,settings,false);}
 public void configurePart(Player player,ChannelList next,int ticks,boolean local,boolean opens,int texture,NativeSegmentedDoorSettings settings){NativeSegmentedDoorLifecycle.configure(this,player,next,ticks,local,opens,texture,settings,true);}
 static void publish(List<NativeSegmentedDoorEntity> all){publish(all,false);}
 static void publishMotion(List<NativeSegmentedDoorEntity> all){publish(all,true);}
 private static void publish(List<NativeSegmentedDoorEntity> all,boolean rootsOnly){
  if(all.isEmpty())return;var first=all.get(0).root();var right=first.facing().getClockWise();var origin=first.getBlockPos().relative(right,-first.column);Map<BlockPos,NativeSegmentedDoorEntity> grid=new HashMap<>();
  for(int x=0;x<first.columns;x++)for(int row=0;row<first.height;row++){var pos=origin.relative(right,x).below(row);var t=at(first.level,pos);if(t!=null&&t.facing()==first.facing())grid.put(pos,t);}
  var layouts=SegmentedWindowLayout.derive(first.columns,first.height,new SegmentedWindowLayout.Cells(){private NativeSegmentedDoorEntity cell(int x,int y){return grid.get(origin.relative(right,x).below(first.height-1-y));}public boolean joined(int x,int y){var t=cell(x,y);return t!=null&&NativeSegmentedArtwork.gate(t.effectiveTexture())<0&&t.settings.window&&t.settings.join;}public int shape(int x,int y){var t=cell(x,y);return t==null?-1:t.settings.shape;}public int facing(int x,int y){var t=cell(x,y);return t==null?-1:t.facing().get3DDataValue();}});
  for(var t:all){List<Integer> artwork=new ArrayList<>();List<NativeSegmentedDoorSettings> rows=new ArrayList<>();List<SegmentedWindowLayout> windows=new ArrayList<>();var owner=t.root();for(int row=0;row<t.height;row++){var part=grid.get(owner.getBlockPos().below(row));rows.add(part!=null?part.settings:t.settings);artwork.add(part!=null?part.effectiveTexture():t.effectiveTexture());int at=(t.height-1-row)*first.columns+t.column;windows.add(at>=0&&at<layouts.length?layouts[at]:SegmentedWindowLayout.SINGLE);}t.publication=t.project(rows,windows,artwork);}
  for(var t:all){t.fluidState.localChanged();t.refreshFluidGeometry();if(rootsOnly)t.fluidState.motionChanged();}
  for(var t:all)t.setChanged();for(var t:all)if(!rootsOnly||t.topOffset==0)t.publicationChanged();
 }
 protected void publicationChanged(){notifyClientPublication();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
 private Publication project(List<NativeSegmentedDoorSettings> rows,List<SegmentedWindowLayout> windows,List<Integer> artwork){return new Publication(new NativeSegmentedVisual(columns,height,column,facing(),texture,settings,rows,windows,!inert&&formed&&level!=null,false,false,false,false,artwork),new NativeSegmentedVisual.Motion(identity,motionIdentity,sequence,targetOpen,pose(0),ticksPerSection,started,duration));}
 void publishLocal(){List<Integer> artwork=new ArrayList<>();List<NativeSegmentedDoorSettings> rows=new ArrayList<>();List<SegmentedWindowLayout> windows=new ArrayList<>();for(int row=0;row<height;row++){rows.add(settings);artwork.add(effectiveTexture());windows.add(SegmentedWindowLayout.SINGLE);}publication=project(rows,windows,artwork);fluidState.localChanged();notifyClientPublication();}

 @Override public BlockEntity channelBlockEntity(){return this;}@Override public ChannelList getRedstoneChannels(){return channels;}@Override public void setChannelLevel(int level){boolean next=level>0;if(channelSignal!=next){channelSignal=next;powerPending=true;root().powerPending=true;}}
 @Override public boolean isChannelMemberActive(){return !inert&&NativeChannelMember.super.isChannelMemberActive();}
 public static boolean validTexture(int value){if(value==-1||com.vandorlabs.tiles.MaterialChoicePolicy.isCustom(value))return true;if(!com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(value))return false;var e=com.vandorlabs.neoforge.ModernFinishCatalog.option(value);String category=e.category();int separator=category.indexOf('/');if(separator>=0)category=category.substring(0,separator);return e.visible()&&(e.general()||category.equals("Doors")||category.equals("Double Doors")||category.equals("Frames")||category.equals("Frame"));}
 public CompoundTag configuration(){CompoundTag tag=new CompoundTag();settings.write(tag);writeCellArtwork(tag);tag.putInt("SegmentedTexture",texture);tag.putInt("SegmentedSpeed",ticksPerSection);tag.putBoolean("SegmentedLocalRedstone",localRedstone);tag.putBoolean("SegmentedSignalOpens",signalOpens);tag.putBoolean("SegmentedConfigured",true);return tag;}
 public final boolean copiedAvailable(){return level instanceof net.minecraft.server.level.ServerLevel server&&server.getServer().isSameThread()&&NativeRedstoneChannels.open(server)&&installed();}
 /** Bounded choices only: fluid buffers, motion identities and retained platform data remain local. */
 public final com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.State copiedState(){
  if(!copiedAvailable())throw new IllegalArgumentException("Unavailable copied Segmented Door section");
  return new com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.State(settings,channels,ticksPerSection,localRedstone,signalOpens,texture,cellArtwork);
 }
 public final boolean configureCopied(CompoundTag values,Player actor){
  if(!copiedAvailable()||actor==null||!actor.isAlive()||actor.isSpectator()||actor.level()!=level||!level.mayInteract(actor,worldPosition)||!actor.mayUseItemAt(worldPosition,Direction.UP,actor.getMainHandItem()))return false;
  final com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.Plan plan;
  try{plan=com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.prepare(copiedState(),values);}catch(IllegalArgumentException malformed){return false;}
  if(!plan.applicable())return false;var next=plan.state();
  if(plan.configuration()){
   try{NativeSegmentedDoorLifecycle.configure(this,actor,next.channels(),next.ticks(),next.local(),next.opens(),next.texture(),next.settings(),true,next.artwork());}catch(IllegalArgumentException refused){return false;}
  }else{
   channels=next.channels();NativeRedstoneChannels.channelsChanged(this);setChanged();publicationChanged();powerPending=true;root().powerPending=true;
  }
  return true;
 }
 public CompoundTag itemSettings(){CompoundTag tag=configuration();tag.putIntArray("RedstoneChannels",channels.toArray());return tag;}
 public void applyItemConfiguration(CompoundTag tag,ChannelList selected){if(selected==null)throw new IllegalArgumentException("Missing channels");readCellArtwork(tag);settings=NativeSegmentedDoorSettings.read(tag);texture=tag.contains("SegmentedTexture",3)&&validTexture(tag.getInt("SegmentedTexture"))?tag.getInt("SegmentedTexture"):-1;ticksPerSection=clamp(tag.getInt("SegmentedSpeed"),5,30);localRedstone=tag.getBoolean("SegmentedLocalRedstone");signalOpens=tag.getBoolean("SegmentedSignalOpens");channels=selected;configured=true;publishLocal();}
 public void configure(Player player,ChannelList next,int ticks,boolean local,boolean opens){NativeSegmentedDoorLifecycle.configureLegacy(this,player,next,ticks,local,opens);}
 public void configureTexture(Player player,int choice){NativeSegmentedDoorLifecycle.configureTexture(this,player,choice);}

 private static UUID uuid(CompoundTag tag,String key,UUID fallback){if(tag.hasUUID(key))return tag.getUUID(key);return tag.contains(key+"Most",99)&&tag.contains(key+"Least",99)?new UUID(tag.getLong(key+"Most"),tag.getLong(key+"Least")):fallback;}
 private static void writeUuid(CompoundTag tag,String key,UUID value){tag.remove(key);tag.remove(key+"Most");tag.remove(key+"Least");if(value==null)return;tag.putLong(key+"Most",value.getMostSignificantBits());tag.putLong(key+"Least",value.getLeastSignificantBits());}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){retained=tag.copy();for(String key:List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retained.remove(key);try{if(tag.getBoolean("SegmentedPlacementRecovery"))throw new IllegalArgumentException("Unfinished Segmented placement recovery");super.loadAdditional(tag,registries);fluidState.read(tag,registries);readCellArtwork(tag);settings=NativeSegmentedDoorSettings.read(tag);topOffset=clamp(tag.getInt("SegmentedTopOffset"),0,31);height=clamp(tag.getInt("SegmentedHeight"),1,32);column=clamp(tag.getInt("SegmentedColumn"),0,31);columns=clamp(tag.getInt("SegmentedColumns"),1,32);if(column>=columns)throw new IllegalArgumentException("Invalid Segmented span");ticksPerSection=clamp(tag.contains("SegmentedSpeed")?tag.getInt("SegmentedSpeed"):10,5,30);texture=tag.contains("SegmentedTexture",3)&&validTexture(tag.getInt("SegmentedTexture"))?tag.getInt("SegmentedTexture"):-1;targetOpen=tag.getBoolean("SegmentedOpen");localRedstone=!tag.contains("SegmentedLocalRedstone")||tag.getBoolean("SegmentedLocalRedstone");signalOpens=!tag.contains("SegmentedSignalOpens")||tag.getBoolean("SegmentedSignalOpens");formed=tag.getBoolean("SegmentedFormed");configured=tag.getBoolean("SegmentedConfigured");from=tag.getDouble("SegmentedFrom");if(!Double.isFinite(from))from=0;from=Math.max(0,Math.min(1,from));started=tag.getLong("SegmentedStarted");sequence=Math.max(0,tag.getLong("SegmentedSequence"));duration=clamp(tag.getInt("SegmentedDuration"),1,960);identity=uuid(tag,"SegmentedIdentity",identity);motionIdentity=uuid(tag,"SegmentedMotion",null);channels=readChannels(tag);inert=false;publishLocal();}catch(RuntimeException failed){inert=true;formed=false;settings=NativeSegmentedDoorSettings.LEGACY;topOffset=column=0;height=columns=1;from=0;sequence=0;duration=1;publishLocal();}scheduleRefresh();}
 private static ChannelList readChannels(CompoundTag tag){if(!tag.contains("RedstoneChannels",11))return ChannelList.EMPTY;try{return ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){return ChannelList.EMPTY;}}
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.merge(retained);if(inert)return;var original=new NativeSegmentedDoorSettings.Saved(retained);tag.merge(settings.same(original.settings)?original.unchanged():original.edited(settings));if(!settings.same(NativeSegmentedDoorSettings.LEGACY)){tag.putInt("SegmentedColumn",column);tag.putInt("SegmentedColumns",columns);}tag.putInt("SegmentedTopOffset",topOffset);tag.putInt("SegmentedHeight",height);tag.putInt("SegmentedSpeed",ticksPerSection);writeCellArtwork(tag);if(texture>=0)tag.putInt("SegmentedTexture",texture);else tag.remove("SegmentedTexture");tag.putBoolean("SegmentedOpen",targetOpen);tag.putBoolean("SegmentedLocalRedstone",localRedstone);tag.putBoolean("SegmentedSignalOpens",signalOpens);tag.putBoolean("SegmentedFormed",formed);tag.putBoolean("SegmentedConfigured",configured);tag.putDouble("SegmentedFrom",from);tag.putLong("SegmentedStarted",started);tag.putLong("SegmentedSequence",sequence);tag.putInt("SegmentedDuration",duration);writeUuid(tag,"SegmentedIdentity",identity);writeUuid(tag,"SegmentedMotion",motionIdentity);tag.putIntArray("RedstoneChannels",channels.toArray());fluidState.write(tag);}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();if(inert)return tag;writeCellArtwork(tag);settings.write(tag);tag.putInt("SegmentedColumn",column);tag.putInt("SegmentedColumns",columns);tag.putInt("SegmentedTopOffset",topOffset);tag.putInt("SegmentedHeight",height);tag.putInt("SegmentedSpeed",ticksPerSection);tag.putInt("SegmentedTexture",texture);tag.putBoolean("SegmentedLocalRedstone",localRedstone);tag.putBoolean("SegmentedSignalOpens",signalOpens);tag.putBoolean("SegmentedConfigured",configured);tag.putIntArray("RedstoneChannels",channels.toArray());tag.putBoolean("SegmentedOpen",targetOpen);tag.putBoolean("SegmentedFormed",formed);tag.putDouble("SegmentedFrom",from);tag.putLong("SegmentedStarted",started);tag.putLong("SegmentedSequence",sequence);tag.putInt("SegmentedDuration",duration);writeUuid(tag,"SegmentedIdentity",identity);writeUuid(tag,"SegmentedMotion",motionIdentity);fluidState.writePublic(tag,registries);return tag;}
 @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){UUID incoming=uuid(tag,"SegmentedIdentity",null);if(incoming!=null&&incoming.equals(identity)&&tag.getLong("SegmentedSequence")<sequence)return;loadWithComponents(tag,registries);if(level!=null&&level.isClientSide&&!inert)publish(NativeSegmentedDoorLifecycle.columnMembers(this));}
 @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
