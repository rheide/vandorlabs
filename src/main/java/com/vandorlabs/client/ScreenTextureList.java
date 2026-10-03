package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.FontRenderer;

/** Shared materials plus Screens; authored artwork retains its animation identity. */
final class ScreenTextureList {
    private final String[] ids;
    private final boolean input;
    private final HousingTextureList list;
    ScreenTextureList(int x,int y,int width,int rows,String selected,String[] ids,String[] labels,boolean input) {
        this.ids=ids;this.input=input;
        int choice=ScreenHousingTextures.screenIndex((input?"console_inputs/":"")+selected+"_static");
        list=new HousingTextureList(x,y,width,choice,rows,null,
                value->HousingTextureList.generalTexture(value) || artwork(value,input),
                value->"Screens".equals(ScreenHousingTextures.category(value))?(input?"Controls":"Screens"):ScreenHousingTextures.category(value));
    }
    static boolean artwork(int choice,boolean input) {
        com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
        return entry!=null && "Screens".equals(ScreenHousingTextures.category(choice))
                && entry.get("source").getAsString().startsWith("console_inputs/")==input;
    }
    ScreenTextureList visibleRows(int rows){list.visibleRows(rows);return this;}
    ScreenTextureList restore(int choice){if(choice>=0)list.setSelected(choice);return this;}
    ScreenTextureList custom(java.util.function.IntConsumer action){list.custom(action);return this;}
    boolean picked(){return list.picked();}
    int choice(){return list.selected();}
    private String source(){com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice());return entry==null?"":entry.get("source").getAsString();}
    String selected(){
        String source=source();
        if(input){for(String id:ids)if(source.equals("console_inputs/"+id+"_static"))return id;}
        else for(ModBlocks.ScreenOption option:ModBlocks.SCREEN_OPTIONS)
            if(source.equals(option.bareId+"_static") || source.equals(option.framedId+"_static"))return option.bareId;
        return null; // General materials and Custom choices use a static face override.
    }
    boolean framed(){
        String source=source();
        for(ModBlocks.ScreenOption option:ModBlocks.SCREEN_OPTIONS)
            if(source.equals(option.framedId+"_static"))return option.hasPair() || ModBlocks.DISPLAY_FRAMED_IDS.contains(option.framedId);
        return false;
    }
    boolean click(int x,int y,int button){return list.click(x,y,button);}
    boolean drag(int y){return list.drag(y);}
    void release(){list.release();}
    boolean wheel(int x,int y,int delta){return list.wheel(x,y,delta);}
    void draw(FontRenderer font,int x,int y){list.draw(font,x,y);}
}
