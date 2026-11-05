package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.ui.DialogInput;
import com.vandorlabs.ui.MountedControlDialog;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fixed current mounted-control dialog; mount drafts commit on Done, inline settings flush separately. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBinaryControlScreen extends AbstractContainerScreen<BinaryControlMenu> {
    private int page,heightValue,tilt,direction,size;
    private NativeInlineTransmissionWidget transmission;
    private final Map<Integer,Button> mountButtons=new HashMap<>();
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(VandorLabsBootstrap.BINARY_CONTROL_MENU.get(),NativeBinaryControlScreen::new);}
    public NativeBinaryControlScreen(BinaryControlMenu menu,Inventory inventory,Component title){
        super(menu,inventory,menu.tile.getBlockState().getBlock().getName());imageWidth=MountedControlDialog.PANEL_WIDTH;imageHeight=MountedControlDialog.PANEL_HEIGHT;
        var mount=menu.tile.mountSettings();heightValue=mount.height;tilt=mount.tilt;direction=mount.direction;size=menu.tile.getPowerLeverSize();
    }
    public int page(){return page;}
    public int sizeDraft(){return size;}
    public ControlMountSettings mountDraft(){return ControlMountSettings.of(heightValue,tilt,direction);}
    public NativeInlineTransmissionWidget inlineWidget(){return transmission;}
    public Button mountButton(int id){return mountButtons.get(id);}
    public int panelLeft(){return leftPos;}public int panelTop(){return topPos;}
    public int panelWidth(){return imageWidth;}public int panelHeight(){return imageHeight;}
    @Override protected void init(){super.init();
        if(transmission==null)transmission=new NativeInlineTransmissionWidget(font,leftPos+12,topPos+62,256,menu.tile.transmissionChannels(),menu.tile.transmissionRange(),menu.tile.localRedstone(),(channels,range,local)->PacketDistributor.sendToServer(new BinaryTransmissionPayload(menu.tile.getBlockPos(),menu.containerId,channels,range,local)));
        else {transmission.flush();transmission.layout(leftPos+12,topPos+62,256);}rebuild();
    }
    private void rebuild(){setFocused(null);clearWidgets();mountButtons.clear();
        if(page==0){if(menu.tile.supportsPowerLeverSize())addOption(MountedControlDialog.sizeControl(leftPos,topPos,menu.tile.isTwinPowerLever(),size));for(var control:MountedControlDialog.controls(leftPos,topPos,heightValue,tilt,direction))addOption(control);}
        else for(var control:transmission.controls())addRenderableWidget(control);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->done()).bounds(leftPos+114,topPos+206,154,20).build());
    }
    private void addOption(com.vandorlabs.ui.DialogControl control){Button button=Button.builder(Component.literal(control.label),ignored->cycle(control.id,1)).bounds(control.x,control.y,control.width,control.height).build();mountButtons.put(control.id,button);addRenderableWidget(button);}
    private void cycle(int id,int step){
        if(id==MountedControlDialog.SIZE)size=DialogInput.cycle(size,2,step);
        if(id==MountedControlDialog.HEIGHT)heightValue=DialogInput.cycle(heightValue,4,step);if(id==MountedControlDialog.TILT)tilt=DialogInput.cycle(tilt,4,step);if(id==MountedControlDialog.DIRECTION)direction=DialogInput.cycle(direction,4,step);
        mountButtons.get(id).setMessage(Component.literal(id==MountedControlDialog.SIZE?MountedControlDialog.sizeLabel(menu.tile.isTwinPowerLever(),size):id==9?MountedControlDialog.heightLabel(heightValue):id==10?MountedControlDialog.tiltLabel(tilt):MountedControlDialog.directionLabel(direction)));
    }
    private void done(){transmission.flush();if(menu.tile.supportsPowerLeverSize())PacketDistributor.sendToServer(new PowerLeverPayload(menu.tile.getBlockPos(),menu.containerId,mountDraft(),size));else PacketDistributor.sendToServer(new BinaryControlPayload(menu.tile.getBlockPos(),menu.containerId,mountDraft()));onClose();}
    @Override public void onClose(){if(transmission!=null)transmission.flush();super.onClose();}
    @Override public void removed(){if(transmission!=null)transmission.flush();super.removed();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0)for(int tab=0;tab<2;tab++){int left=leftPos+(tab==0?12:116);if(x>=left&&x<left+100&&y>=topPos+30&&y<topPos+50){if(page!=tab){transmission.hide();page=tab;rebuild();}return true;}}
        if(page==0&&button==1)for(var entry:mountButtons.entrySet())if(entry.getValue().isMouseOver(x,y)){entry.getValue().playDownSound(minecraft.getSoundManager());cycle(entry.getKey(),-1);return true;}
        if(page==1&&transmission.mouseClicked(x,y,button))return true;return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==1&&transmission.specialKey(key))return true;if(key==257||key==335){done();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF101012);graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+24,0xFF202028);graphics.drawString(font,title,leftPos+12,topPos+8,0xFFFFFFFF,false);
        for(int tab=0;tab<2;tab++){int left=leftPos+(tab==0?12:116);graphics.fill(left,topPos+30,left+100,topPos+50,page==tab?0xFF365568:0xFF203540);graphics.drawCenteredString(font,tab==0?"General":"Redstone",left+50,topPos+35,0xFFE0F0FF);}if(page==1)transmission.renderLabels(graphics,font);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
