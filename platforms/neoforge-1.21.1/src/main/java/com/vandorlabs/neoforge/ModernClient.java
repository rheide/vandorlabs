package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ModernClient {
    private ModernClient() {}
    static ProgrammableBlockDialog.State smokeSettings() { return ((FinishScreen)Minecraft.getInstance().screen).state; }
    static int smokeScroll() { return ((FinishScreen)Minecraft.getInstance().screen).picker.scroll(); }
    static void smokeCenterSelection() { ((FinishScreen)Minecraft.getInstance().screen).picker.centerSelected(); }
    @net.neoforged.bus.api.SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(VandorLabsModern.PROGRAMMABLE_MENU.get(),FinishScreen::new);
    }
    static final class FinishScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<ProgrammableMenu> {
        private final net.minecraft.core.BlockPos pos;
        private ProgrammableBlockDialog.State state;
        private ProgrammableDialogLayout panel;
        private List<ProgrammableBlockDialog.Control> controls=List.of();
        private NativeMaterialPicker picker;
        FinishScreen(ProgrammableMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component ignoredTitle) {
            super(menu,inventory,Component.translatable("gui.vandorlabs.block.title"));pos=menu.tile.getBlockPos();state=menu.tile.settings();
        }
        @Override protected void init() { super.init();panel=new ProgrammableDialogLayout(width,height);rebuild();picker.centerSelected(); }
        private void rebuild() {
            clearWidgets();controls=ProgrammableBlockDialog.controls(panel,state);
            for(var control:controls) {
                var b=control.bounds;
                Button button=Button.builder(Component.literal(font.plainSubstrByWidth(b.label,b.width-4)),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();
                button.active=control.enabled;addRenderableWidget(button);
            }
            var previous=picker;
            picker=new NativeMaterialPicker(panel.listX,ProgrammableBlockDialog.listY(panel,state),panel.listWidth,ProgrammableBlockDialog.listRows(panel,state),state.selectedTexture(),choice->{state=state.choose(choice);send();rebuild();});
            picker.keepView(previous);
        }
        private void send() { PacketDistributor.sendToServer(new FinishPayload(pos,state.mainTexture,state.layout,state.facesEnabled,state.faces())); }
        private void action(int id,int direction) {
            if(id==ProgrammableBlockDialog.DONE) { onClose();return; }
            state=state.action(id,direction);
            if(id==ProgrammableBlockDialog.FACE_OVERRIDES||id==ProgrammableBlockDialog.TEXTURE_LAYOUT||id==ProgrammableBlockDialog.INHERIT)send();
            rebuild();if(id==ProgrammableBlockDialog.MAIN||id>=ProgrammableBlockDialog.FIRST_FACE&&id<ProgrammableBlockDialog.FIRST_FACE+6)picker.centerSelected();
        }
        @Override public boolean mouseClicked(double x,double y,int button) {
            if(picker.click(x,y,button))return true;
            if(button==1)for(var control:controls)if(control.enabled&&control.bounds.contains(x,y)) { action(control.bounds.id,-1);return true; }
            return super.mouseClicked(x,y,button);
        }
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return picker.drag(y)||super.mouseDragged(x,y,button,dx,dy); }
        @Override public boolean mouseReleased(double x,double y,int button) { picker.release();return super.mouseReleased(x,y,button); }
        @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical); }
        @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float delta) { graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,delta,mouseX,mouseY); }
        @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
            graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);
            graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+18,0xFF202028);
            graphics.drawString(font,title,panel.left+8,panel.top+5,0xFFFFFFFF,false);
            picker.draw(graphics,font,mouseX,mouseY);
        }
        @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {}
        @Override public boolean isPauseScreen() { return false; }
    }
}
