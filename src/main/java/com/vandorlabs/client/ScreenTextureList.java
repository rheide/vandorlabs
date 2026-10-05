package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.FontRenderer;

/** Shared materials plus Screens; authored artwork retains its animation identity. */
final class ScreenTextureList {
    private final String[] ids;
    private final boolean input;
    private final HousingTextureList list;
    private boolean framed;
    ScreenTextureList(int x,int y,int width,int rows,String selected,String[] ids,String[] labels,boolean input) {
        this.ids=ids;this.input=input;
        int choice=ScreenHousingTextures.screenIndex((input?"console_inputs/":"")+selected+"_static");
        framed=isFramed(choice);
        list=new HousingTextureList(x,y,width,canonical(choice),rows,null,
                value->HousingTextureList.generalTexture(value) || artwork(value,input) && canonical(value)==value,
                value->"Screens".equals(ScreenHousingTextures.category(value))?(input?"Controls":"Screens"):ScreenHousingTextures.category(value),
                value->artwork(value,input)?HousingTextureList.name(value).replaceAll("\\b(Bare|Framed)\\s+",""):HousingTextureList.name(value));
        setFramed(framed);
    }
    static boolean artwork(int choice,boolean input) {
        com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
        return entry!=null && "Screens".equals(ScreenHousingTextures.category(choice))
                && entry.get("source").getAsString().startsWith("console_inputs/")==input;
    }
    ScreenTextureList visibleRows(int rows){list.visibleRows(rows);return this;}
    ScreenTextureList restore(int choice){
        if(choice>=0 || choice==com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE) {
            if(artwork(choice,input))framed=isFramed(choice);
            list.setSelected(canonical(choice));setFramed(framed);
        }
        return this;
    }
    static boolean isFramed(int choice){com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);return e!=null && (e.get("source").getAsString().matches(".*(?:^|/|_)framed_.*") || ModBlocks.DISPLAY_FRAMED_IDS.contains(e.get("source").getAsString().replaceFirst("_static$","")));}
    private int counterpart(int choice,boolean frame) {
        if(!artwork(choice,input))return choice;
        String source=ScreenHousingTextures.entry(choice).get("source").getAsString();
        String replaced=source.replace("_bare_","_framed_");
        if(source.startsWith("bare_"))replaced="framed_"+source.substring(5);
        if(source.startsWith("console_inputs/bare_"))replaced="console_inputs/framed_"+source.substring(20);
        if(!frame){replaced=source.replace("_framed_","_bare_");
            if(source.startsWith("framed_"))replaced="bare_"+source.substring(7);
            if(source.startsWith("console_inputs/framed_"))replaced="console_inputs/bare_"+source.substring(22);
        }
        int other=ScreenHousingTextures.screenIndex(replaced);return other==0?choice:other;
    }
    private int canonical(int choice){return counterpart(choice,false);}
    boolean hasPair(){return artwork(choice(),input) && counterpart(choice(),true)!=counterpart(choice(),false);}
    ScreenTextureList setFramed(boolean value) {
        framed=value;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(artwork(i,input) && canonical(i)==i)
            list.thumbnail(i,ScreenHousingTextures.fullTexture(counterpart(i,value)));
        return this;
    }
    ScreenTextureList custom(java.util.function.IntConsumer action){list.custom(action);return this;}
    ScreenTextureList redstone(net.minecraft.util.math.BlockPos pos,int slot){list.redstone(()->com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageSurfaceTexture(pos,slot,com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE)));return this;}
    boolean picked(){return list.picked();}
    int choice(){return list.selected();}
    private String source(){com.google.gson.JsonObject entry=ScreenHousingTextures.entry(counterpart(choice(),framed));return entry==null?"":entry.get("source").getAsString();}
    String selected(){
        String source=source();
        if(input){for(String id:ids)if(source.equals("console_inputs/"+id+"_static"))return id;}
        else for(ModBlocks.ScreenOption option:ModBlocks.SCREEN_OPTIONS)
            if(source.equals(option.bareId+"_static") || source.equals(option.framedId+"_static"))return option.bareId;
        return null; // General materials and Custom choices use a static face override.
    }
    boolean framed(){return hasPair()?framed:isFramed(choice());}
    boolean click(int x,int y,int button){return list.click(x,y,button);}
    boolean drag(int y){return list.drag(y);}
    void release(){list.release();}
    boolean wheel(int x,int y,int delta){return list.wheel(x,y,delta);}
    void draw(FontRenderer font,int x,int y){list.draw(font,x,y);}
}
