package com.vandorlabs.screen;

import java.util.List;

/** Standalone control artwork in authored 128-pixel coordinates; no font, texture, world or GPU APIs. */
public final class ScreenControlOverlay {
    public static final int TITLE_WIDTH=112,LABEL_WIDTH=86;
    public interface Row {String label();boolean slider();boolean pulse();boolean active(long now);int selectedSegment();default int statusLevel(){return -1;}}
    public interface Contents {String title();List<? extends Row> rows();}
    public interface Sink {
        void rectangle(int left,int top,int right,int bottom,double depth,int argb);
        default void beginText(){}
        /** A negative width means untrimmed; adapters use their actual font metrics. */
        void text(String value,int x,int y,int rgb,int width);
    }
    public static void emit(Contents contents,int height,int maxRows,long now,Sink sink){
        if(height!=64&&height!=128||maxRows<0||maxRows>8)throw new IllegalArgumentException("Standalone control canvas");List<? extends Row> rows=contents.rows();int count=Math.min(rows.size(),maxRows),top=height==64?ScreenControlLayout.HALF_ROW_TOP:ScreenControlLayout.FULL_ROW_TOP;boolean header=height>64;
        sink.rectangle(0,0,128,height,0,0xFF0A141D);if(header)sink.rectangle(3,3,125,20,.01,0xFF23465A);
        for(int i=0;i<count;i++){
            Row row=rows.get(i);int y=top+i*ScreenControlLayout.ROW_HEIGHT;boolean active=row.active(now);
            sink.rectangle(ScreenControlLayout.ROW_LEFT,y,ScreenControlLayout.ROW_RIGHT,y+10,.01,!row.slider()&&active?0xFF174C3B:0xFF1C2B37);
            if(row.statusLevel()>=0)battery(sink,y,row.statusLevel());
            else if(row.slider())for(int segment=0;segment<ScreenControlLayout.SLIDER_STEPS;segment++){
                int width=ScreenControlLayout.SLIDER_RIGHT-ScreenControlLayout.SLIDER_LEFT,left=ScreenControlLayout.SLIDER_LEFT+width*segment/ScreenControlLayout.SLIDER_STEPS,right=ScreenControlLayout.SLIDER_LEFT+width*(segment+1)/ScreenControlLayout.SLIDER_STEPS;
                sink.rectangle(left,y,right,y+10,.02,0xFF91B5C8);sink.rectangle(left+1,y+1,right-1,y+9,.03,row.selectedSegment()==segment?(segment==0?0xFFFFB33B:0xFF38DDE4):(segment==0?0xFF4D3820:0xFF183B46));
            }else sink.rectangle(ScreenControlLayout.BUTTON_LEFT,y,ScreenControlLayout.BUTTON_RIGHT,y+10,.02,active?0xFF30C58A:0xFF45576A);
        }
        sink.beginText();if(header)sink.text(contents.title(),8,8,0xC8EEFF,TITLE_WIDTH);
        if(rows.isEmpty()){sink.text("No controls",8,40,0xB8C8D0,-1);sink.text("Configure to add",8,54,0x748C9C,-1);}
        for(int i=0;i<count;i++){
            Row row=rows.get(i);int y=top+i*ScreenControlLayout.ROW_HEIGHT;boolean active=row.active(now);
            sink.text(row.label(),8,y+1,active?0xDEFFF0:0xB8C8D8,row.slider()?ScreenControlLayout.SLIDER_LABEL_WIDTH:LABEL_WIDTH);
            if(!row.slider()&&row.statusLevel()<0)sink.text(active?"ON":row.pulse()?"GO":"OFF",100,y+1,active?0x082419:0xE0E8EF,-1);
        }
    }
    /** Cockpit faces divide their authored square-pixel height evenly among installed rows. */
    public static void emitCockpit(Contents contents,int height,long now,Sink sink){
        if(height<10||height>128||contents.rows().size()>6)throw new IllegalArgumentException("Cockpit control canvas");
        List<? extends Row> rows=contents.rows();
        sink.rectangle(0,0,128,height,0,0xFF0A141D);
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i);
            int top=(i*height/rows.size()+(i+1)*height/rows.size()-10)/2;
            boolean active=row.active(now);
            sink.rectangle(6,top,122,top+10,.01,!row.slider()&&active?0xFF174C3B:0xFF1C2B37);
            if(row.statusLevel()>=0)battery(sink,top,row.statusLevel());
            else if(row.slider()){
                for(int step=0;step<4;step++){
                    int left=ScreenControlLayout.SLIDER_LEFT+15*step;
                    sink.rectangle(left,top,left+15,top+10,.02,0xFF91B5C8);
                    sink.rectangle(left+1,top+1,left+14,top+9,.03,row.selectedSegment()==step?0xFF38DDE4:0xFF183B46);
                }
            }else sink.rectangle(98,top,120,top+10,.02,active?0xFF30C58A:0xFF45576A);
        }
        sink.beginText();
        if(rows.isEmpty()){
            sink.text("No controls",8,height/2-5,0xB8C8D0,-1);
            return;
        }
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i);
            int y=(i*height/rows.size()+(i+1)*height/rows.size()-10)/2+1;
            boolean active=row.active(now);
            sink.text(row.label(),8,y,active?0xDEFFF0:0xB8C8D8,row.slider()?ScreenControlLayout.SLIDER_LABEL_WIDTH:LABEL_WIDTH);
            if(!row.slider()&&row.statusLevel()<0)sink.text(active?"ON":row.pulse()?"GO":"OFF",100,y,active?0x082419:0xE0E8EF,-1);
        }
    }
    private static void battery(Sink sink,int y,int level){
        sink.rectangle(96,y,119,y+10,.02,0xFF91B5C8);
        sink.rectangle(119,y+3,121,y+7,.02,0xFF91B5C8);
        sink.rectangle(97,y+1,118,y+9,.03,0xFF142732);
        for(int i=0;i<5;i++){
            int left=98+4*i;
            sink.rectangle(left,y+2,left+3,y+8,.04,level>3*i?0xFF46D4B5:0xFF33434D);
        }
    }
    private ScreenControlOverlay(){}
}
