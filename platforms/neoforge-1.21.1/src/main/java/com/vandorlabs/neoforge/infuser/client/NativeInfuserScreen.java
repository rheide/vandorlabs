package com.vandorlabs.neoforge.infuser.client;
import com.vandorlabs.neoforge.infuser.*;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.ui.InfuserDialog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
/** Shared standard machine layout: sunlight and work/charge, without a combustion gauge. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeInfuserScreen extends AbstractContainerScreen<NativeInfuserMenu>{
 public NativeInfuserScreen(NativeInfuserMenu menu,Inventory inventory,Component title){super(menu,inventory,Component.literal(InfuserDialog.TITLE));imageWidth=InfuserDialog.WIDTH;imageHeight=InfuserDialog.HEIGHT;}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(NativeInfuserBindings.MENU,NativeInfuserScreen::new);}
 @Override protected void init(){super.init();addRenderableWidget(com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(InfuserDialog.DONE),button->onClose()).bounds(leftPos+InfuserDialog.DONE_X,topPos+InfuserDialog.DONE_Y,InfuserDialog.DONE_WIDTH,InfuserDialog.DONE_HEIGHT).build());}
 public int left(){return leftPos;}public int top(){return topPos;}public boolean sunlight(){return menu.value(3)>0;}
 private ItemStack battery(){var input=menu.contents().getItem(0);return NativeBatteryModule.valid(input)?input:menu.contents().getItem(2);}
 private boolean charging(){return NativeBatteryModule.valid(battery());}
 public int progressPixels(){return InfuserDialog.fill(menu.value(2),charging(),NativeBatteryModule.energy(battery()),NativeBatteryModule.CAPACITY);}
 public boolean controlsFit(){if(leftPos<0||topPos<0||leftPos+imageWidth>width||topPos+imageHeight>height||InfuserDialog.FOOTER_Y+font.lineHeight>imageHeight)return false;for(var slot:menu.slots)if(slot.x<0||slot.y<0||slot.x+16>imageWidth||slot.y+16>imageHeight)return false;return font.width(InfuserDialog.detail(200,true,10000,10000))<=InfuserDialog.PROGRESS_WIDTH;}
 @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xc0101010,0xd0101010);renderBg(graphics,partial,x,y);}
 @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
  NativeDialogVisuals.panel(graphics,font,title,leftPos,topPos,imageWidth,imageHeight,InfuserDialog.STYLE);
  for(int slot=0;slot<39;slot++){int sx=leftPos+InfuserDialog.slotX(slot),sy=topPos+InfuserDialog.slotY(slot);graphics.fill(sx-1,sy-1,sx+17,sy+17,InfuserDialog.FRAME);graphics.fill(sx,sy,sx+16,sy+16,InfuserDialog.SLOT);}
  int left=leftPos+InfuserDialog.PROGRESS_X,top=topPos+InfuserDialog.PROGRESS_Y;graphics.fill(left-1,top-1,left+InfuserDialog.PROGRESS_WIDTH+1,top+InfuserDialog.PROGRESS_HEIGHT+1,InfuserDialog.FRAME);graphics.fill(left,top,left+InfuserDialog.PROGRESS_WIDTH,top+InfuserDialog.PROGRESS_HEIGHT,InfuserDialog.SLOT);int fill=progressPixels();if(fill>0)graphics.fill(left,top,left+fill,top+InfuserDialog.PROGRESS_HEIGHT,InfuserDialog.PROGRESS);
 }
 @Override protected void renderLabels(GuiGraphics graphics,int x,int y){
  boolean sun=sunlight(),battery=charging();graphics.drawString(font,InfuserDialog.sunlight(sun),InfuserDialog.SUN_X,InfuserDialog.SUN_Y,InfuserDialog.sunlightColor(sun),false);
  graphics.drawString(font,InfuserDialog.INPUT,InfuserDialog.INPUT_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR,false);graphics.drawString(font,InfuserDialog.CATALYST,InfuserDialog.CATALYST_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR,false);graphics.drawString(font,InfuserDialog.OUTPUT,InfuserDialog.OUTPUT_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR,false);
  graphics.drawString(font,InfuserDialog.workLabel(battery),InfuserDialog.PROGRESS_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR,false);graphics.drawString(font,font.plainSubstrByWidth(InfuserDialog.detail(menu.value(2),battery,NativeBatteryModule.energy(battery()),NativeBatteryModule.CAPACITY),InfuserDialog.PROGRESS_WIDTH),InfuserDialog.PROGRESS_X,InfuserDialog.DETAIL_Y,InfuserDialog.MUTED_COLOR,false);
  graphics.drawString(font,InfuserDialog.INVENTORY,InfuserDialog.INVENTORY_X,InfuserDialog.INVENTORY_LABEL_Y,InfuserDialog.TEXT_COLOR,false);graphics.drawString(font,InfuserDialog.CATALYST_LEFT,InfuserDialog.LEFT_X,InfuserDialog.CATALYST_LABEL_Y,InfuserDialog.MUTED_COLOR,false);graphics.drawString(font,InfuserDialog.catalyst(menu.value(0)),InfuserDialog.LEFT_X,InfuserDialog.CATALYST_VALUE_Y,InfuserDialog.TEXT_COLOR,false);graphics.drawString(font,InfuserDialog.recipe(battery),InfuserDialog.LEFT_X,InfuserDialog.RECIPE_HINT_Y,InfuserDialog.MUTED_COLOR,false);
  graphics.drawString(font,InfuserDialog.status(!menu.contents().getItem(0).isEmpty(),!menu.contents().getItem(2).isEmpty(),sun,menu.value(0)>0||NativeInfuserEntity.fuelTicks(menu.contents().getItem(1))>0,battery),InfuserDialog.LEFT_X,InfuserDialog.FOOTER_Y,InfuserDialog.MUTED_COLOR,false);
 }
 @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
 @Override public boolean isPauseScreen(){return false;}
}
