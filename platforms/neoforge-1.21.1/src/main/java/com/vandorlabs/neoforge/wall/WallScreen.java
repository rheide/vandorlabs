package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.WallDialog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native paint/widgets over the legacy flat-wall policy, including immediate settings edits. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WallScreen extends AbstractContainerScreen<WallMenu> {
    private ProgrammableDialogLayout panel;
    private WallDialog.State state;
    private NativeMaterialPicker picker;
    private int selected;
    public WallScreen(WallMenu menu,Inventory inventory,Component title){super(menu,inventory,title);state=menu.tile.wallSettings();selected=state.main;imageWidth=460;imageHeight=332;}
    @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(WallMenu.TYPE.get(),WallScreen::new);}
    private void live(){state=state.withLive(menu.tile.wallSettings());}
    @Override protected void init(){
        live();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;super.init();clearWidgets();var previous=picker;
        picker=new NativeMaterialPicker(panel.listX,WallDialog.listY(panel,menu.tile.profile()),panel.listWidth,WallDialog.rows(panel,menu.tile.profile()),state.target==-3?state.selected(menu.tile.wallSettings().appearance.shade):selected,state.target==-3?glassEntries():NativeMaterialPicker.generalEntries(),this::choose);
        if(state.target==-3)picker.keepView(previous);buttons();
    }
    private static List<NativeMaterialPicker.Entry> glassEntries(){var entries=new ArrayList<NativeMaterialPicker.Entry>();for(int shade=0;shade<GlassTextures.IDS.length;shade++)entries.add(new NativeMaterialPicker.Entry(WallDialog.GLASS_BASE+shade,GlassTextures.LABELS[shade],"Glass",GlassTextures.texture(shade,1).replace(":blocks/",":block/"),1,1));entries.addAll(NativeMaterialPicker.generalEntries());return entries;}
    private void buttons(){for(var control:WallDialog.controls(panel,menu.tile.profile(),state,Component.translatable("gui.done").getString())){var b=control.bounds;var button=Button.builder(Component.literal(b.label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();button.active=control.enabled;addRenderableWidget(button);}}
    private void send(WallDialog.Effect effect){menu.tile.applyWall(WallDialog.applyEffect(menu.tile.wallSettings(),state,effect));PacketDistributor.sendToServer(new WallPayload(menu.containerId,menu.pos,state,effect));}
    private void choose(int choice){live();int target=state.target;selected=choice;state=state.choose(choice);send(state.choiceEffect());if(target==-3){if(picker.selected()!=choice)picker.setSelected(choice);init();}else if(target==-2)init();}
    private void action(int id,int direction){
        live();if(!WallDialog.handles(menu.tile.profile(),state,id))return;var effect=WallDialog.effect(id);if(effect==WallDialog.Effect.CLOSE){onClose();return;}state=state.action(id,direction);if(effect!=WallDialog.Effect.NONE)send(effect);
        if(WallDialog.rebuild(id)){selected=state.selected(menu.tile.wallSettings().appearance.shade);if(WallDialog.resetView(id))picker=null;init();}else{clearWidgets();buttons();}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var c:WallDialog.controls(panel,menu.tile.profile(),state,"Done"))if(c.enabled&&(WallDialog.effect(c.bounds.id)==WallDialog.Effect.APPEARANCE||WallDialog.effect(c.bounds.id)==WallDialog.Effect.LAYOUT)&&c.bounds.contains(x,y)){action(c.bounds.id,-1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+18,0xFF202028);g.drawString(font,title,panel.left+8,panel.top+5,0xFFFFFF,false);picker.draw(g,font,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
