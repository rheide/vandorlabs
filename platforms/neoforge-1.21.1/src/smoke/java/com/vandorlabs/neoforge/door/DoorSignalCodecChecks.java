package com.vandorlabs.neoforge.door;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
/** Canonical shared Member codec, including conflicting unrelated old-native tag names. */
final class DoorSignalCodecChecks {
 static void run(HolderLookup.Provider registries,BlockEntityType<?> type,BlockState state){
  var host=new NativeProgrammableDoorEntity(type,BlockPos.ZERO,state,false,false);var tag=new CompoundTag();tag.putInt("SpaceDoorSchema",2);tag.putBoolean("ChannelSignal",true);tag.putBoolean("RedstoneSignal",false);host.loadWithComponents(tag,registries);if(!host.channelSignal()||!host.getUpdateTag(registries).getBoolean("ChannelSignal"))throw new AssertionError("Canonical positive ChannelSignal lost");var saved=host.saveWithoutMetadata(registries);var copy=new NativeProgrammableDoorEntity(type,BlockPos.ZERO,state,false,false);copy.loadWithComponents(saved,registries);if(!copy.channelSignal())throw new AssertionError("Positive channel signal save/reload lost");tag.putBoolean("ChannelSignal",false);tag.putBoolean("RedstoneSignal",true);host.loadWithComponents(tag,registries);saved=host.saveWithoutMetadata(registries);if(host.channelSignal()||saved.getBoolean("ChannelSignal")||!saved.getBoolean("RedstoneSignal")||host.getUpdateTag(registries).contains("RedstoneSignal"))throw new AssertionError("Unrelated retained field overrides canonical Member state or leaks to projection");System.out.println("[vandorlabs][door-signal-codec] PASS canonical true/false save/public/reload and unrelated conflicting field retention");
 }
}
