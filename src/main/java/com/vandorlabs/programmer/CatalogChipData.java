package com.vandorlabs.programmer;
import com.vandorlabs.credentials.CatalogChipPolicy;
import com.vandorlabs.items.ModItems;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
/** Explicit versioned buildable designs on each physical chip. Unknown data is never replaced while reading. */
public final class CatalogChipData {
 public static boolean catalog(ItemStack stack){return ChipJournal.isChip(stack)&&stack.hasTagCompound()&&"catalog".equals(stack.getTagCompound().getString("ChipType"));}
 public static List<String> designs(ItemStack stack,String machine){if(!catalog(stack)||!CatalogChipPolicy.machine(machine))return Collections.emptyList();NBTTagCompound payload=stack.getTagCompound().getCompoundTag("Catalog");if(payload.getInteger("Version")!=CatalogChipPolicy.VERSION)return Collections.emptyList();NBTTagCompound designs=payload.getCompoundTag("Designs");if(!designs.hasKey(machine,9))return Collections.emptyList();NBTTagList list=designs.getTagList(machine,8);if(list.tagCount()>CatalogChipPolicy.MAX_DESIGNS)return Collections.emptyList();LinkedHashSet<String> result=new LinkedHashSet<>();for(int i=0;i<list.tagCount();i++){String id=list.getStringTagAt(i);if(!CatalogChipPolicy.valid(id))return Collections.emptyList();result.add(id);}return Collections.unmodifiableList(new ArrayList<>(result));}
 public static List<String> designs(ItemStack stack){LinkedHashSet<String> all=new LinkedHashSet<>();for(String machine:CatalogChipPolicy.MACHINES)all.addAll(designs(stack,machine));return Collections.unmodifiableList(new ArrayList<>(all));}
 public static ItemStack masterStack(){ItemStack stack=new ItemStack(ModItems.DATA_CHIP);NBTTagCompound tag=new NBTTagCompound(),payload=new NBTTagCompound(),designs=new NBTTagCompound();tag.setString("ChipType","catalog");tag.setString("CatalogName","Master");payload.setInteger("Version",CatalogChipPolicy.VERSION);for(String machine:CatalogChipPolicy.MACHINES){NBTTagList list=new NBTTagList();for(String id:CatalogChipPolicy.master(machine))list.appendTag(new NBTTagString(id));designs.setTag(machine,list);}payload.setTag("Designs",designs);tag.setTag("Catalog",payload);stack.setTagCompound(tag);return stack;}
 private CatalogChipData(){}
}
