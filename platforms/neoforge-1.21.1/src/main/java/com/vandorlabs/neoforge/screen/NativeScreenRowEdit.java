package com.vandorlabs.neoforge.screen;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
/** Bounded editable choices only. Runtime levels, latch ownership and pulse deadlines cannot enter here. */
public record NativeScreenRowEdit(String title,List<Row> rows) {
 public NativeScreenRowEdit{if(!NativeScreenControls.validTitle(title))throw new IllegalArgumentException("Screen title");rows=List.copyOf(rows);if(rows.size()>NativeScreenControls.STANDALONE_ROWS)throw new IllegalArgumentException("Screen row count");title=title.trim();}
 public record Row(String label,ChannelList channels,boolean slider,boolean pulse,int min,int max){public Row{Objects.requireNonNull(channels);if(!NativeScreenControls.validLabel(label)||slider&&label.length()>NativeScreenControls.SLIDER_LABEL||slider&&pulse||min<0||min>max||max>15)throw new IllegalArgumentException("Screen row choices");label=label.trim();}}
}
