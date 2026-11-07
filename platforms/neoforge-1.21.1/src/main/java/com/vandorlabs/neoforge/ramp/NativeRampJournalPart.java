package com.vandorlabs.neoforge.ramp;
import com.vandorlabs.neoforge.NativePlatformComponents;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
/** Private ownership marker foundation, not a generated Ramp geometry implementation. */
public class NativeRampJournalPart extends BlockEntity {
 private UUID operation;private BlockPos root=BlockPos.ZERO;private boolean inert;private CompoundTag retained=new CompoundTag();
 public NativeRampJournalPart(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
 public final void install(UUID operation,BlockPos root){this.operation=java.util.Objects.requireNonNull(operation);this.root=root.immutable();inert=false;NativeRampReservations.add(this);}
 public boolean ownershipKnown(){return !inert&&operation!=null;}
 public final boolean belongs(UUID id,BlockPos owner){return ownershipKnown()&&id.equals(operation)&&root.equals(owner);}
 public final UUID operationId(){return ownershipKnown()?operation:null;}
 public final BlockPos controller(){return root;}
 /** Real geometry implementations override this with an immutable world-space projection. */
 public java.util.List<net.minecraft.world.phys.AABB> physicalBounds(){return null;}
 /** Subclasses must quarantine the complete incoming envelope if their own body fails. */
 protected final void refuseSnapshot(CompoundTag original){retained=original.copy();operation=null;inert=true;}
 @Override public void onLoad(){super.onLoad();NativeRampReservations.add(this);}
 @Override public void setRemoved(){NativeRampReservations.remove(this);super.setRemoved();}
 @Override public void onChunkUnloaded(){NativeRampReservations.remove(this);super.onChunkUnloaded();}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){var original=tag.copy();try{if(!tag.contains("NativeRampPartSchema",Tag.TAG_INT)||tag.getInt("NativeRampPartSchema")!=1||!tag.hasUUID("Operation")||!tag.contains("RootX",Tag.TAG_INT)||!tag.contains("RootY",Tag.TAG_INT)||!tag.contains("RootZ",Tag.TAG_INT)||Math.abs((long)tag.getInt("RootX")-worldPosition.getX())>64||Math.abs((long)tag.getInt("RootY")-worldPosition.getY())>64||Math.abs((long)tag.getInt("RootZ")-worldPosition.getZ())>64)throw new IllegalArgumentException("Unknown Ramp marker");NativePlatformComponents.requireSupported(tag,registries);try(var platform=NativePlatformComponents.guardLoad(this)){super.loadAdditional(tag.copy(),registries);platform.requireSuccessful();}operation=tag.getUUID("Operation");root=new BlockPos(tag.getInt("RootX"),tag.getInt("RootY"),tag.getInt("RootZ"));NativePlatformComponents.forgetLoadedFields(original);retained=original;inert=false;}catch(RuntimeException failure){retained=original;operation=null;inert=true;}}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){if(inert){NativePlatformComponents.clearForInertSave(this);tag.merge(retained.copy());return;}tag.merge(retained.copy());write(tag);super.saveAdditional(tag,registries);}
 private void write(CompoundTag tag){tag.putInt("NativeRampPartSchema",1);if(operation!=null)tag.putUUID("Operation",operation);tag.putInt("RootX",root.getX());tag.putInt("RootY",root.getY());tag.putInt("RootZ",root.getZ());}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();write(tag);tag.putBoolean("Inert",inert);return tag;}
}
