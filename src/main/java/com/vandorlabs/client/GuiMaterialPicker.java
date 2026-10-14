package com.vandorlabs.client;

import java.io.IOException;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import org.lwjgl.input.Mouse;

/** Shared wheel coordinates, scrollbar dragging and release for material dialogs. */
abstract class GuiMaterialPicker extends GuiContainer {
    GuiMaterialPicker(Container container){super(container);}
    protected abstract HousingTextureList materialPicker();
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed){
        HousingTextureList picker=materialPicker();
        if(picker==null || !picker.drag(y))super.mouseClickMove(x,y,button,elapsed);
    }
    @Override protected void mouseReleased(int x,int y,int button){
        HousingTextureList picker=materialPicker();if(picker!=null)picker.release();super.mouseReleased(x,y,button);
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();HousingTextureList picker=materialPicker();
        if(picker!=null)picker.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
    }
}
