package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.Minecraft;

/** Shared static face palette within the existing authorized settings container. */
final class SurfaceTexturePicker {
    private final TileEntityAnimatedScreenSelector tile;
    private final boolean second;
    private HousingTextureList list;
    private boolean open;
    private int x,y,width,height,slot;
    SurfaceTexturePicker(TileEntityAnimatedScreenSelector tile,boolean second){this.tile=tile;this.second=second;}
    void init(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;open=false;}
    boolean click(int mx,int my,int button) {
        if(button!=0)return open;
        if(!open) {
            if(mx>=x+width-114 && mx<x+width-6 && my>=y+2 && my<y+17){open=true;refresh();return true;}return false;
        }
        if(my>=y+height-26 && my<y+height-6) {
            if(mx>=x+12 && mx<x+width/2){tile.setSurfaceTexture(slot,-1);send(-1);open=false;return true;}
            if(mx>=x+width/2 && mx<x+width-12){open=false;return true;}
        }
        if(second && my>=y+25 && my<y+45 && mx>=x+12 && mx<x+width-12){slot=1-slot;refresh();return true;}
        int before=list.selected();if(list.click(mx,my,button) && before!=list.selected()){tile.setSurfaceTexture(slot,list.selected());send(list.selected());}
        return true;
    }
    private void send(int choice){PacketHandler.INSTANCE.sendToServer(new MessageSurfaceTexture(tile.getPos(),slot,choice));}
    private void refresh(){int choice=tile.getSurfaceTexture(slot);if(choice<0)choice=ScreenHousingTextures.screenIndex(slot==1?"console_inputs/"+tile.getSecondaryInputPanel()+"_static":tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableInput || second?"console_inputs/"+tile.getInputPanel()+"_static":tile.getSelectedScreen()+"_static");list=new HousingTextureList(x+12,y+(second?52:28),width-32,choice,Math.max(3,(height-(second?92:68))/12));}
    boolean drag(int my){return open && list.drag(my);}
    void release(){if(list!=null)list.release();}
    boolean wheel(int mx,int my,int delta){return open && list.wheel(mx,my,delta);}
    void draw(FontRenderer font,int mx,int my) {
        if(!open){Gui.drawRect(x+width-114,y+2,x+width-6,y+17,0xFF38566C);font.drawString("Surface textures",x+width-110,y+5,0xFFFFFF);return;}
        Gui.drawRect(x,y,x+width,y+height,0xFF18242F);font.drawString("Static surface texture",x+12,y+10,0xFFFFFF);
        if(second){Gui.drawRect(x+12,y+25,x+width-12,y+45,0xFF38566C);font.drawString("Surface: "+(slot==0?"Front":"Rear")+" (click to switch)",x+18,y+31,0xFFFFFF);}
        list.draw(font,mx,my);Gui.drawRect(x+12,y+height-26,x+width/2-4,y+height-6,0xFF38566C);Gui.drawRect(x+width/2,y+height-26,x+width-12,y+height-6,0xFF38566C);
        font.drawString("Use screen / animation",x+18,y+height-20,0xFFFFFF);font.drawString("Done",x+width/2+8,y+height-20,0xFFFFFF);
    }
}
