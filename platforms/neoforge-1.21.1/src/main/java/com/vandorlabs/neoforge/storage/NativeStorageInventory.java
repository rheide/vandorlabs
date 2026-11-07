package com.vandorlabs.neoforge.storage;

import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Stage all slots before installation. This class never publishes, clears or mutates the previous owner. */
public final class NativeStorageInventory {
 public static final String SCHEMA="NativeStorageInventorySchema",DATA_VERSION="NativeStorageItemDataVersion";
 private NativeStorageInventory(){}
 public static NonNullList<ItemStack> empty(){return NonNullList.withSize(NativeStorageSettings.SLOTS,ItemStack.EMPTY);}
 public static boolean sourcePreservesItems(CompoundTag tag){return !tag.contains(SCHEMA)&&!tag.contains("Items",Tag.TAG_LIST);}
 public static NonNullList<ItemStack> read(CompoundTag tag,HolderLookup.Provider registries,List<ItemStack> previous){
  if(previous.size()!=NativeStorageSettings.SLOTS)throw new IllegalArgumentException("Storage previous slot count");boolean modern=tag.contains(SCHEMA);if(modern&&(!tag.contains(SCHEMA,Tag.TAG_INT)||tag.getInt(SCHEMA)!=1||!tag.contains(DATA_VERSION,Tag.TAG_INT)||tag.getInt(DATA_VERSION)!=SharedConstants.getCurrentVersion().getDataVersion().getVersion()))throw new IllegalArgumentException("Unsupported Storage inventory schema");
  var staged=empty();if(!tag.contains("Items",Tag.TAG_LIST)){if(modern)throw new IllegalArgumentException("Incomplete native Storage inventory");for(int i=0;i<staged.size();i++)staged.set(i,previous.get(i));return staged;}
  var raw=(ListTag)tag.get("Items");if(!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND){if(modern)throw new IllegalArgumentException("Unsupported native Storage list");return staged;}if(raw.size()>NativeInventoryItems.MAX_NESTED_ITEMS)throw new IllegalArgumentException("Storage item entry bound");var budget=new NativeInventoryItems.Budget();boolean[] seen=new boolean[NativeStorageSettings.SLOTS];
  for(Tag value:raw){var entry=(CompoundTag)value;int slot=modern?entry.getInt("Slot"):entry.getByte("Slot")&255;if(modern&&(!entry.contains("Slot",Tag.TAG_BYTE)&&!entry.contains("Slot",Tag.TAG_INT)||slot<0||slot>=staged.size()||seen[slot]))throw new IllegalArgumentException("Unsupported native Storage slot");ItemStack stack;if(modern){var item=entry.copy();item.remove("Slot");stack=NativeInventoryItems.modern(item,registries,budget);}else stack=NativeInventoryItems.legacy(entry,registries,budget);if(slot>=staged.size()){if(!stack.isEmpty())throw new IllegalArgumentException("Unsupported owned legacy Storage slot");continue;}if(!modern&&seen[slot]&&(NativeInventoryItems.ownershipBearing(staged.get(slot))||NativeInventoryItems.ownershipBearing(stack)))throw new IllegalArgumentException("Ambiguous duplicate Storage inventory owner");staged.set(slot,stack);seen[slot]=true;}
  return staged;
 }
 public static void write(CompoundTag tag,List<ItemStack> items,HolderLookup.Provider registries){if(items.size()!=NativeStorageSettings.SLOTS)throw new IllegalArgumentException("Storage slot count");var list=new ListTag();for(int i=0;i<items.size();i++)if(!items.get(i).isEmpty()){var entry=NativeInventoryItems.write(items.get(i),registries);entry.putByte("Slot",(byte)i);list.add(entry);}tag.putInt(SCHEMA,1);tag.putInt(DATA_VERSION,SharedConstants.getCurrentVersion().getDataVersion().getVersion());tag.put("Items",list);}
}
