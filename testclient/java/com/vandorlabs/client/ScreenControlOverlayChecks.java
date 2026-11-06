package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.screen.ScreenControlOverlay;
import java.util.*;

/** Shared commands match original whole-renderer submissions, independent of the native drawing adapter. */
public final class ScreenControlOverlayChecks {
    private static final class Row implements ScreenControlOverlay.Row {
        final JsonObject data;Row(JsonObject data){this.data=data;}
        public String label(){return data.get("label").getAsString();}public boolean slider(){return data.get("slider").getAsBoolean();}public boolean pulse(){return data.get("pulse").getAsBoolean();}public boolean active(long now){return data.get("active").getAsBoolean();}public int selectedSegment(){return data.get("segment").getAsInt();}
    }
    private static final class Contents implements ScreenControlOverlay.Contents {
        final JsonObject data;final List<Row> rows=new ArrayList<Row>();Contents(JsonObject data){this.data=data;for(JsonElement row:data.getAsJsonArray("rows"))rows.add(new Row(row.getAsJsonObject()));}
        public String title(){return data.get("title").getAsString();}public List<Row> rows(){return rows;}
    }
    private static final class Sink implements ScreenControlOverlay.Sink {
        final Contents contents;final JsonArray vertices=new JsonArray(),text=new JsonArray();boolean textStage;Sink(Contents contents){this.contents=contents;}
        private void vertex(int x,int y,double z,int argb){vertices.add(Float.floatToRawIntBits((float)x));vertices.add(Float.floatToRawIntBits((float)y));vertices.add(Float.floatToRawIntBits((float)z));vertices.add((argb&0xff00ff00)|(argb>>16&255)|(argb&255)<<16);vertices.add(Float.floatToRawIntBits(.5F));vertices.add(Float.floatToRawIntBits(.5F));vertices.add(0x00f000f0);vertices.add(0x007f0000);}
        public void rectangle(int left,int top,int right,int bottom,double depth,int color){if(textStage)throw new AssertionError("Solids after text");vertex(left,bottom,depth,color);vertex(right,bottom,depth,color);vertex(right,top,depth,color);vertex(left,top,depth,color);}
        public void beginText(){textStage=true;}
        public void text(String value,int x,int y,int color,int width){if(!textStage)throw new AssertionError("Text before solid batch");String trimmed=value;if(width>=0){if(width==112&&value.equals(contents.title()))trimmed=contents.data.get("trimTitle").getAsString();else{boolean found=false;for(Row row:contents.rows)if(row.label().equals(value)){if(width!=(row.slider()?50:86))throw new AssertionError("Source trim budget");trimmed=row.data.get("trim").getAsString();found=true;break;}if(!found)throw new AssertionError("Unexpected Source font request");}}JsonObject command=new JsonObject();command.addProperty("text",trimmed);command.addProperty("x",x);command.addProperty("y",y);command.addProperty("color",color);text.add(command);}
    }
    private static final class Discard implements ScreenControlOverlay.Sink {long rectangles,text;public void rectangle(int l,int t,int r,int b,double z,int c){rectangles++;}public void text(String s,int x,int y,int c,int w){text++;}}
    public static void main(String[] args)throws Exception{
        JsonObject root;try(java.io.Reader reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(new java.io.FileInputStream("testclient/fixtures/screen-control-overlay-reference.json.gz")),"UTF-8")){root=new JsonParser().parse(reader).getAsJsonObject();}if(!root.get("sourceSha256").getAsString().equals("0b5c4fc23b6ab8543aa13885317f83d31f4d6e69b5b9305a5e706c56851818f9")||root.getAsJsonArray("profiles").size()!=1148)throw new AssertionError("Released row coverage");int styles=0,words=0,text=0;Contents full=null;
        for(JsonElement element:root.getAsJsonArray("styles")){JsonObject style=element.getAsJsonObject();Contents data=new Contents(style);int height=style.get("height").getAsInt();Sink sink=new Sink(data);ScreenControlOverlay.emit(data,height,height==64?4:8,112,sink);if(!sink.vertices.equals(style.get("data"))||!sink.text.equals(style.get("text")))throw new AssertionError("Released row command mismatch style="+styles+" vertices="+sink.vertices.equals(style.get("data"))+" text="+sink.text.equals(style.get("text")));styles++;words+=sink.vertices.size();text+=sink.text.size();if(height==128&&data.rows.size()==8)full=data;}
        if(styles!=14||full==null)throw new AssertionError("Full/half styles");Discard sink=new Discard();for(int n=0;n<12000;n++)ScreenControlOverlay.emit(full,128,8,112,sink);com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=meter.getThreadAllocatedBytes(thread);for(int n=0;n<4096;n++)ScreenControlOverlay.emit(full,128,8,112,sink);long allocated=meter.getThreadAllocatedBytes(thread)-before;if(allocated!=0)throw new AssertionError("Shared overlay allocation "+allocated);
        System.out.println("Screen control overlay PASS Source profiles=1148 styles="+styles+" words="+words+" text="+text+" solid-before-font; warm4096calls bytes="+allocated);
    }
}
