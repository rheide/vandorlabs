package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.programmer.JournalText;
import java.util.*;
import net.minecraft.nbt.*;

/** Bounded immutable editor data only; original inventory/foreign envelopes stay with the host. */
public record NativeProgrammerDraft(boolean vehicle,boolean journal,String title,List<String> pages) {
 public NativeProgrammerDraft {
  title=JournalText.bounded(title,JournalText.TITLE_LIMIT);var bounded=new ArrayList<String>(JournalText.MAX_ENTRIES);
  for(int entry=0;entry<JournalText.MAX_ENTRIES;entry++)bounded.add(JournalText.bounded(entry<pages.size()?pages.get(entry):"",JournalText.PAGE_LIMIT));pages=List.copyOf(bounded);
 }
 public static NativeProgrammerDraft empty(){return new NativeProgrammerDraft(false,false,"",List.of());}
 public static NativeProgrammerDraft read(CompoundTag tag,boolean chipInput){var raw=tag.getList("Pages",Tag.TAG_STRING);var pages=new ArrayList<String>(JournalText.MAX_ENTRIES);for(int entry=0;entry<JournalText.MAX_ENTRIES;entry++)pages.add(entry<raw.size()?raw.getString(entry):"");return new NativeProgrammerDraft(chipInput&&tag.getBoolean("Vehicle"),chipInput&&tag.getBoolean("Journal"),tag.getString("Title"),pages);}
 public boolean available(){return !vehicle;}
 public String[] entries(){return pages.toArray(String[]::new);}
 /** Writes known draft fields without claiming ownership of unrelated saved data. */
 public void write(CompoundTag tag){if(vehicle)tag.putBoolean("Vehicle",true);else tag.remove("Vehicle");tag.putBoolean("Journal",journal);tag.putString("Title",title);var list=new ListTag();for(String page:pages)list.add(StringTag.valueOf(page));tag.put("Pages",list);}
}
