package com.vandorlabs.neoforge.navigation;
import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
/** One dedicated dialog for the Configurizer and ordinary table configuration. */
public final class NativeNavigationTableScreen extends AbstractContainerScreen<NativeNavigationTableMenu> {
 private NativeMaterialPicker picker;private EditBox channels;private int choice;
 public NativeNavigationTableScreen(NativeNavigationTableMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=308;imageHeight=250;choice=menu.tile.texture();}
 @Override protected void init(){super.init();picker=new NativeMaterialPicker(choice,id->ModernFinishCatalog.option(id).general(),value->{choice=value;save();}).specialMaterial(-5,"Navigation","Screens","vandorlabs:items/data_chip_navigation").specialMaterial(-2,"Original artwork","Screens","vandorlabs:blocks/core_flat").specialMaterial(-1,"Black","Screens","minecraft:block/black_concrete");picker.layout(leftPos+12,topPos+32,imageWidth-31,7,true);addRenderableWidget(picker.searchField());channels=new EditBox(font,leftPos+12,topPos+203,imageWidth-24,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(menu.tile.rxChannels().toString());channels.setResponder(value->save());addRenderableWidget(channels);addRenderableWidget(NativeDialogButton.builder(Component.literal("Done"),b->onClose()).bounds(leftPos+12,topPos+225,imageWidth-24,20).build());}
 private void save(){if(channels==null)return;var value=ChannelList.parse(channels.getValue());if(value==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);PacketDistributor.sendToServer(new NativeNavigationTableSettingsPayload(menu.tile.getBlockPos(),menu.containerId,choice,value));}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.PORT);picker.render(g,x,y);g.drawString(font,"Redstone channels",leftPos+12,topPos+191,0xbbbbbb,false);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
 @Override public boolean mouseClicked(double x,double y,int b){return picker.click(x,y,b)||super.mouseClicked(x,y,b);}@Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return picker.drag(x,y)||super.mouseDragged(x,y,b,dx,dy);}@Override public boolean mouseReleased(double x,double y,int b){picker.release();return super.mouseReleased(x,y,b);}@Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}@Override public boolean keyPressed(int k,int s,int m){return picker.keyPressed(k,s,m)||super.keyPressed(k,s,m);}@Override public void onClose(){save();super.onClose();}@Override public boolean isPauseScreen(){return false;}
}
