package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.surface.core.SurfaceArtwork;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source Active/Inactive artwork tabs, layout cycle and the standard scroll/drag material picker. */
public final class NativeSurfaceScreen extends AbstractContainerScreen<NativeSurfaceMenu> {
    private com.vandorlabs.ui.ProgrammableDialogLayout panel;
    private String on,off;private int mode;private boolean active;private NativeMaterialPicker picker;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativeSurfaceScreen(NativeSurfaceMenu menu,Inventory inventory,Component title){super(menu,inventory,Component.literal(com.vandorlabs.ui.SurfaceDialog.TITLE));on=menu.tile.activeArtwork();off=menu.tile.inactiveArtwork();mode=menu.tile.layoutMode();}
    public Button control(int id){return buttons.get(id);}
    public NativeMaterialPicker materialPicker(){return picker;}
    @Override protected void init(){panel=new com.vandorlabs.ui.ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;super.init();rebuild();}
    private void button(int id,int x,int y,int w,String caption){var button=com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(caption),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void rebuild(){
        clearWidgets();buttons.clear();
        for(var control:com.vandorlabs.ui.SurfaceDialog.controls(panel,active,mode)){
            Button widget=control.tab?new com.vandorlabs.neoforge.client.NativeDialogTabButton(control.x,control.y,control.width,control.height,Component.literal(control.label),ignored->action(control.id,1)):com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(control.label),ignored->action(control.id,1)).bounds(control.x,control.y,control.width,control.height).build();widget.active=control.enabled;buttons.put(control.id,widget);addRenderableWidget(widget);
        }
        if(picker==null){picker=new NativeMaterialPicker(NativeSurfaceArtwork.choice(active?on:off),id->true,choice->{if(active)on=NativeSurfaceArtwork.fromChoice(choice);else off=NativeSurfaceArtwork.fromChoice(choice);send();});for(int i=0;i<SurfaceArtwork.IDS.length;i++)if(NativeSurfaceArtwork.choice(SurfaceArtwork.IDS[i])<0)picker.specialMaterial(-i-1,SurfaceArtwork.LABELS[i],"Surface","vandorlabs:blocks/"+SurfaceArtwork.FILES[i]);}
        picker.select(NativeSurfaceArtwork.choice(active?on:off),false);picker.layout(panel.listX,panel.top+54,panel.listWidth,panel.rows(true),false);addRenderableWidget(picker.searchField());
    }
    private void send(){PacketDistributor.sendToServer(new NativeSurfaceSettingsPayload(menu.containerId,menu.tile.getBlockPos(),on,off,mode));}
    public void action(int id,int direction){if(id==90||id==91){active=id==com.vandorlabs.ui.SurfaceDialog.ACTIVE;rebuild();}else if(id==1){mode=com.vandorlabs.ui.SurfaceDialog.cycleLayout(mode,direction);send();rebuild();}else if(id==3)picker.customInventory();else if(id==2)onClose();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&buttons.get(1).isMouseOver(x,y)){action(1,-1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(graphics,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.SURFACE);picker.render(graphics,x,y);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
