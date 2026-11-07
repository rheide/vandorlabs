package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.ui.CopifierDialog;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Same five-page layout and option indices as Forge; uses the synchronized held-tool preview. */
public final class NativeCopifierScreen extends AbstractContainerScreen<NativeCopifierMenu> {
 private int page,previousGuiScale=-1;private boolean loaded,connected,ignore,closing;private long[] words;private List<CopifierDialog.Control> controls=List.of();
 public NativeCopifierScreen(NativeCopifierMenu menu,Inventory inventory,Component title){super(menu,inventory,Component.literal("Duplifier: Apply Settings"));imageWidth=CopifierDialog.WIDTH;imageHeight=CopifierDialog.height();}
 public static void register(RegisterMenuScreensEvent event,MenuType<NativeCopifierMenu> type){event.register(type,NativeCopifierScreen::new);}
 public List<CopifierDialog.Control> controls(){return controls;}public int page(){return page;}public boolean loaded(){return loaded;}
 @Override protected void init(){if(!closing&&(width<imageWidth+12||height<imageHeight+12)&&minecraft.getWindow().getGuiScale()>1){if(previousGuiScale<0)previousGuiScale=minecraft.options.guiScale().get();minecraft.options.guiScale().set((int)minecraft.getWindow().getGuiScale()-1);minecraft.resizeDisplay();return;}super.init();if(loaded)rebuild();}
 @Override public void removed(){closing=true;super.removed();if(previousGuiScale>=0){int restore=previousGuiScale;previousGuiScale=-1;minecraft.options.guiScale().set(restore);minecraft.resizeDisplay();}}
 @Override protected void containerTick(){super.containerTick();if(!loaded&&menu.synchronizedStack){var root=NativeCopifierItem.data(menu.editorStack());words=NativeCopifierOptions.maskWords(root);connected=NativeCopifierOptions.connected(root);ignore=NativeCopifierOptions.ignoreDirectionality(root);loaded=true;rebuild();}}
 private void rebuild(){clearWidgets();controls=CopifierDialog.controls(leftPos,topPos,page,words,connected,ignore,Component.translatable("gui.done").getString());for(var control:controls){var button=Button.builder(Component.literal(control.label),ignored->action(control.id)).bounds(control.x,control.y,control.width,control.height).build();button.active=control.enabled;addRenderableWidget(button);}}
 private void action(int id){if(!loaded)return;if(id==CopifierDialog.DONE){onClose();return;}if(id<CopifierDialog.IGNORE){page=id;rebuild();return;}if(id==CopifierDialog.UNDO){PacketDistributor.sendToServer(new NativeCopifierUndoPayload(menu.containerId));return;}if(id==CopifierDialog.CONNECTED)connected=!connected;else if(id==CopifierDialog.IGNORE)ignore=!ignore;else if(id>=CopifierDialog.OPTION_START){int index=id-CopifierDialog.OPTION_START;words[index/64]^=1L<<(index%64);}PacketDistributor.sendToServer(new NativeCopifierPayload(menu.containerId,words,connected,ignore));rebuild();}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var control:controls)if(control.enabled&&(button==0||CopifierDialog.cycles(control.id))&&x>=control.x&&x<control.x+control.width&&y>=control.y&&y<control.y+control.height){action(control.id);return true;}return super.mouseClicked(x,y,button);}
 @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
 @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF15171C);graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+22,0xFF2A3039);graphics.drawString(font,title,leftPos+10,topPos+7,0xFFFFFFFF,false);graphics.drawString(font,"Choose which copied properties to apply",leftPos+14,topPos+70,0xFFB8C7D2,false);}
 @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
 @Override public boolean isPauseScreen(){return false;}
}
