package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.SlabStairDialog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native paint/widgets over the same immediate two/eight-tab material and stair policy. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ShapeScreen extends AbstractContainerScreen<ShapeMenu> {
    private ProgrammableDialogLayout panel;private SlabStairDialog.State state;private NativeMaterialPicker picker;
    public ShapeScreen(ShapeMenu menu,Inventory inventory,Component title){super(menu,inventory,title);state=menu.tile.shapeSettings();imageWidth=460;imageHeight=332;}
    @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(ShapeMenu.TYPE.get(),ShapeScreen::new);}
    @Override protected void init(){panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;super.init();clearWidgets();picker=new NativeMaterialPicker(panel.listX,SlabStairDialog.listY(panel),panel.listWidth,SlabStairDialog.rows(panel),state.selected(),this::choose);buttons();}
    private void buttons(){for(var control:SlabStairDialog.controls(panel,state,menu.tile.isStair(),Component.translatable("gui.done").getString())){var b=control.bounds;
        if(control.slider){var slider=new AbstractSliderButton(b.x,b.y,b.width,b.height,Component.literal(b.label),state.stairs.sliderIndex()/3D){
            @Override protected void updateMessage(){setMessage(Component.literal(SlabStairDialog.stepLabel(state.slider((int)Math.round(value*3)).stairs)));}
            @Override protected void applyValue(){state=state.slider((int)Math.round(value*3));send();}
        };slider.active=control.enabled;addRenderableWidget(slider);}
        else {var button=Button.builder(Component.literal(b.label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();button.active=control.enabled;addRenderableWidget(button);}
    }}
    private void send(){PacketDistributor.sendToServer(new ShapePayload(menu.containerId,menu.pos,state));}
    private void choose(int choice){int target=state.target;state=state.choose(choice);send();if(target==-2||target>=0&&state.facesEnabled)init();}
    private void action(int id,int direction){if(id==100){onClose();return;}state=state.action(id,direction);if(id==103||id==106||id==108||id==128||id==131)send();if(id==103){clearWidgets();buttons();}else init();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var c:SlabStairDialog.controls(panel,state,menu.tile.isStair(),"Done"))if(c.enabled&&(c.bounds.id==103||c.bounds.id==106||c.bounds.id==131)&&c.bounds.contains(x,y)){action(c.bounds.id,-1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+18,0xFF202028);g.drawString(font,title,panel.left+8,panel.top+5,0xFFFFFF,false);picker.draw(g,font,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
