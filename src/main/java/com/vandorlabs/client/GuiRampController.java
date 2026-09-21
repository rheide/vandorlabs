package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.network.MessageRampController;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public class GuiRampController extends GuiContainer {
    private final TileEntityRampController controller;
    private GuiTextField heightField;
    private GuiTextField channelField;
    private int segments;
    private boolean top,powerOn,slow,elevator;
    private int lastUpdate;
    private net.minecraft.util.EnumFacing direction;
    public GuiRampController(TileEntityRampController controller) {
        super(new ContainerRampController(controller));
        this.controller=controller;
        segments=controller.segments; top=controller.top; powerOn=controller.activateOnPower;
        slow=controller.slow; elevator=controller.elevator;
        lastUpdate=controller.clientUpdates;
        direction=controller.rampDirection();
        xSize=320; ySize=264;
    }
    @Override public void initGui() {
        super.initGui(); Keyboard.enableRepeatEvents(true);
        heightField=new GuiTextField(0,fontRenderer,guiLeft+244,guiTop+37,54,18);
        heightField.setMaxStringLength(1);
        heightField.setText(Integer.toString(Math.min(controller.drop,com.vandorlabs.ramp.ControllerPlatform.MAX_DROP)));
        heightField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,2}"));
        heightField.setDisabledTextColour(0xADBECA);
        channelField=new GuiTextField(8,fontRenderer,guiLeft+244,guiTop+206,54,18);
        channelField.setMaxStringLength(10);
        channelField.setText(Integer.toString(controller.getRedstoneChannel()));
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+66,142,20,""));
        buttonList.add(new GuiButton(2,guiLeft+164,guiTop+66,142,20,""));
        buttonList.add(new GuiButton(3,guiLeft+14,guiTop+92,142,20,""));
        buttonList.add(new GuiButton(6,guiLeft+164,guiTop+92,142,20,""));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+118,142,20,""));
        buttonList.add(new GuiButton(5,guiLeft+164,guiTop+118,142,20,""));
        buttonList.add(new GuiButton(7,guiLeft+14,guiTop+232,292,20,"Done"));
        refresh();
    }
    private void refresh() {
        for (GuiButton b:buttonList) {
            if (b.id==1) b.displayString=elevator?"Mode: elevator":"Mode: ramp";
            if (b.id==2) b.displayString=top?"Top: lower":"Bottom: raise";
            if (b.id==3) b.displayString=powerOn?"Trigger: redstone ON":"Trigger: redstone OFF";
            if (b.id==6) b.displayString="Ramp direction: "+direction.getName().toUpperCase(java.util.Locale.ROOT);
            if (b.id==4) b.displayString=slow?"Base speed: slow":"Base speed: fast";
            if (b.id==5) { b.displayString=segments==2?"Treads: stairs":"Treads: smooth"; b.enabled=!elevator; }
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==1) elevator=!elevator;
        if (button.id==2) top=!top;
        if (button.id==3) powerOn=!powerOn;
        if (button.id==4) slow=!slow;
        if (button.id==5) segments=segments==2?8:2;
        if (button.id==6) direction=direction.rotateY();
        if (button.id==7) mc.player.closeScreen();
        if (button.id>=1 && button.id<=6) {
            if (height()<1 || height()>com.vandorlabs.ramp.ControllerPlatform.MAX_DROP)
                heightField.setText(Integer.toString(Math.min(controller.drop,com.vandorlabs.ramp.ControllerPlatform.MAX_DROP)));
            submit();
        }
        refresh();
    }
    private void submit() {
            int height=height();
            if (height>=1 && height<=com.vandorlabs.ramp.ControllerPlatform.MAX_DROP && channel()>=0) PacketHandler.INSTANCE.sendToServer(
                    new MessageRampController(controller.getPos(),height,segments,top,powerOn,slow,elevator,direction,channel()));
    }
    private int height() {
        try { return Integer.parseInt(heightField.getText()); } catch (NumberFormatException e) { return 0; }
    }
    private int channel() {
        try { long value=Long.parseLong(channelField.getText()); return value<=Integer.MAX_VALUE?(int)value:-1; }
        catch (NumberFormatException e) { return -1; }
    }
    @Override public void updateScreen() {
        super.updateScreen(); heightField.updateCursorCounter(); channelField.updateCursorCounter();
        if (lastUpdate!=controller.clientUpdates) {
            lastUpdate=controller.clientUpdates;
            if (controller.error) {
                // A rejected reset retains old server settings; do not display unsaved toggles.
                segments=controller.segments; top=controller.top; powerOn=controller.activateOnPower;
                slow=controller.slow; elevator=controller.elevator;
                direction=controller.rampDirection();
                heightField.setText(Integer.toString(Math.min(controller.drop,com.vandorlabs.ramp.ControllerPlatform.MAX_DROP))); refresh();
            }
        }
    }
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if (keyCode==Keyboard.KEY_ESCAPE) { super.keyTyped(typedChar,keyCode); return; }
        String before=heightField.getText();
        String channelBefore=channelField.getText();
        if (!heightField.textboxKeyTyped(typedChar,keyCode)
                && !channelField.textboxKeyTyped(typedChar,keyCode)) super.keyTyped(typedChar,keyCode);
        if (!before.equals(heightField.getText()) || !channelBefore.equals(channelField.getText())) submit();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        super.mouseClicked(x,y,button); heightField.mouseClicked(x,y,button); channelField.mouseClicked(x,y,button);
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xFF304858);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString("Ramp / Elevator Controller",14,10,0xFFFFFF);
        fontRenderer.drawString("Vertical travel (1–8 blocks)",14,42,0xDAE8F0);
        fontRenderer.drawString("Max: 8 wide × 8 long × 8 vertical. Extras ignored.",14,147,0xADBECA);
        fontRenderer.drawSplitString((controller.error?"Error: ":"")+controller.status,
                14,162,292,controller.error?0xFF9988:0xE5C76B);
        fontRenderer.drawString("Channel (0 = none)",144,211,0xDAE8F0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX,mouseY,partial); heightField.drawTextBox(); channelField.drawTextBox();
    }
}
