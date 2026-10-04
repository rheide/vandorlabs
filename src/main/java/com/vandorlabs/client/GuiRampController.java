package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.network.MessageRampController;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public class GuiRampController extends GuiContainer {
    private final TileEntityRampController controller;
    private GuiSlider startSlider,endSlider;
    private int lastStartHalf,lastEndHalf;
    private GuiTextField channelField;
    private GuiTextField treadField;
    private boolean powerOn,elevator,extendSegments,matchTextures;
    private int travelAxis,speed;
    private int lastUpdate;
    private net.minecraft.util.EnumFacing direction;
    public GuiRampController(TileEntityRampController controller) {
        super(new ContainerRampController(controller));
        this.controller=controller;
        powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;
        speed=controller.speed; elevator=controller.elevator;
        travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
        lastUpdate=controller.clientUpdates;
        direction=controller.rampDirection();
        lastStartHalf=controller.startHalfSteps(); lastEndHalf=controller.endHalfSteps();
        xSize=320; ySize=232;
    }
    @Override public void initGui() {
        super.initGui(); buttonList.clear(); Keyboard.enableRepeatEvents(true);
        startSlider=new GuiSlider(20,guiLeft+14,guiTop+30,292,20,"","",
                -ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                controller.startHalfSteps(),false,true,slider->{
            int half=slider.getValueInt();slider.setValue(half);
            slider.displayString=offsetLabel("Start / off",half);
        });
        endSlider=new GuiSlider(21,guiLeft+14,guiTop+54,292,20,"","",
                -ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                controller.endHalfSteps(),false,true,slider->{
            int half=slider.getValueInt();slider.setValue(half);
            slider.displayString=offsetLabel("End / on",half);
        });
        startSlider.displayString=offsetLabel("Start / off",controller.startHalfSteps());
        endSlider.displayString=offsetLabel("End / on",controller.endHalfSteps());
        buttonList.add(startSlider);buttonList.add(endSlider);
        treadField=new GuiTextField(16,fontRenderer,guiLeft+216,guiTop+127,36,18);
        treadField.setMaxStringLength(2);
        treadField.setValidator(text -> text.matches("[0-9]{0,2}"));
        treadField.setText(Integer.toString(controller.treadPixels));
        buttonList.add(new GuiButton(14,guiLeft+260,guiTop+126,20,20,"-"));
        buttonList.add(new GuiButton(15,guiLeft+282,guiTop+126,20,20,"+"));
        channelField=new GuiTextField(8,fontRenderer,guiLeft+200,guiTop+184,98,18);
        ChannelFields.configure(channelField);
        channelField.setText(controller.getRedstoneChannels().toString());

        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+78,142,20,""));
        buttonList.add(new GuiButton(3,guiLeft+14,guiTop+102,142,20,""));
        buttonList.add(new GuiButton(6,guiLeft+164,guiTop+78,142,20,""));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+126,142,20,""));
        buttonList.add(new GuiButton(17,guiLeft+164,guiTop+102,142,20,""));
        buttonList.add(new GuiButton(18,guiLeft+14,guiTop+183,122,20,""));
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
            if (b.id==18) b.displayString="Match textures: "+(matchTextures?"On":"Off");
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
        if (button.id==18) matchTextures=!matchTextures;
        if (button.id==17) travelAxis=travelAxis==0?2:0;
        if (button.id==7) mc.player.closeScreen();
        if (button.id==14 || button.id==15) {
            int pixels=parseOffset(treadField);
            if (pixels<1 || pixels>16) pixels=controller.treadPixels;
            treadField.setText(Integer.toString(ControllerPlatform.stepTreadPixels(pixels,button.id==15)));
        }
        if (button.id!=7 && button.id!=20 && button.id!=21) submit();
        refresh();
    }
    private static String offsetLabel(String label,int half) {
        return label+" offset: "+(half/2D)+" blocks";
    }
    private int parseOffset(GuiTextField field) {
        try { return Integer.parseInt(field.getText()); }
        catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }
    private void submit() {
        int start=startSlider.getValueInt(),end=endSlider.getValueInt(),pixels=parseOffset(treadField);
        if (Math.abs(start)<=ControllerPlatform.MAX_OFFSET_HALF_STEPS
                && Math.abs(end)<=ControllerPlatform.MAX_OFFSET_HALF_STEPS
                && ControllerPlatform.validTreadPixels(pixels) && channel()>=0) {
            lastStartHalf=start;lastEndHalf=end;
            PacketHandler.INSTANCE.sendToServer(MessageRampController.halfOffsets(controller.getPos(),start,end,
                    pixels,powerOn,speed==2,elevator,direction,channel(),travelAxis,extendSegments,speed,matchTextures).withChannels(ChannelFields.parse(channelField)));
        }
    }
    private int channel() {return ChannelFields.first(channelField);}
    @Override public void updateScreen() {
        super.updateScreen(); treadField.updateCursorCounter(); channelField.updateCursorCounter();
        if (lastUpdate!=controller.clientUpdates) {
            lastUpdate=controller.clientUpdates;
            if (controller.error) {
                // A rejected reset retains old server settings; do not display unsaved toggles.
                powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;
                speed=controller.speed; elevator=controller.elevator;
                travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
                direction=controller.rampDirection();
                lastStartHalf=controller.startHalfSteps();lastEndHalf=controller.endHalfSteps();
                startSlider.setValue(lastStartHalf);endSlider.setValue(lastEndHalf);
                startSlider.displayString=offsetLabel("Start / off",lastStartHalf);
                endSlider.displayString=offsetLabel("End / on",lastEndHalf);
                treadField.setText(Integer.toString(controller.treadPixels)); refresh();
            }
        }
    }
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if (keyCode==Keyboard.KEY_ESCAPE) { super.keyTyped(typedChar,keyCode); return; }
        String channelBefore=channelField.getText(),treadBefore=treadField.getText();
        if (!treadField.textboxKeyTyped(typedChar,keyCode)
                && !channelField.textboxKeyTyped(typedChar,keyCode)) super.keyTyped(typedChar,keyCode);
        if (!treadBefore.equals(treadField.getText()) || !channelBefore.equals(channelField.getText())) submit();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        super.mouseClicked(x,y,button); treadField.mouseClicked(x,y,button); channelField.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button) {
        super.mouseReleased(x,y,button);
        if (startSlider.getValueInt()!=lastStartHalf || endSlider.getValueInt()!=lastEndHalf) submit();
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+28,0xFF304858);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString("Programmable Ramp",14,10,0xFFFFFF);
        fontRenderer.drawString("Tread px",164,132,elevator?0x78848C:0xDAE8F0);
        fontRenderer.drawString(travelAxis==0?"Positive = up; negative = down. Footprint: 8x16.":
                "Positive = right; negative = left. Footprint: 8x16.",14,153,0xADBECA);
        fontRenderer.drawSplitString((controller.error?"Error: ":"")+controller.status,
                14,166,292,controller.error?0xFF9988:0xE5C76B);
        fontRenderer.drawString("Channels",144,188,0xDAE8F0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX,mouseY,partial); treadField.drawTextBox(); channelField.drawTextBox();
    }
}
