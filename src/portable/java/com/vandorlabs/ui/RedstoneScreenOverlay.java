package com.vandorlabs.ui;

import java.util.*;

/** Ordered solid and text submissions; drawing and font measurement stay in adapters. */
public final class RedstoneScreenOverlay {
    public static final int LABEL_WIDTH=86,TITLE_WIDTH=112,SLIDER_LABEL_WIDTH=50;
    public interface RowAccess<R> {
        String label(R row);boolean slider(R row);boolean pulse(R row);boolean active(R row);int segments(R row);int selected(R row);
    }
    /** Retains only strings/primitives and the immutable plan, never a row, tile or world. */
    public static final class Cache {
        private final String[] labels=new String[8];
        private final int[] flags=new int[8],segments=new int[8],selected=new int[8];
        private int count=-1,height,rowTop,maxRows;
        private boolean header;
        private String title;
        private RedstoneScreenOverlay plan;
        private long builds,rowsCreated,rectsCreated,textsCreated;
        public <R> RedstoneScreenOverlay plan(RedstoneScreenLayout layout,String title,List<R> rows,RowAccess<? super R> access){
            int count=Math.min(layout.maxRows,rows.size());boolean changed=plan==null||this.count!=count||height!=layout.height||rowTop!=layout.rowTop||maxRows!=layout.maxRows||header!=layout.header||!Objects.equals(this.title,title);
            for(int i=0;i<count;i++){
                R row=rows.get(i);String label=access.label(row);int flags=(access.slider(row)?1:0)|(access.pulse(row)?2:0)|(access.active(row)?4:0),segments=access.segments(row),selected=access.selected(row);
                changed|=!Objects.equals(labels[i],label)||this.flags[i]!=flags||this.segments[i]!=segments||this.selected[i]!=selected;
                labels[i]=label;this.flags[i]=flags;this.segments[i]=segments;this.selected[i]=selected;
            }
            if(changed){
                List<Row> snapshot=new ArrayList<>(count);for(int i=0;i<count;i++)snapshot.add(new Row(labels[i],(flags[i]&1)!=0,(flags[i]&2)!=0,(flags[i]&4)!=0,segments[i],selected[i]));
                for(int i=count;i<labels.length;i++)labels[i]=null;
                plan=new RedstoneScreenOverlay(layout,title,snapshot);builds++;rowsCreated+=count;rectsCreated+=plan.solids.size();textsCreated+=plan.text.size();
                this.count=count;this.title=title;height=layout.height;rowTop=layout.rowTop;maxRows=layout.maxRows;header=layout.header;
            }
            return plan;
        }
        public long builds(){return builds;}public long rowsCreated(){return rowsCreated;}public long rectsCreated(){return rectsCreated;}public long textsCreated(){return textsCreated;}
    }
    public static final class Row {
        public final String label;public final boolean slider,pulse,active;public final int segments,selected;
        public Row(String label,boolean slider,boolean pulse,boolean active,int segments,int selected){this.label=label;this.slider=slider;this.pulse=pulse;this.active=active;this.segments=segments;this.selected=selected;}
    }
    public static final class Rect {
        public final int left,top,right,bottom,color;public final double layer;
        private Rect(int left,int top,int right,int bottom,double layer,int color){this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.layer=layer;this.color=color;}
    }
    public static final class Text {
        public final String value;public final int x,y,color,maxWidth;
        private Text(String value,int x,int y,int color,int maxWidth){this.value=value;this.x=x;this.y=y;this.color=color;this.maxWidth=maxWidth;}
    }
    public final List<Rect> solids;
    public final List<Text> text;
    public RedstoneScreenOverlay(RedstoneScreenLayout layout,String title,List<Row> rows){
        List<Rect> solids=new ArrayList<>();List<Text> text=new ArrayList<>();
        solids.add(new Rect(0,0,128,layout.height,0,0xFF0A141D));if(layout.header){solids.add(new Rect(3,3,125,20,.01,0xFF23465A));text.add(new Text(title,8,8,0xC8EEFF,TITLE_WIDTH));}
        if(rows.isEmpty()){text.add(new Text("No controls",8,40,0xB8C8D0,0));text.add(new Text("Configure to add",8,54,0x748C9C,0));}
        for(int i=0;i<Math.min(rows.size(),layout.maxRows);i++){
            Row row=rows.get(i);int y=layout.rowTop+i*12;
            solids.add(new Rect(6,y,122,y+10,.01,!row.slider&&row.active?0xFF174C3B:0xFF1C2B37));
            if(row.slider)for(int segment=0;segment<row.segments;segment++){
                int left=60+60*segment/row.segments,right=60+60*(segment+1)/row.segments;
                solids.add(new Rect(left,y,right,y+10,.02,0xFF91B5C8));solids.add(new Rect(left+1,y+1,right-1,y+9,.03,row.selected==segment?(segment==0?0xFFFFB33B:0xFF38DDE4):(segment==0?0xFF4D3820:0xFF183B46)));
            }else solids.add(new Rect(98,y,120,y+10,.02,row.active?0xFF30C58A:0xFF45576A));
            text.add(new Text(row.label,8,y+1,row.active?0xDEFFF0:0xB8C8D8,row.slider?SLIDER_LABEL_WIDTH:LABEL_WIDTH));
            if(!row.slider)text.add(new Text(row.active?"ON":row.pulse?"GO":"OFF",100,y+1,row.active?0x082419:0xE0E8EF,0));
        }
        this.solids=Collections.unmodifiableList(solids);this.text=Collections.unmodifiableList(text);
    }
}
