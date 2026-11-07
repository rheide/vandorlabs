package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.programmer.JournalText;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Prepared bounded Input/Output display. No physical stack, platform or arbitrary journal tags. */
public final class NativeProgrammerJournalView {
    public final JournalText journal;
    public final int page;
    private final NativeProgrammerDraft contents;
    private NativeProgrammerJournalView(JournalText journal,NativeProgrammerDraft contents,int page){this.journal=Objects.requireNonNull(journal);this.contents=contents;this.page=Math.max(0,Math.min(journal.pages.size()-1,page));}
    private static NativeProgrammerJournalView of(String source,NativeProgrammerDraft contents){return new NativeProgrammerJournalView(JournalText.layout(contents.title(),source,contents.entries()),contents,0);}
    public static NativeProgrammerJournalView prepare(ItemStack input,ItemStack output,NativeProgrammerDraft draft){
        if(draft.vehicle()||NativeChipJournal.vehicle(input)||NativeChipJournal.vehicle(output))return null;
        if(input.is(NativeCredentialItems.DATA_CHIP.get())&&draft.journal())return of("Input",draft);
        var text=NativeChipJournal.textData(output);if(text==null)return null;
        var prepared=NativeChipJournal.viewText(text,"Output");var raw=text.getList("JournalPages",Tag.TAG_STRING);var pages=new ArrayList<String>(8);for(int entry=0;entry<8;entry++)pages.add(raw.getString(entry));
        return new NativeProgrammerJournalView(prepared,new NativeProgrammerDraft(false,true,prepared.title,pages),0);
    }
    public NativeProgrammerJournalView withPage(int page){int bounded=Math.max(0,Math.min(journal.pages.size()-1,page));return bounded==this.page?this:new NativeProgrammerJournalView(journal,contents,bounded);}
    public CompoundTag write(){var result=new CompoundTag();contents.write(result);result.putString("Source",journal.source);result.putInt("Page",page);return result;}
    public static NativeProgrammerJournalView read(CompoundTag tag){
        for(String key:tag.getAllKeys())if(!Set.of("Source","Page","Journal","Title","Pages").contains(key))throw new IllegalArgumentException("Unknown Programmer journal field");
        if(!tag.contains("Source",Tag.TAG_STRING)||!Set.of("Input","Output").contains(tag.getString("Source"))||!tag.contains("Page",Tag.TAG_INT)||!tag.contains("Journal",Tag.TAG_BYTE)||tag.getByte("Journal")!=1||!tag.contains("Title",Tag.TAG_STRING)||tag.getString("Title").length()>40||!(tag.get("Pages") instanceof ListTag pages)||pages.size()!=8||pages.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Programmer journal projection bounds");
        for(int entry=0;entry<8;entry++)if(pages.getString(entry).length()>512)throw new IllegalArgumentException("Programmer journal entry length");
        return of(tag.getString("Source"),NativeProgrammerDraft.read(tag,true)).withPage(tag.getInt("Page"));
    }
}
