package com.vandorlabs.neoforge.appliances;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
/** Ordinary inventory dialog: consumables for printing, original document for scanning. */
public final class NativeApplianceScreen extends AbstractContainerScreen<NativeApplianceMenu>{
 public NativeApplianceScreen(NativeApplianceMenu m,Inventory i,Component title){super(m,i,title);imageWidth=176;imageHeight=166;inventoryLabelY=72;}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.PORT);for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff61727b);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff111a22);}if(menu.tile.scanner())g.drawCenteredString(font,"Original",leftPos+88,topPos+23,0xbbbbbb);else{g.drawCenteredString(font,"Ink",leftPos+70,topPos+23,0xbbbbbb);g.drawCenteredString(font,"Paper",leftPos+106,topPos+23,0xbbbbbb);}}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xbbbbbb,false);}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
 @Override public boolean isPauseScreen(){return false;}
}
