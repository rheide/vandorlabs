package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.programmer.JournalText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Current chip text overrides the retained import envelope; decoding never mutates either owner. */
public final class NativeChipJournal {
    private NativeChipJournal(){}
    private static CompoundTag data(ItemStack stack){
        var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return root.contains("ChipType")?root:root.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag");
    }
    public static boolean journal(ItemStack stack){return !stack.isEmpty()&&stack.is(NativeCredentialItems.DATA_CHIP.get())&&"journal".equals(data(stack).getString("ChipType"));}
    public static boolean master(ItemStack stack){return NativeCredentialItems.accepts(stack,true)&&NativeCredentialItems.company(stack)>0&&!journal(stack)&&!"vehicle".equals(data(stack).getString("ChipType"));}
    public static JournalText view(ItemStack stack){
        return viewText(textData(stack));
    }
    /** Only authored journal text may leave the inventory owner; arbitrary item fields are excluded. */
    public static CompoundTag textData(ItemStack stack){
        if(stack.isEmpty()||!stack.is(NativeCredentialItems.DATA_CHIP.get()))return null;var tag=data(stack);if(!"journal".equals(tag.getString("ChipType")))return null;
        var result=new CompoundTag();result.putString("ChipType","journal");result.putString("JournalTitle",JournalText.bounded(tag.getString("JournalTitle"),JournalText.TITLE_LIMIT));var source=tag.getList("JournalPages",Tag.TAG_STRING);var pages=new ListTag();for(int entry=0;entry<JournalText.MAX_ENTRIES;entry++)pages.add(StringTag.valueOf(JournalText.bounded(entry<source.size()?source.getString(entry):"",JournalText.PAGE_LIMIT)));result.put("JournalPages",pages);return result;
    }
    public static JournalText viewText(CompoundTag tag){
        if(tag==null||!"journal".equals(tag.getString("ChipType")))return null;
        var list=tag.getList("JournalPages",Tag.TAG_STRING);var entries=new String[JournalText.MAX_ENTRIES];for(int entry=0;entry<entries.length;entry++)entries[entry]=JournalText.bounded(entry<list.size()?list.getString(entry):"",JournalText.PAGE_LIMIT);
        return JournalText.layout(JournalText.bounded(tag.getString("JournalTitle"),JournalText.TITLE_LIMIT),"Chip",entries);
    }
    /** Retain company/components and opaque import data while publishing one complete native payload. */
    public static void write(ItemStack stack,boolean journal,String title,String[] entries){
        if(stack.isEmpty()||!stack.is(NativeCredentialItems.DATA_CHIP.get()))throw new IllegalArgumentException("Journal requires a data chip");
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();for(String key:new String[]{"VehicleDesign","VehicleDesignCodec","VehicleName","VehicleShape","JournalTitle","JournalPages"})tag.remove(key);
        tag.putString("ChipType",journal?"journal":"master");if(journal){tag.putString("JournalTitle",JournalText.bounded(title,JournalText.TITLE_LIMIT));var pages=new ListTag();for(int entry=0;entry<JournalText.MAX_ENTRIES;entry++)pages.add(StringTag.valueOf(JournalText.bounded(entry<entries.length?entries[entry]:"",JournalText.PAGE_LIMIT)));tag.put("JournalPages",pages);}
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
}
