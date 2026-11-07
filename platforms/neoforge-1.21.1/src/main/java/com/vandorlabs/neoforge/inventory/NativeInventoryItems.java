package com.vandorlabs.neoforge.inventory;

import com.mojang.serialization.Dynamic;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/** Strict local stack staging for Storage and Programmer slots; no world, global item remapping or physical publication. */
public final class NativeInventoryItems {
 public static final int SOURCE_DATA_VERSION=1343,MAX_NESTED_ITEMS=4096,MAX_DEPTH=16;
 private static final Set<String> MODERN_ROOT=Set.of("id","count","components");
 private static final Set<String> LEGACY_ROOT=Set.of("id","Count","Damage","tag","ForgeCaps","Slot");
 private static final Set<String> CONTAINER_FIELDS=Set.of("id","x","y","z","Items","CustomName","Lock","LootTable","LootTableSeed","ForgeCaps");
 private static final Set<String> CONTAINERS=Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:hopper","minecraft:furnace","minecraft:dropper","minecraft:dispenser","minecraft:shulker_box","minecraft:brewing_stand");
 private static final Set<String> SOURCE_VANILLA=sourceVanilla();
 private static Set<String> sourceVanilla(){var input=NativeInventoryItems.class.getResourceAsStream("/assets/vandorlabs/data/legacy_inventory_items.json");if(input==null)throw new IllegalStateException("Missing Source Storage item manifest");try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("sourceDataVersion").getAsInt()!=SOURCE_DATA_VERSION)throw new IllegalStateException("Wrong Storage source data version");var values=new HashSet<String>();for(var value:root.getAsJsonArray("items")){String id=value.getAsString();if(!id.matches("minecraft:[a-z0-9_]+")||!values.add(id)||values.size()>2048)throw new IllegalStateException("Invalid Source Storage item manifest");}if(values.size()!=411||!values.contains("minecraft:air"))throw new IllegalStateException("Incomplete Source Storage item manifest");return Set.copyOf(values);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
 public static Set<String> sourceVanillaItems(){return SOURCE_VANILLA;}
 /** Duplicate slots cannot overwrite a credential envelope or another inventory's physical contents. */
 public static boolean ownershipBearing(ItemStack stack){
  if(stack.isEmpty())return false;
  if(stack.is(NativeCredentialItems.ACCESS_CARD.get())||stack.is(NativeCredentialItems.DATA_CHIP.get()))return true;
  return containedOwnership(stack);
 }
 /** Any nested physical contents, distinct from the credential itself being an owner. */
 public static boolean containedOwnership(ItemStack stack){
  if(stack.isEmpty())return false;
  var container=stack.get(DataComponents.CONTAINER);if(container!=null&&container.nonEmptyItems().iterator().hasNext())return true;
  var bundle=stack.get(DataComponents.BUNDLE_CONTENTS);if(bundle!=null&&!bundle.isEmpty())return true;
  var projectiles=stack.get(DataComponents.CHARGED_PROJECTILES);return projectiles!=null&&!projectiles.isEmpty();
 }
 private NativeInventoryItems(){}
 public static final class Budget {private int items;private void enter(int depth){if(depth>MAX_DEPTH||++items>MAX_NESTED_ITEMS)throw new IllegalArgumentException("Storage nested item bound");}}
 public static ItemStack legacy(CompoundTag raw,HolderLookup.Provider registries){return legacy(raw,registries,new Budget(),0);}
 public static ItemStack legacy(CompoundTag raw,HolderLookup.Provider registries,Budget budget){return legacy(raw,registries,budget,0);}
 private static ItemStack legacy(CompoundTag raw,HolderLookup.Provider registries,Budget budget,int depth){
  budget.enter(depth);if(raw.getByte("Count")<=0){for(String key:raw.getAllKeys())if(!LEGACY_ROOT.contains(key))throw new IllegalArgumentException("Unknown empty Storage item owner");requireEmptyCaps(raw);if(raw.contains("tag")&&(!(raw.get("tag") instanceof CompoundTag empty)||!empty.isEmpty()))throw new IllegalArgumentException("Unsupported empty Storage item ownership");return ItemStack.EMPTY;}
  if(!raw.contains("id",Tag.TAG_STRING))throw new IllegalArgumentException("Unsupported legacy Storage item identity");
  String name=raw.getString("id");
  if(name.equals("vandorlabs:data_chip")||name.equals("vandorlabs:access_card")){var credential=NativeCredentialItems.readStack(raw,registries);if(credential.isEmpty())throw new IllegalArgumentException("Unsupported legacy Storage credential");return credential;}
  for(String key:raw.getAllKeys())if(!LEGACY_ROOT.contains(key))throw new IllegalArgumentException("Unsupported legacy Storage item field");
  requireEmptyCaps(raw);
  var id=ResourceLocation.tryParse(name);if(id==null)throw new IllegalArgumentException("Malformed legacy Storage item identity");
  var tag=raw.getCompound("tag");if(raw.contains("tag")&&!(raw.get("tag") instanceof CompoundTag))throw new IllegalArgumentException("Malformed legacy Storage item data");
  NonNullList<ItemStack> children=null;
  if(name.equals("minecraft:air")){if(raw.getShort("Damage")!=0||!tag.isEmpty())throw new IllegalArgumentException("Unsupported Source empty item data");return ItemStack.EMPTY;}
  if(tag.contains("EntityTag")||tag.contains("Inventory")||tag.contains("Items"))throw new IllegalArgumentException("Unsupported legacy embedded Storage owner");
  if(tag.contains("BlockEntityTag")){
   if(!(tag.get("BlockEntityTag") instanceof CompoundTag block))throw new IllegalArgumentException("Unsupported nested Storage block data");
   requireEmptyCaps(block);String inferred=container(name),blockId=block.contains("id",Tag.TAG_STRING)?block.getString("id"):inferred;if(inferred==null||!inferred.equals(blockId)||!CONTAINERS.contains(blockId))throw new IllegalArgumentException("Unsupported nested Storage block owner");for(String key:block.getAllKeys())if(!CONTAINER_FIELDS.contains(key))throw new IllegalArgumentException("Unknown nested Storage block ownership");
   if(block.contains("Items")){if(!(block.get("Items") instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed nested Storage inventory");int slots=capacity(blockId);children=NonNullList.withSize(slots,ItemStack.EMPTY);boolean[] seen=new boolean[slots];for(Tag value:list){var entry=(CompoundTag)value;if((entry.getByte("Slot")&255)>=slots)throw new IllegalArgumentException("Unsupported nested Storage inventory slot");int slot=entry.getByte("Slot")&255;var child=legacy(entry,registries,budget,depth+1);if(seen[slot]&&(ownershipBearing(children.get(slot))||ownershipBearing(child)))throw new IllegalArgumentException("Ambiguous duplicate nested inventory owner");children.set(slot,child);seen[slot]=true;}}
  }
  if(!id.getNamespace().equals("minecraft")){
   if(raw.getShort("Damage")!=0||!tag.isEmpty()||!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Unsupported tagged legacy Storage mod item");var item=BuiltInRegistries.ITEM.get(id);var stack=new ItemStack(item,raw.getByte("Count"));if(stack.isEmpty())throw new IllegalArgumentException("Unknown legacy Storage mod item");return stack;
  }
  if(!SOURCE_VANILLA.contains(name))throw new IllegalArgumentException("Unknown Source vanilla Storage item identity");
  var input=raw.copy();input.remove("Slot");input.remove("ForgeCaps");if(children!=null)input.getCompound("tag").getCompound("BlockEntityTag").remove("Items");var fixed=DataFixers.getDataFixer().update(References.ITEM_STACK,new Dynamic<>(NbtOps.INSTANCE,input),SOURCE_DATA_VERSION,SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();if(!(fixed instanceof CompoundTag encoded))throw new IllegalArgumentException("Unsupported fixed Storage item");var result=modern(encoded,registries);if(children!=null)result.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(children));return result;
 }
 private static int capacity(String id){return id.equals("minecraft:hopper")||id.equals("minecraft:brewing_stand")?5:id.equals("minecraft:furnace")?3:id.equals("minecraft:dropper")||id.equals("minecraft:dispenser")?9:27;}
 private static String container(String item){if(item.equals("minecraft:trapped_chest"))return "minecraft:chest";if(item.startsWith("minecraft:")&&item.endsWith("shulker_box"))return "minecraft:shulker_box";return CONTAINERS.contains(item)?item:null;}
 private static void nativeTree(CompoundTag raw,Budget budget,int depth){budget.enter(depth);for(String key:raw.getAllKeys())if(!MODERN_ROOT.contains(key))throw new IllegalArgumentException("Unsupported nested native Storage item field");var components=raw.getCompound("components");for(String key:List.of("minecraft:container","minecraft:bundle_contents","minecraft:charged_projectiles")){if(!components.contains(key))continue;if(!(components.get(key) instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed nested native Storage items");var seen=new HashSet<Integer>();for(Tag value:list){var child=(CompoundTag)value;if(key.equals("minecraft:container")){for(String field:child.getAllKeys())if(!field.equals("slot")&&!field.equals("item"))throw new IllegalArgumentException("Unknown native Storage slot owner");if(!seen.add(child.getInt("slot")))throw new IllegalArgumentException("Duplicate native nested inventory slot");String owner=container(raw.getString("id"));if(owner!=null&&(child.getInt("slot")<0||child.getInt("slot")>=capacity(owner)))throw new IllegalArgumentException("Unsupported native Storage container slot");if(!(child.get("item") instanceof CompoundTag item))throw new IllegalArgumentException("Malformed nested native Storage slot");child=item;}nativeTree(child,budget,depth+1);}}}
 public static ItemStack modern(CompoundTag raw,HolderLookup.Provider registries){return modern(raw,registries,new Budget(),0);}
 public static ItemStack modern(CompoundTag raw,HolderLookup.Provider registries,Budget budget){return modern(raw,registries,budget,0);}
 private static ItemStack modern(CompoundTag raw,HolderLookup.Provider registries,Budget budget,int depth){
  if(raw.isEmpty())return ItemStack.EMPTY;
  nativeTree(raw,budget,depth);
  for(String key:raw.getAllKeys())if(!MODERN_ROOT.contains(key))throw new IllegalArgumentException("Unsupported native Storage item field");
  var id=ResourceLocation.tryParse(raw.getString("id"));if(id==null||!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Unknown native Storage item identity");var stack=ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),raw).getOrThrow();if(stack.isEmpty())throw new IllegalArgumentException("Empty positive-count Storage item");return stack;
 }
 /** Applies separately to each item and each nested block envelope; never descends into opaque caps. */
 private static void requireEmptyCaps(CompoundTag tag){if(tag.contains("ForgeCaps")&&(!(tag.get("ForgeCaps") instanceof CompoundTag caps)||!caps.isEmpty()))throw new IllegalArgumentException("Unsupported Storage ForgeCaps owner");}
 public static CompoundTag write(ItemStack stack,HolderLookup.Provider registries){if(stack.isEmpty())return new CompoundTag();var encoded=ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();if(!(encoded instanceof CompoundTag tag))throw new IllegalArgumentException("Storage item encoding");return tag.copy();}
}
