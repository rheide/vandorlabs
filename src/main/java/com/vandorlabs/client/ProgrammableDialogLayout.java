package com.vandorlabs.client;

import net.minecraft.client.gui.GuiButton;

/** Forge widgets over the shared logical dialog layout. */
final class ProgrammableDialogLayout extends com.vandorlabs.ui.ProgrammableDialogLayout {
    ProgrammableDialogLayout(int screenWidth,int screenHeight){super(screenWidth,screenHeight);}
    ProgrammableDialogLayout(int screenWidth,int screenHeight,int maxWidth,int maxHeight){super(screenWidth,screenHeight,maxWidth,maxHeight);}
    private GuiButton widget(Button bounds){return new GuiButton(bounds.id,bounds.x,bounds.y,bounds.width,bounds.height,bounds.label);}
    GuiButton control(int id,int y,String label){return widget(controlBounds(id,y,label));}
    GuiButton done(int id){return widget(doneBounds(id));}
    GuiButton tab(int id,int index,int count,String label){Button b=tabBounds(id,index,count,label);return new GuiButton(b.id,b.x,b.y,b.width,b.height,net.minecraft.client.Minecraft.getMinecraft().fontRenderer.trimStringToWidth(b.label,b.width-4));}
    GuiButton choice(int id,int index,int count,int y,String label){return widget(choiceBounds(id,index,count,y,label));}
}
