package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.storage.*;
import com.vandorlabs.ui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Existing housing controls and material picker around a dedicated Storage edit session. */
public final class NativeStorageScreen extends AbstractContainerScreen<NativeStorageMenu> {
 private ProgrammableBlockDialog.State state;private ProgrammableDialogLayout panel;private List<ProgrammableBlockDialog.Control> controls=List.of();private final NativeMaterialPicker picker;
 public NativeStorageScreen(NativeStorageMenu menu,Inventory inventory,Component title){super(menu,inventory,title);var housing=menu.tile.settings().housing();state=new ProgrammableBlockDialog.State(housing.mainTexture,housing.layout(),housing.facesEnabled,housing.faces(),-1);picker=new NativeMaterialPicker(state.selectedTexture(),id->ModernFinishCatalog.option(id).general(),this::choose).presentation(NativeStorageAssets::presentation);}
 public static void register(RegisterMenuScreensEvent event,MenuType<NativeStorageMenu> type){event.register(type,NativeStorageScreen::new);}
 public ProgrammableBlockDialog.State editingState(){return state;}public ProgrammableDialogLayout panel(){return panel;}public NativeMaterialPicker activePicker(){return picker;}public List<ProgrammableBlockDialog.Control> controls(){return controls;}
 @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);rebuild(true);}
 private void rebuild(boolean center){clearWidgets();controls=ProgrammableBlockDialog.controls(panel,state,Component.translatable("gui.done").getString());for(var control:controls){if(control.tab)continue;var button=Button.builder(Component.literal(control.label),ignored->action(control.id,1)).bounds(control.x,control.y,control.width,control.height).build();button.active=control.enabled;addRenderableWidget(button);}picker.select(state.selectedTexture(),false);picker.layout(panel.listX,ProgrammableBlockDialog.listY(panel,state),panel.listWidth,ProgrammableBlockDialog.listRows(panel,state),center);addRenderableWidget(picker.searchField());}
 private void send(){PacketDistributor.sendToServer(new StorageSettingsPayload(NativeStorageEdit.from(menu.tile.getBlockPos(),menu.containerId,state)));}
 public void action(int id,int direction){picker.closeInventory();if(id==ProgrammableBlockDialog.DONE){onClose();return;}state=state.action(id,direction);if(id==ProgrammableBlockDialog.FACE_OVERRIDES||id==ProgrammableBlockDialog.INHERIT||id==ProgrammableBlockDialog.TEXTURE_LAYOUT)send();rebuild(true);}
 public void choose(int choice){state=state.choose(choice);send();rebuild(false);}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var control:controls)if(control.enabled&&(button==0||control.id==ProgrammableBlockDialog.FACE_OVERRIDES||control.id==ProgrammableBlockDialog.TEXTURE_LAYOUT)&&x>=control.x&&x<control.x+control.width&&y>=control.y&&y<control.y+control.height){action(control.id,button==1?-1:1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){return picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
 @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF202028);graphics.drawString(font,title,panel.left+8,panel.top+8,0xFFFFFFFF,false);for(var control:controls)if(control.tab){graphics.fill(control.x,control.y,control.x+control.width,control.y+control.height,control.enabled?0xFF203540:0xFF365568);graphics.drawCenteredString(font,control.label,control.x+control.width/2,control.y+5,0xFFE0F0FF);}picker.render(graphics,mouseX,mouseY);}
 @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
 @Override public boolean isPauseScreen(){return false;}
}
