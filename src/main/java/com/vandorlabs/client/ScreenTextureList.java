package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import java.util.*;

/** Categorized native choices: selects the original animation identity, never a static override. */
final class ScreenTextureList {
    private final String[] ids;
    private final HousingTextureList list;
    ScreenTextureList(int x,int y,int width,int rows,String selected,String[] ids,String[] labels,boolean input) {
        this.ids=ids;List<HousingTextureList.Option> options=new ArrayList<>();int current=0;
        for(int i=0;i<ids.length;i++) {
            if(ids[i].equals(selected))current=i;
            String source=(input?"console_inputs/":"")+ids[i]+"_static";
            String label=labels==null?I18n.format("gui.vandorlabs.console.input."+ids[i]):labels[i];
            String category=input?"Controls":ids[i].contains("viewscreen")||ids[i].contains("cruiser")?"Viewscreens":"Displays";
            options.add(new HousingTextureList.Option(i,label,category,ScreenHousingTextures.fullTexture(ScreenHousingTextures.screenIndex(source))));
        }
        list=new HousingTextureList(x,y,width,current,rows,options);
    }
    boolean picked(){return list.picked();}
    String selected(){return ids[list.selected()];}
    boolean click(int x,int y,int button){return list.click(x,y,button);}
    boolean drag(int y){return list.drag(y);}
    void release(){list.release();}
    boolean wheel(int x,int y,int delta){return list.wheel(x,y,delta);}
    void draw(FontRenderer font,int x,int y){list.draw(font,x,y);}
}
