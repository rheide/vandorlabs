package com.vandorlabs.neoforge.storage;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Appearance copies omit inventory; failed owners have a private, unplaceable recovery envelope. */
public final class NativeStorageItemSettings {
 public static final String CONFIGURATION="BlockEntityTag";
 private NativeStorageItemSettings(){}
 public static CompoundTag configuration(ItemStack stack){var block=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(block!=null)return block.copyTag();var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();if(root.contains(CONFIGURATION)&&!root.contains(CONFIGURATION,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Storage item configuration");return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;}
 private static ItemStack configured(Item item,CompoundTag choices){var stack=new ItemStack(item);if(choices!=null){var root=new CompoundTag();root.put(CONFIGURATION,choices);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;}
 private static CompoundTag appearance(NativeStorageEntity owner,HolderLookup.Provider registries){if(owner.inert()){var tag=new CompoundTag();owner.settings().write(tag);return tag;}var tag=owner.saveWithoutMetadata(registries);for(String key:new String[]{"Items",NativeStorageInventory.SCHEMA,NativeStorageInventory.DATA_VERSION,"id","x","y","z","ChannelLevel","ChannelSignal","JoinedSignalLevel","LightGroupPowered",NativeStorageEntity.SCHEMA})tag.remove(key);for(int slot=0;slot<2;slot++){String key=slot==0?"RedstonePrimary":"RedstoneSecondary";if(tag.contains(key,Tag.TAG_COMPOUND))tag.put(key,com.vandorlabs.neoforge.screen.NativeScreenControls.read(tag.getCompound(key),com.vandorlabs.neoforge.screen.NativeScreenControls.STANDALONE_ROWS).configuration());}return tag;}
 public static ItemStack pick(Item item,NativeStorageEntity owner,HolderLookup.Provider registries){return configured(item,owner==null?null:appearance(owner,registries));}
 public static ItemStack drop(Item item,NativeStorageEntity owner,HolderLookup.Provider registries){if(owner==null||!owner.inert())return pick(item,owner,registries);var recovery=new CompoundTag();recovery.put(NativeStorageEntity.RECOVERY,owner.saveWithoutMetadata(registries));return configured(item,recovery);}
 public static final class Prepared {
  private final CompoundTag snapshot;private final HolderLookup.Provider registries;private final BlockEntityType<?> type;
  private Prepared(NativeStorageEntity owner,HolderLookup.Provider registries){snapshot=owner.saveWithoutMetadata(registries).copy();this.registries=registries;type=owner.getType();}
  public CompoundTag snapshot(){return snapshot.copy();}BlockEntityType<?> type(){return type;}HolderLookup.Provider registries(){return registries;}
 }
 public static Prepared prepare(BlockEntityType<?> type,BlockPos pos,BlockState state,CompoundTag raw,HolderLookup.Provider registries){var draft=new NativeStorageEntity(type,pos,state);var merged=draft.saveWithoutMetadata(registries);if(raw!=null){if(raw.contains(NativeStorageEntity.RECOVERY)||raw.contains(NativeStorageEntity.PLACEMENT_RECOVERY)||raw.contains(NativeStorageEntity.PREVIOUS))throw new IllegalArgumentException("Recovery Storage cannot be placed");merged.merge(raw.copy());if(raw.contains("Items")&&!raw.contains(NativeStorageInventory.SCHEMA)){merged.remove(NativeStorageInventory.SCHEMA);merged.remove(NativeStorageInventory.DATA_VERSION);}}draft.loadWithComponents(merged,registries);if(draft.inert())throw new IllegalArgumentException("Unsupported Storage item ownership");return new Prepared(draft,registries);}
 public static void install(NativeStorageEntity owner,Prepared prepared){if(owner.getType()!=prepared.type)throw new IllegalArgumentException("Storage item family mismatch");owner.loadWithComponents(prepared.snapshot.copy(),prepared.registries);if(owner.inert())throw new IllegalArgumentException("Prepared Storage platform data failed installation");}
}
