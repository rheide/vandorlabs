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
    private boolean analogDeployment,powerOn,elevator,extendSegments,matchTextures,moat;
    private int travelAxis,speed,interpolation,escalator;
    private int lastUpdate,page;
    private net.minecraft.util.EnumFacing direction;
    public GuiRampController(TileEntityRampController controller) {
        super(new ContainerRampController(controller));
        this.controller=controller;
        analogDeployment=controller.analogDeployment;powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;moat=controller.moat;
        escalator=controller.escalator;interpolation=controller.interpolation;speed=controller.speed; elevator=controller.elevator;
        travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
        lastUpdate=controller.clientUpdates;
        direction=controller.rampDirection();
        lastStartHalf=controller.startHalfSteps(); lastEndHalf=controller.endHalfSteps();
        xSize=320; ySize=248;
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
            slider.displayString=offsetLabel(escalator==0?"End / on":"Ramp height",half);
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
        channelField=new GuiTextField(8,fontRenderer,guiLeft+200,guiTop+208,98,18);
        ChannelFields.configure(channelField);
        channelField.setText(ChannelFields.text(controller.getRedstoneChannels()));

        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+78,142,20,""));
        buttonList.add(new RedstoneActivationButton(3,guiLeft+14,guiTop+102,142,powerOn?1:2));
        buttonList.add(new GuiButton(6,guiLeft+164,guiTop+78,142,20,""));
        buttonList.add(new GuiButton(4,guiLeft+14,guiTop+126,142,20,""));
        buttonList.add(new GuiButton(17,guiLeft+164,guiTop+102,142,20,""));
        buttonList.add(new GuiButton(19,guiLeft+14,guiTop+151,292,20,""));
        buttonList.add(new GuiButton(22,guiLeft+14,guiTop+176,292,20,""));
        buttonList.add(new GuiButton(18,guiLeft+14,guiTop+207,122,20,""));
        buttonList.add(new GuiButton(7,guiLeft+14,guiTop+232,292,20,"Done"));
        for(GuiButton button:buttonList)button.y+=26;
        startSlider.y=guiTop+56;endSlider.y=guiTop+80;
        treadField.y+=26;channelField.y=guiTop+120;

        buttonList.add(new DialogTabButton(30,guiLeft+14,guiTop+30,140,20,"Ramp"));
        buttonList.add(new DialogTabButton(31,guiLeft+164,guiTop+30,142,20,"Redstone"));
        refresh();
    }
    private void refresh() {
        treadField.setVisible(page==0);channelField.setVisible(page==1);
        for(GuiButton button:buttonList){
            boolean redstone=button.id==3||button.id==22||button.id==23;
            button.visible=button.id>=30||button.id==7||((page==1)==redstone);
            if(button.id>=30)button.enabled=button.id!=30+page;
            if(button.id==3){button.x=guiLeft+14;button.y=guiTop+60;button.width=292;}
            if(button.id==22)button.y=guiTop+86;
            if(button.id==7){button.x=guiLeft+(page==1?184:164);button.y=guiTop+200;button.width=page==1?122:142;}
            if(button.id==18){button.x=guiLeft+14;button.y=guiTop+200;button.width=142;}
            if(button.id==4)button.y=guiTop+128;
            if(button.id==14||button.id==15)button.y=guiTop+152;
            if(button.id==19)button.y=guiTop+176;

        }
        treadField.setEnabled(!elevator);
        startSlider.enabled=escalator==0;
        endSlider.displayString=offsetLabel(escalator==0?"End / on":"Ramp height",endSlider.getValueInt());
        for (GuiButton b:buttonList) {
            if(b.id==22){b.enabled=escalator==0;b.displayString="Deployment: "+(analogDeployment?"Analog (0-15)":"Digital (on/off)");}
            if (b.id==14 || b.id==15) b.enabled=!elevator;
            if (b.id==1) b.displayString=moat?"Mode: Moat":escalator!=0?(escalator==1?"Mode: escalator up":"Mode: escalator down"):elevator
                    ?(extendSegments?"Mode: extend":"Mode: lift")
                    :(extendSegments?"Mode: filled ramp":"Mode: ramp");
            if (b.id==3) ((RedstoneActivationButton)b).setMode(powerOn?1:2);
            if (b.id==6) b.displayString="Ramp direction: "+direction.getName().toUpperCase(java.util.Locale.ROOT);
            if (b.id==4) b.displayString="Base speed: "+(speed==0?"fast":speed==1?"medium":"slow");
            if (b.id==18) b.displayString="Match textures: "+(matchTextures?"On":"Off");
            if(b.id==19){b.enabled=!elevator;b.displayString="Interpolation: "+new String[]{"Linear","Curve In","Curve Out"}[interpolation];}
            if (b.id==17) {b.enabled=escalator==0&&!moat;b.displayString="Travel: "+(travelAxis==0?"up / down":"left / right");}
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id>=30){page=button.id-30;refresh();return;}
        if(button.id==23){PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(controller.getPos(),0));return;}
        if (button.id==1) {
            int mode=GuiOptionCycle.next(moat?6:escalator!=0?escalator+3:elevator?(extendSegments?3:2):(extendSegments?1:0),7);
            moat=mode==6;escalator=mode>=4&&mode<6?mode-3:0;
            elevator=mode==2 || mode==3;extendSegments=mode<4 && (mode&1)!=0;
            if(escalator!=0){travelAxis=0;startSlider.setValue(0);}if(moat)travelAxis=0;
        }
        if(button.id==22)analogDeployment=!analogDeployment;
        if (button.id==3) powerOn=!powerOn;
        if (button.id==4) speed=GuiOptionCycle.next(speed,3);
        if (button.id==6) direction=GuiOptionCycle.next(direction);
        if(button.id==19) interpolation=GuiOptionCycle.next(interpolation,3);
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
                    pixels,powerOn,speed==2,elevator,direction,channel(),travelAxis,extendSegments,speed,matchTextures).withAnalogDeployment(analogDeployment).withEscalator(escalator).withMoat(moat).withInterpolation(interpolation).withChannels(ChannelFields.parse(channelField)));
        }
    }
    private int channel() {return ChannelFields.first(channelField);}
    @Override public void updateScreen() {
        super.updateScreen(); treadField.updateCursorCounter(); channelField.updateCursorCounter();
        if (lastUpdate!=controller.clientUpdates) {
            lastUpdate=controller.clientUpdates;
            if (controller.error) {
                // A rejected reset retains old server settings; do not display unsaved toggles.
                analogDeployment=controller.analogDeployment;powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;moat=controller.moat;
                escalator=controller.escalator;interpolation=controller.interpolation;speed=controller.speed; elevator=controller.elevator;
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
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,3,4,6,17,18,19,22))return;
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
        fontRenderer.drawString(controller.getBlockType().getLocalizedName(),14,10,0xFFFFFF);
        DialogHeader.logo(0,0,xSize);
        if(page==0)fontRenderer.drawString("Tread px",164,158,elevator?0x78848C:0xDAE8F0);
        fontRenderer.drawSplitString((controller.error?"Error: ":"")+controller.status,
                14,224,292,controller.error?0xFF9988:0xE5C76B);
        if(page==1)fontRenderer.drawString("Channels",144,124,0xDAE8F0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX,mouseY,partial); treadField.drawTextBox(); channelField.drawTextBox();
    }
}
