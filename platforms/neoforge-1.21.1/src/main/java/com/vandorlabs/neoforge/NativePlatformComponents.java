package com.vandorlabs.neoforge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Guards the final vanilla component codec, which runs outside subclass load/save hooks. */
public final class NativePlatformComponents {
 private NativePlatformComponents(){}
 /** Reject unknown or malformed maps before authoritative settings or platform state are installed. */
 public static void requireSupported(CompoundTag original,HolderLookup.Provider registries){if(original.contains("components")&&DataComponentMap.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),original.get("components")).result().isEmpty())throw new IllegalArgumentException("Unsupported block entity components");}
 /** The final vanilla save must not append stale live components over a retained inert envelope. */
 public static void clearForInertSave(BlockEntity tile){tile.setComponents(DataComponentMap.EMPTY);}
 /** Call only after successful platform installation; subsequent saves serialize current live state. */
 public static void forgetLoadedFields(CompoundTag retained){for(String key:java.util.List.of("components","NeoForgeData","neoforge:attachments"))retained.remove(key);}
}
