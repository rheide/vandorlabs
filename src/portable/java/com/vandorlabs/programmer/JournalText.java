package com.vandorlabs.programmer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plain text pagination shared by physical chip displays and both runtime adapters. */
public final class JournalText {
    public static final int TITLE_LIMIT=40, PAGE_LIMIT=512, MAX_ENTRIES=8, COLUMNS=50, LINES=10;
    public static final class Page {
        public final int entry;
        public final List<String> lines;
        private Page(int entry,List<String> lines){this.entry=entry;this.lines=Collections.unmodifiableList(new ArrayList<String>(lines));}
    }
    public final String title,source,key;
    public final List<Page> pages;
    private JournalText(String title,String source,String[] entries){
        this.title=title;this.source=source;StringBuilder signature=new StringBuilder(source).append('\0').append(title);List<Page> result=new ArrayList<Page>();int count=1;
        for(int i=0;i<entries.length;i++){signature.append('\0').append(entries[i]);if(!entries[i].isEmpty())count=i+1;}
        for(int entry=0;entry<count;entry++){
            List<String> lines=new ArrayList<String>();String[] paragraphs=entries[entry].split("\n",-1);
            for(String paragraph:paragraphs){String text=paragraph.replace("\r","").replace('\t',' ');if(text.isEmpty())lines.add("");for(int from=0;from<text.length();from+=COLUMNS)lines.add(text.substring(from,Math.min(from+COLUMNS,text.length())));}
            for(int first=0;first<lines.size();first+=LINES)result.add(new Page(entry,lines.subList(first,Math.min(first+LINES,lines.size()))));
        }
        key=signature.toString();pages=Collections.unmodifiableList(result);
    }
    /** Entries are already decoded by the version adapter; retain source signature and UTF-16 wrapping. */
    public static JournalText layout(String title,String source,String[] entries){return new JournalText(title,source,entries);}
    /** Source truncates before removing NULs, including when a surrogate straddles the boundary. */
    public static String bounded(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length())).replace("\u0000","");}
}
