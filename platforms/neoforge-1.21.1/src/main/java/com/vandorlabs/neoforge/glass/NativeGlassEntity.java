package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Ordinary glass authority; installed-thread preparation publishes world-free worker/render data. */
public final class NativeGlassEntity extends BlockEntity implements ContainedFluidHost,com.vandorlabs.surface.TextureJoinOwner {
 public static final String RECOVERY="GlassRecoverySnapshot",PLACEMENT_RECOVERY="GlassPlacementRecovery";
 public record Publication(NativeGlassSettings settings,NativeGlassGeometry.Snapshot geometry,NativeFlatGlassVisual visual){}
 public static final ModelProperty<Publication> GLASS=new ModelProperty<>();
 private NativeGlassSettings settings;
 private com.vandorlabs.surface.TextureJoinSettings textureJoin=com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
 public com.vandorlabs.surface.TextureJoinSettings textureJoin(){return settings!=null&&!settings.glass().join?textureJoin.disabled():textureJoin;}
 public void setTextureJoin(com.vandorlabs.surface.TextureJoinSettings next){if(textureJoin().equals(next))return;textureJoin=java.util.Objects.requireNonNull(next);var g=settings.glass();settings=new NativeGlassSettings(new com.vandorlabs.persistence.ProgrammableGlassSettings(g.shade,g.material,g.layout,g.stretch,g.tint,g.framed,g.size,next.direction!=0,g.panelFacing,g.legacySize),settings.frames(),settings.playback());NativeGlassService.invalidate(this);setChanged();NativeFluidWorld.queue(this);if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
 private CompoundTag retained=new CompoundTag();
 private NativeFluidSlots fluids=new NativeFluidSlots();
 private volatile Publication publication;
 private volatile ModelData modelData;
 private NativeFluidRenderSnapshot fluidRender=NativeFluidRenderSnapshot.EMPTY;
 private long geometryRevision,modelRevision;
 private boolean inert,unloading;
 private net.minecraft.world.item.ItemStack forcedRemoval=net.minecraft.world.item.ItemStack.EMPTY;
 NativeGlassService.Group group;
 public NativeGlassEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);settings=NativeGlassSettings.read(new CompoundTag()).resolveLegacySize(1);publishLocal(NativeGlassService.straight(this),true);}
 public NativeGlassSettings settings(){return settings;}
 public Publication publication(){return publication;}
 public NativeFlatGlassVisual visual(){return publication.visual;}
 public boolean inert(){return inert;}
 public boolean available(){return !inert&&!unloading&&!isRemoved();}
 void resolveSize(){if(!settings.glass().legacySize)return;settings=settings.resolveLegacySize(getBlockState().getValue(NativeGlassBlock.SIZE));if(level instanceof ServerLevel)setChanged();}
 void publishLocal(Publication next,boolean force){var before=publication;if(!force&&before!=null&&NativeGlassService.same(before,next))return;if(before==null||!before.geometry.boxes().equals(next.geometry.boxes())||before.geometry.scalar().facing!=next.geometry.scalar().facing||before.geometry.scalar().near!=next.geometry.scalar().near)geometryRevision++;publication=next;modelRevision++;modelData=ModelData.builder().with(GLASS,next).with(NativeFluidRenderSnapshot.DATA,fluidRender).build();if(level!=null&&NativeFluidWorld.installed(this)){requestModelDataUpdate();if(level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
 void settings(NativeGlassSettings next){if(!settings.same(next)){settings=next;NativeGlassService.invalidate(this);}}
 public boolean configure(NativeGlassSettings next,int stateSize){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!available()||!NativeFluidWorld.installed(this)||!NativeGlassService.valid(next)||stateSize<0||stateSize>2)return false;settings(next);if(getBlockState().getValue(NativeGlassBlock.DEPTH)==0&&getBlockState().getValue(NativeGlassBlock.SIZE)!=stateSize)level.setBlock(worldPosition,getBlockState().setValue(NativeGlassBlock.SIZE,stateSize),2);refreshFluidGeometry();setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);NativeGlassService.changed(this);return true;}
 private static void fluidsSupported(CompoundTag tag){if(tag.contains(NativeFluidSlots.KEY)&&!tag.contains(NativeFluidSlots.KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unsupported glass fluid envelope");var data=tag.getCompound(NativeFluidSlots.KEY);for(String key:new String[]{"Side0","Side1"})if(data.contains(key)&&!data.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unsupported glass fluid slot");}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  forcedRemoval=net.minecraft.world.item.ItemStack.EMPTY;var original=tag.copy();try{
   NativePlatformComponents.requireSupported(original,registries);fluidsSupported(original);
   if(tag.contains("NeoForgeData")&&!tag.contains("NeoForgeData",Tag.TAG_COMPOUND)||tag.contains(RECOVERY)||tag.contains(PLACEMENT_RECOVERY)||tag.contains("NativeGlassSchema")&&(!tag.contains("NativeGlassSchema",Tag.TAG_INT)||tag.getInt("NativeGlassSchema")!=1))throw new IllegalArgumentException("Retained glass owner");
   textureJoin=com.vandorlabs.surface.TextureJoinSettings.read(new com.vandorlabs.neoforge.ModernHousingTags(tag));var next=NativeGlassSettings.read(tag);if(!tag.contains(com.vandorlabs.surface.TextureJoinSettings.DIRECTION_TAG)&&!next.glass().join)textureJoin=new com.vandorlabs.surface.TextureJoinSettings(0,0);try(var platform=NativePlatformComponents.guardLoad(this)){super.loadAdditional(tag.copy(),registries);platform.requireSuccessful();}
   fluids.read(tag,registries);retained=tag.copy();NativePlatformComponents.forgetLoadedFields(retained);
   if(!settings.same(next))NativeGlassService.invalidate(this);settings=settings.same(next)?settings:next;inert=level!=null&&level.isClientSide&&tag.getBoolean("NativeGlassInert");
  }catch(RuntimeException failure){NativeGlassService.invalidate(this);retained=original;settings=NativeGlassSettings.read(new CompoundTag()).resolveLegacySize(getBlockState().getValue(NativeGlassBlock.SIZE));fluids=new NativeFluidSlots();fluidRender=NativeFluidRenderSnapshot.EMPTY;inert=true;}
  if(publication==null||!publication.settings.same(settings)||publication.visual.inert()!=inert||publication.visual.pose().rotated!=getBlockState().getValue(NativeGlassBlock.ROTATED)||publication.visual.pose().depth!=getBlockState().getValue(NativeGlassBlock.DEPTH))publishLocal(NativeGlassService.straight(this),false);
  NativeFluidWorld.queue(this);
 }

 private void writeFluids(CompoundTag tag){var extra=retained.getCompound(NativeFluidSlots.KEY).copy();extra.remove("Side0");extra.remove("Side1");fluids.write(tag);extra.merge(tag.getCompound(NativeFluidSlots.KEY));tag.put(NativeFluidSlots.KEY,extra);}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);settings.write(tag);textureJoin.write(new com.vandorlabs.neoforge.ModernHousingTags(tag));writeFluids(tag);tag.putInt("NativeGlassSchema",1);}
 static final class Prepared {final BlockState state;final CompoundTag snapshot;Prepared(BlockState state,CompoundTag snapshot){this.state=state;this.snapshot=snapshot.copy();}}
 static Prepared prepareItem(NativeGlassBlock block,BlockPos pos,BlockState state,CompoundTag generic,CompoundTag outer,int facing,HolderLookup.Provider registries){
  if(outer!=null&&(outer.contains(RECOVERY)||outer.contains(PLACEMENT_RECOVERY)))throw new IllegalArgumentException("Retained glass item owner");var candidate=new NativeGlassEntity(block.entityType(),pos,state);var merged=candidate.saveWithoutMetadata(registries);if(generic!=null)merged.merge(generic.copy());candidate.loadWithComponents(merged,registries);if(candidate.inert)throw new IllegalArgumentException("Unsupported glass item owner");var result=NativeGlassPlacementPolicy.place(candidate.settings,outer,state.getValue(NativeGlassBlock.SIZE),state.getValue(NativeGlassBlock.DEPTH),facing);BlockState target=state.setValue(NativeGlassBlock.SIZE,result.stateSize());candidate.settings=result.settings();candidate.setBlockState(target);candidate.publishLocal(NativeGlassService.straight(candidate),true);return new Prepared(target,candidate.saveWithoutMetadata(registries));
 }
 void installItem(Prepared prepared){unloading=false;clearRemoved();setBlockState(prepared.state);loadWithComponents(prepared.snapshot.copy(),level.registryAccess());if(inert)throw new IllegalArgumentException("Failed glass platform installation");}
 void retainPlacementRecovery(Prepared prepared){unloading=false;clearRemoved();retained=prepared.snapshot.copy();retained.put(PLACEMENT_RECOVERY,prepared.snapshot.copy());settings=NativeGlassSettings.read(new CompoundTag()).resolveLegacySize(getBlockState().getValue(NativeGlassBlock.SIZE));fluids=new NativeFluidSlots();inert=true;publishLocal(NativeGlassService.straight(this),true);}
 void prepareRecoveryDrop(net.minecraft.world.item.ItemStack stack){forcedRemoval=stack.copy();}
 net.minecraft.world.item.ItemStack takeRecoveryDrop(){var stack=forcedRemoval;forcedRemoval=net.minecraft.world.item.ItemStack.EMPTY;return stack;}
 /** Placement samples the two separating sides; storage availability is independent of visible gap volume. */
 public void capturePlacementFluids(BlockState previous){if(!available()||!NativeFluidWorld.installed(this))return;refreshFluidGeometry();var fallback=NativeFluidWorld.fromState(previous.getFluidState());net.neoforged.neoforge.fluids.FluidStack[] boundary={net.neoforged.neoforge.fluids.FluidStack.EMPTY,net.neoforged.neoforge.fluids.FluidStack.EMPTY};for(Direction direction:fluidPlacementFaces()){BlockPos next=worldPosition.relative(direction);if(!NativeFluidWorld.loaded(level,next))continue;var fluid=NativeFluidWorld.fromState(level.getFluidState(next));if(fluid.isEmpty()||NativeFluidWorld.gaseous(fluid))continue;int side=containedFluidSide(.5+direction.getStepX()*.51,.5+direction.getStepY()*.51,.5+direction.getStepZ()*.51);boundary[side]=fluid;}boolean sides=!boundary[0].isEmpty()||!boundary[1].isEmpty();for(int side=0;side<2;side++){var fluid=sides?boundary[side]:fallback;if(!fluid.isEmpty()&&!NativeFluidWorld.gaseous(fluid)&&!fluids.occupied(side))fluids.set(side,fluid,level.registryAccess());}}
 @Override public boolean acceptsPlacementFluid(int side){return side==0||side==1;}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();settings.write(tag);textureJoin.write(new com.vandorlabs.neoforge.ModernHousingTags(tag));fluids.write(tag);tag.putBoolean("NativeGlassInert",inert);tag.putInt("NativeGlassSchema",1);return tag;}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
 @Override public ModelData getModelData(){return modelData;}
 public CompoundTag recoverySnapshot(){return retained.copy();}
 @Override public BlockEntity fluidOwner(){return this;}
 @Override public NativeFluidSlots containedFluids(){return fluids;}
 @Override public NativeFluidVolume fluidVolumeSnapshot(){return publication.geometry.fluids();}
 @Override public long fluidGeometryRevision(){return geometryRevision;}
 @Override public long modelDataRevision(){return modelRevision;}
 @Override public int containedFluidSide(double x,double y,double z){return publication.geometry.side(x,y,z);}
 @Override public int intrinsicContainedLight(){return inert?0:settings.intrinsicFrameLight();}
 @Override public boolean fluidActionsAllowed(){return available();}
 @Override public Direction[] fluidPlacementFaces(){Direction facing=NativeGlassService.facing(this);return new Direction[]{facing,facing.getOpposite()};}
 @Override public NativeFluidRenderSnapshot fluidRenderSnapshot(){return fluidRender;}
 @Override public void publishFluidRenderSnapshot(NativeFluidRenderSnapshot next){fluidRender=java.util.Objects.requireNonNull(next);modelData=ModelData.builder().with(GLASS,publication).with(NativeFluidRenderSnapshot.DATA,next).build();}
 @Override public void refreshFluidGeometry(){NativeGlassService.refresh(this);}
 @Override public void containedFluidsChanged(){setChanged();if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);NativeFluidWorld.queue(this);}}
 @Override public void onLoad(){super.onLoad();unloading=false;NativeGlassService.invalidate(this);NativeFluidWorld.queue(this);}
 @Override public void setBlockState(BlockState state){super.setBlockState(state);if(settings!=null){NativeGlassService.invalidate(this);NativeFluidWorld.queue(this);}}
 @Override public void setRemoved(){unloading=true;NativeGlassService.retire(this);NativeFluidWorld.removed(this);super.setRemoved();}
 @Override public void onChunkUnloaded(){unloading=true;NativeGlassService.retire(this);NativeFluidWorld.unloaded(this);super.onChunkUnloaded();}
}
