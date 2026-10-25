package com.vandorlabs.programmer;

import java.util.*;
import net.minecraft.item.ItemStack;

/** Bounded plain-text views shared by physical display rendering and navigation. */
public final class ProgrammerJournalView {
    public static final int COLUMNS=50,LINES=10;
    public static final class Page {
        public final int entry;
        public final List<String> lines;
        private Page(int entry,List<String> lines){this.entry=entry;this.lines=Collections.unmodifiableList(new ArrayList<>(lines));}
    }
    public final String title,source,key;
    public final List<Page> pages;
    private ProgrammerJournalView(String title,String source,String[] entries){
        this.title=title;this.source=source;StringBuilder signature=new StringBuilder(source).append('\0').append(title);List<Page> result=new ArrayList<>();int count=1;
        for(int i=0;i<entries.length;i++){signature.append('\0').append(entries[i]);if(!entries[i].isEmpty())count=i+1;}
        for(int entry=0;entry<count;entry++){
            List<String> lines=new ArrayList<>();String[] paragraphs=entries[entry].split("\\n",-1);
            for(String paragraph:paragraphs){String text=paragraph.replace("\r","").replace('\t',' ');if(text.isEmpty())lines.add("");for(int from=0;from<text.length();from+=COLUMNS)lines.add(text.substring(from,Math.min(from+COLUMNS,text.length())));}
            for(int first=0;first<lines.size();first+=LINES)result.add(new Page(entry,lines.subList(first,Math.min(first+LINES,lines.size()))));
        }
        key=signature.toString();pages=Collections.unmodifiableList(result);
    }
    public static ProgrammerJournalView from(ProgrammerTile tile){
        if(tile.editableJournal())return new ProgrammerJournalView(tile.title,"Input",tile.pages.clone());
        ItemStack output=tile.getStackInSlot(2);if(!ChipJournal.journal(output))return null;
        String[] entries=new String[ChipJournal.MAX_PAGES];for(int i=0;i<entries.length;i++)entries[i]=ChipJournal.page(output,i);
        return new ProgrammerJournalView(ChipJournal.title(output),"Output",entries);
    }
}
