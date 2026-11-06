package com.vandorlabs.neoforge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Guards the final vanilla component codec, which runs outside subclass load/save hooks. */
public final class NativePlatformComponents {
 private NativePlatformComponents(){}
 private static final ThreadLocal<LoadScope> LOADING=new ThreadLocal<>();
 public static LoadScope guardLoad(BlockEntity owner){return new LoadScope(owner);}
 public static final class LoadScope implements AutoCloseable {
  private final BlockEntity owner;private final LoadScope previous;private boolean failed,closed;
  private LoadScope(BlockEntity owner){this.owner=java.util.Objects.requireNonNull(owner);previous=LOADING.get();LOADING.set(this);}
  public void requireSuccessful(){if(failed)throw new IllegalArgumentException("Failed block entity attachment decode");}
  @Override public void close(){if(!closed){if(previous==null)LOADING.remove();else LOADING.set(previous);closed=true;}}
 }
 /** Mixin bridge: invokes the original codec once and preserves its normal platform catch behavior. */
 public static Object readAttachment(net.neoforged.neoforge.attachment.IAttachmentSerializer<Tag,?> codec,net.neoforged.neoforge.attachment.IAttachmentHolder owner,Tag tag,HolderLookup.Provider registries)throws Exception{var scope=LOADING.get();try{Object value=codec.read(owner,tag,registries);if(value==null&&scope!=null&&scope.owner==owner)scope.failed=true;return value;}catch(Exception failure){if(scope!=null&&scope.owner==owner)scope.failed=true;throw failure;}}
 /** Reject unknown or malformed maps before authoritative settings or platform state are installed. */
 public static void requireSupported(CompoundTag original,HolderLookup.Provider registries){requireAttachments(original);if(original.contains("components")&&DataComponentMap.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),original.get("components")).result().isEmpty())throw new IllegalArgumentException("Unsupported block entity components");}
 /** Platform deserialization silently skips these envelopes; retaining them inert preserves their original owner. */
 private static void requireAttachments(CompoundTag original){if(!original.contains("neoforge:attachments"))return;if(!(original.get("neoforge:attachments") instanceof CompoundTag attachments))throw new IllegalArgumentException("Unsupported block entity attachment envelope");for(String key:attachments.getAllKeys()){var id=net.minecraft.resources.ResourceLocation.tryParse(key);var type=id==null?null:net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.get(id);if(type==null||((com.vandorlabs.neoforge.mixin.NativeAttachmentTypeAccess)(Object)type).vandorlabs$serializer()==null)throw new IllegalArgumentException("Unsupported block entity attachment type");}}
 /** The final vanilla save must not append stale live components over a retained inert envelope. */
 public static void clearForInertSave(BlockEntity tile){tile.setComponents(DataComponentMap.EMPTY);}
 /** Call only after successful platform installation; subsequent saves serialize current live state. */
 public static void forgetLoadedFields(CompoundTag retained){for(String key:java.util.List.of("components","NeoForgeData","neoforge:attachments"))retained.remove(key);}
}
