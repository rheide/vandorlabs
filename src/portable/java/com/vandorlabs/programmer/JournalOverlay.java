package com.vandorlabs.programmer;

import java.util.List;
import java.util.Objects;

/** Standalone journal artwork in authored 320-pixel coordinates. Inventory, fonts and logos stay in adapters. */
public final class JournalOverlay {
    public static final int WIDTH=320,MIN_HEIGHT=136,PREVIOUS_LEFT=6,PREVIOUS_RIGHT=76,NEXT_LEFT=244,NEXT_RIGHT=314;
    public static final class Contents {
        public final boolean occupied,master;public final int company,page,pages;public final String displayName,title,source,counter;public final List<String> lines;
        public Contents(boolean occupied,boolean master,int company,String displayName,String title,String source,List<String> lines,int page,int pages){
            if(company<0||company>6||pages<0||pages>416||pages>0&&(page<0||page>=pages)||pages==0&&page!=0||master&&(!occupied||company==0||pages>0))throw new IllegalArgumentException("Journal canvas contents");
            this.occupied=occupied;this.master=master;this.company=company;this.displayName=Objects.requireNonNull(displayName);this.title=Objects.requireNonNull(title);this.source=Objects.requireNonNull(source);this.lines=java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(lines));this.page=page;this.pages=pages;this.counter=pages>0?source+"  "+(page+1)+" / "+pages:"";
        }
    }
    public interface Sink {
        void rectangle(int left,int top,int right,int bottom,double depth,int argb);
        default void beginText(){}
        void fitted(String value,int x,int y,int width,int rgb);
        void text(String value,int x,int y,int rgb);
        void centered(String value,int center,int y,int rgb);
        /** False preserves the released text fallback when artwork is unavailable. */
        boolean companyLogo(int company,int height);
    }
    private JournalOverlay(){}
    public static void emit(Contents contents,int height,Sink sink){
        if(height<MIN_HEIGHT)throw new IllegalArgumentException("Journal canvas height");boolean journal=contents.pages>0;
        sink.rectangle(0,0,WIDTH,height,0,0xff000000);sink.rectangle(3,3,317,20,.01,0xff152330);
        if(journal){sink.rectangle(PREVIOUS_LEFT,height-20,PREVIOUS_RIGHT,height-4,.01,contents.page>0?0xff23465a:0xff121b23);sink.rectangle(NEXT_LEFT,height-20,NEXT_RIGHT,height-4,.01,contents.page+1<contents.pages?0xff23465a:0xff121b23);}
        sink.beginText();
        if(!journal){if(contents.master&&sink.companyLogo(contents.company,height))return;sink.fitted(!contents.occupied?"Insert a Data Chip":contents.master?"Master Chip":"Blank Data Chip",7,8,306,0xc8eeff);if(contents.occupied)sink.fitted(contents.displayName,8,48,304,0xe2edf5);return;}
        sink.fitted(contents.title.isEmpty()?"Untitled Journal":contents.title,7,8,306,0xc8eeff);
        for(int line=0;line<contents.lines.size();line++)sink.fitted(contents.lines.get(line),8,23+line*9,304,0xe2edf5);
        sink.text("< Previous",12,height-16,contents.page>0?0xc8eeff:0x697987);sink.text("Next >",270,height-16,contents.page+1<contents.pages?0xc8eeff:0x697987);
        sink.centered(contents.counter,160,height-16,0xa6bfd0);
    }
}
