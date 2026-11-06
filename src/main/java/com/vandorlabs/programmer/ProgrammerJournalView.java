package com.vandorlabs.programmer;

import java.util.*;
import net.minecraft.item.ItemStack;

/** Bounded plain-text views shared by physical display rendering and navigation. */
public final class ProgrammerJournalView {
    public static final int COLUMNS=JournalText.COLUMNS,LINES=JournalText.LINES;
    public static final class Page {
        public final int entry;
        public final List<String> lines;
        private Page(int entry,List<String> lines){this.entry=entry;this.lines=Collections.unmodifiableList(new ArrayList<>(lines));}
    }
    public final String title,source,key;
    public final List<Page> pages;
    private ProgrammerJournalView(String title,String source,String[] entries){
        JournalText text=JournalText.layout(title,source,entries);this.title=text.title;this.source=text.source;this.key=text.key;
        List<Page> result=new ArrayList<Page>();for(JournalText.Page page:text.pages)result.add(new Page(page.entry,page.lines));pages=Collections.unmodifiableList(result);
    }

    public static ProgrammerJournalView from(ItemStack chip){
        if(!ChipJournal.journal(chip))return null;String[] entries=new String[ChipJournal.MAX_PAGES];for(int i=0;i<entries.length;i++)entries[i]=ChipJournal.page(chip,i);return new ProgrammerJournalView(ChipJournal.title(chip),"Chip",entries);
    }
    public static ProgrammerJournalView from(ProgrammerTile tile){
        if(tile.editableJournal())return new ProgrammerJournalView(tile.title,"Input",tile.pages.clone());
        ItemStack output=tile.getStackInSlot(2);if(!ChipJournal.journal(output))return null;
        String[] entries=new String[ChipJournal.MAX_PAGES];for(int i=0;i<entries.length;i++)entries[i]=ChipJournal.page(output,i);
        return new ProgrammerJournalView(ChipJournal.title(output),"Output",entries);
    }
}
