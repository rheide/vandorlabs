package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import java.util.*;

/** One categorized, scrollable artwork picker with atlas thumbnails. */
final class HousingTextureList {
    private static final int ROW_HEIGHT=12;
    private final int x,y,width,count;
    private final List<String> categories=new ArrayList<>();
    private final Set<String> expanded=new HashSet<>();
    private final List<Integer> rows=new ArrayList<>();
    private int selected,scroll,dragOffset,custom=-1,missing=-1;
    private java.util.function.IntConsumer customConsumer;
    HousingTextureList custom(java.util.function.IntConsumer consumer){customConsumer=consumer;rebuild();return this;}
    private boolean dragging;
    HousingTextureList(int x,int y,int width,int selected){this(x,y,width,selected,8);}
    HousingTextureList(int x,int y,int width,int selected,int count) {
        this.x=x;this.y=y;this.width=width;this.count=count;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(!categories.contains(ScreenHousingTextures.category(i)))categories.add(ScreenHousingTextures.category(i));
        setSelected(selected);
    }
    void setSelected(int choice) {
        custom=com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice)?choice:-1;
        missing=choice>=com.vandorlabs.tiles.FilesystemTextures.ID_BASE && ScreenHousingTextures.localIndex(choice)==0?choice:-1;
        selected=ScreenHousingTextures.localIndex(ScreenHousingTextures.clamp(choice));expanded.add(ScreenHousingTextures.category(selected));rebuild();
        int index=rows.indexOf(selected);scroll=Math.max(0,Math.min(maxScroll(),index-count/2));
    }
    private void rebuild() {
        rows.clear();
        for(int c=0;c<categories.size();c++) {
            String category=categories.get(c);rows.add(-c-1);
            if(expanded.contains(category))for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(category.equals(ScreenHousingTextures.category(i)))rows.add(i);
        }
        if(customConsumer!=null)rows.add(-100000);
        scroll=Math.min(scroll,maxScroll());
    }
    int selected(){return custom>=0?custom:missing>=0?missing:ScreenHousingTextures.choiceAt(selected);}
    private int height(){return ROW_HEIGHT*count;}
    private int maxScroll(){return Math.max(0,rows.size()-count);}
    private int thumbHeight(){return Math.max(8,height()*count/Math.max(count,rows.size()));}
    private int thumbY(){return y+(height()-thumbHeight())*scroll/Math.max(1,maxScroll());}
    boolean click(int mouseX,int mouseY,int button) {
        if(button!=0 || mouseY<y || mouseY>=y+height())return false;
        if(mouseX>=x+width && mouseX<x+width+7 && maxScroll()>0) {
            dragOffset=mouseY>=thumbY() && mouseY<thumbY()+thumbHeight()?mouseY-thumbY():thumbHeight()/2;
            dragging=true;drag(mouseY);return true;
        }
        if(mouseX<x || mouseX>=x+width)return false;
        int index=scroll+(mouseY-y)/ROW_HEIGHT;if(index>=rows.size())return true;
        int choice=rows.get(index);
        if(choice==-100000){Minecraft mc=Minecraft.getMinecraft();net.minecraft.item.ItemStack carried=mc.player.inventory.getItemStack();mc.player.inventory.setItemStack(net.minecraft.item.ItemStack.EMPTY);mc.displayGuiScreen(new GuiCustomTexture(mc.currentScreen,value->{setSelected(value);customConsumer.accept(value);}));mc.player.inventory.setItemStack(carried);return true;}
        if(choice>=0){selected=choice;custom=-1;missing=-1;}
        else {String category=categories.get(-choice-1);if(!expanded.remove(category))expanded.add(category);rebuild();}
        return true;
    }
    boolean drag(int mouseY) {
        if(!dragging)return false;
        scroll=GuiProgrammableWall.scrollForDrag(mouseY,y,height(),thumbHeight(),maxScroll(),dragOffset);return true;
    }
    void release(){dragging=false;}
    boolean wheel(int mouseX,int mouseY,int delta) {
        if(delta==0 || mouseX<x || mouseX>=x+width+7 || mouseY<y || mouseY>=y+height())return false;
        scroll=Math.max(0,Math.min(maxScroll(),scroll+(delta>0?-1:1)));return true;
    }
    static String name(int choice) {
        if(com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice))return CustomBlockTextures.label(choice);
        choice=ScreenHousingTextures.localIndex(choice);
        String label=ScreenHousingTextures.label(choice);
        return label!=null?label:I18n.format("tile.vandorlabs."+ScreenHousingTextures.IDS[choice]+".name");
    }
    void draw(FontRenderer font,int mouseX,int mouseY) {
        Gui.drawRect(x-1,y-1,x+width+8,y+height()+1,0xFF000000);
        Gui.drawRect(x,y,x+width,y+height(),0xFF0A0A0C);
        Minecraft mc=Minecraft.getMinecraft();
        for(int row=0;row<count && scroll+row<rows.size();row++) {
            int choice=rows.get(scroll+row),yy=y+row*ROW_HEIGHT;
            boolean hover=mouseX>=x && mouseX<x+width && mouseY>=yy && mouseY<yy+ROW_HEIGHT;
            if(choice==selected && custom<0 || hover)Gui.drawRect(x,yy,x+width,yy+ROW_HEIGHT,choice==selected && custom<0?0xFF2A4A6A:0xFF1A1A20);
            if(choice==-100000){font.drawStringWithShadow(custom>=0?"Custom: "+name(custom):"Custom...",x+3,yy+2,0xFFABCFE8);}
            else if(choice<0) {
                String category=categories.get(-choice-1);
                font.drawStringWithShadow((expanded.contains(category)?"- ":"+ ")+category,x+3,yy+2,0xFFABCFE8);
            } else {
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
                new Gui().drawTexturedModalRect(x+3,yy+2,mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice)),8,8);
                GlStateManager.disableBlend();
                font.drawStringWithShadow(font.trimStringToWidth(name(choice),width-20),x+15,yy+2,choice==selected?0xFFFFE08A:0xFFD8D8D8);
            }
        }
        if(maxScroll()>0){Gui.drawRect(x+width,y,x+width+7,y+height(),0xFF303038);Gui.drawRect(x+width,thumbY(),x+width+7,thumbY()+thumbHeight(),0xFF808090);}
    }
}
