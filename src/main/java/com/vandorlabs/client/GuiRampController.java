package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.ramp.ControllerPlatform;
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
    private GuiTextField endField,startField;
    private GuiTextField channelField;
    private GuiTextField treadField;
    private boolean powerOn,elevator,extendSegments;
    private int travelAxis,speed;
    private int lastUpdate;
    private net.minecraft.util.EnumFacing direction;
    public GuiRampController(TileEntityRampController controller) {
        super(new ContainerRampController(controller));
        this.controller=controller;
        powerOn=controller.activateOnPower;
        speed=controller.speed; elevator=controller.elevator;
        travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
        lastUpdate=controller.clientUpdates;
        direction=controller.rampDirection();
        xSize=320; ySize=232;
    }
    @Override public void initGui() {
        super.initGui(); Keyboard.enableRepeatEvents(true);
        startField=offsetField(0,30,controller.startOffset);
        endField=offsetField(9,54,controller.endOffset());
        buttonList.add(new GuiButton(10,guiLeft+266,guiTop+29,20,20,"-"));
        buttonList.add(new GuiButton(11,guiLeft+288,guiTop+29,20,20,"+"));
        buttonList.add(new GuiButton(12,guiLeft+266,guiTop+53,20,20,"-"));
        buttonList.add(new GuiButton(13,guiLeft+288,guiTop+53,20,20,"+"));
        treadField=new GuiTextField(16,fontRenderer,guiLeft+216,guiTop+127,36,18);
        treadField.setMaxStringLength(2);
        treadField.setValidator(text -> text.matches("[0-9]{0,2}"));
        treadField.setText(Integer.toString(controller.treadPixels));
        buttonList.add(new GuiButton(14,guiLeft+260,guiTop+126,20,20,"-"));
        buttonList.add(new GuiButton(15,guiLeft+282,guiTop+126,20,20,"+"));
        channelField=new GuiTextField(8,fontRenderer,guiLeft+244,guiTop+184,54,18);
        channelField.setMaxStringLength(10);
        channelField.setText(Integer.toString(controller.getRedstoneChannel()));
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+78,142,20,""));
        buttonList.add(new GuiButton(3,guiLeft+14,guiTop+102,142,20,""));
        buttonList.add(new GuiButton(6,guiLeft+164,guiTop+78,142,20,""));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+126,142,20,""));
        buttonList.add(new GuiButton(17,guiLeft+164,guiTop+102,142,20,""));
        buttonList.add(new GuiButton(7,guiLeft+14,guiTop+208,292,20,"Done"));
        refresh();
    }
    private void refresh() {
        treadField.setEnabled(!elevator);
        for (GuiButton b:buttonList) {
            if (b.id==14 || b.id==15) b.enabled=!elevator;
            if (b.id==1) b.displayString=elevator
                    ?(extendSegments?"Mode: extend":"Mode: lift")
                    :(extendSegments?"Mode: filled ramp":"Mode: ramp");
            if (b.id==3) b.displayString=powerOn?"Trigger: redstone ON":"Trigger: redstone OFF";
            if (b.id==6) b.displayString="Ramp direction: "+direction.getName().toUpperCase(java.util.Locale.ROOT);
            if (b.id==4) b.displayString="Base speed: "+(speed==0?"fast":speed==1?"medium":"slow");
            if (b.id==17) b.displayString="Travel: "+(travelAxis==0?"up / down":"left / right");
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id==1) {
            if (!elevator && !extendSegments) extendSegments=true;
            else if (!elevator) { elevator=true; extendSegments=false; }
            else if (!extendSegments) extendSegments=true;
            else { elevator=false; extendSegments=false; }
        }
        if (button.id==3) powerOn=!powerOn;
        if (button.id==4) speed=(speed+1)%3;
        if (button.id==6) direction=direction.rotateY();
        if (button.id==17) travelAxis=travelAxis==0?2:0;
        if (button.id==7) mc.player.closeScreen();
        if (button.id>=10 && button.id<=13) {
            GuiTextField field=button.id<12?startField:endField;
            int value=parseOffset(field);
            if (value==Integer.MIN_VALUE) value=button.id<12?controller.startOffset:controller.endOffset();
            field.setText(Integer.toString(Math.max(-8,Math.min(8,value+(button.id%2==0?-1:1)))));
        }
        if (button.id==14 || button.id==15) {
            int pixels=parseOffset(treadField);
            if (pixels<1 || pixels>16) pixels=controller.treadPixels;
            treadField.setText(Integer.toString(ControllerPlatform.stepTreadPixels(pixels,button.id==15)));
        }
        if (button.id!=7) submit();
        refresh();
    }
    private GuiTextField offsetField(int id,int y,int value) {
        GuiTextField field=new GuiTextField(id,fontRenderer,guiLeft+220,guiTop+y,40,18);
        field.setMaxStringLength(2);
        field.setValidator(text -> text.matches("-?[0-9]?"));
        field.setText(Integer.toString(value));
        return field;
    }
    private int parseOffset(GuiTextField field) {
        try { return Integer.parseInt(field.getText()); }
        catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }
    private void submit() {
        int start=parseOffset(startField),end=parseOffset(endField),pixels=parseOffset(treadField);
        if (start>=-8 && start<=8 && end>=-8 && end<=8 && ControllerPlatform.validTreadPixels(pixels) && channel()>=0)
            PacketHandler.INSTANCE.sendToServer(new MessageRampController(controller.getPos(),start,end,
                    pixels,powerOn,speed==2,elevator,direction,channel(),travelAxis,extendSegments,speed));
    }
    private int channel() {
        try { long value=Long.parseLong(channelField.getText()); return value<=Integer.MAX_VALUE?(int)value:-1; }
        catch (NumberFormatException e) { return -1; }
    }
    @Override public void updateScreen() {
        super.updateScreen(); endField.updateCursorCounter(); startField.updateCursorCounter(); treadField.updateCursorCounter(); channelField.updateCursorCounter();
        if (lastUpdate!=controller.clientUpdates) {
            lastUpdate=controller.clientUpdates;
            if (controller.error) {
                // A rejected reset retains old server settings; do not display unsaved toggles.
                powerOn=controller.activateOnPower;
                speed=controller.speed; elevator=controller.elevator;
                travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
                direction=controller.rampDirection();
                endField.setText(Integer.toString(controller.endOffset()));
                startField.setText(Integer.toString(controller.startOffset));
                treadField.setText(Integer.toString(controller.treadPixels)); refresh();
            }
        }
    }
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if (keyCode==Keyboard.KEY_ESCAPE) { super.keyTyped(typedChar,keyCode); return; }
        String before=endField.getText(),startBefore=startField.getText();
        String channelBefore=channelField.getText(),treadBefore=treadField.getText();
        if (!endField.textboxKeyTyped(typedChar,keyCode)
                && !treadField.textboxKeyTyped(typedChar,keyCode)
                && !startField.textboxKeyTyped(typedChar,keyCode)
                && !channelField.textboxKeyTyped(typedChar,keyCode)) super.keyTyped(typedChar,keyCode);
        if (!treadBefore.equals(treadField.getText()) || !startBefore.equals(startField.getText()) || !before.equals(endField.getText()) || !channelBefore.equals(channelField.getText())) submit();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        super.mouseClicked(x,y,button); endField.mouseClicked(x,y,button); startField.mouseClicked(x,y,button); treadField.mouseClicked(x,y,button); channelField.mouseClicked(x,y,button);
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xFF304858);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString("Programmable Ramp",14,10,0xFFFFFF);
        fontRenderer.drawString("Start / off offset (-8 to 8)",14,35,0xDAE8F0);
        fontRenderer.drawString("End / on offset (-8 to 8)",14,59,0xDAE8F0);
        fontRenderer.drawString("Tread px",164,132,elevator?0x78848C:0xDAE8F0);
        fontRenderer.drawString(travelAxis==0?"Positive = up; negative = down. Footprint: 8x16.":
                "Positive = right; negative = left. Footprint: 8x16.",14,153,0xADBECA);
        fontRenderer.drawSplitString((controller.error?"Error: ":"")+controller.status,
                14,166,292,controller.error?0xFF9988:0xE5C76B);
        fontRenderer.drawString("Channel (0 = none)",144,188,0xDAE8F0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX,mouseY,partial); endField.drawTextBox(); startField.drawTextBox(); treadField.drawTextBox(); channelField.drawTextBox();
    }
}
