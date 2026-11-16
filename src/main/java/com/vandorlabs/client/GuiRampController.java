package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ui.RampDialog;
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
    private boolean verticalLayout,analogDeployment,powerOn,elevator,extendSegments,matchTextures,moat;
    private int travelAxis,speed,interpolation,escalator;
    private int lastUpdate,page;
    private net.minecraft.util.EnumFacing direction;
    public GuiRampController(TileEntityRampController controller) {
        super(new ContainerRampController(controller));
        this.controller=controller;
        verticalLayout=controller.verticalLayout;analogDeployment=controller.analogDeployment;powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;moat=controller.moat;
        escalator=controller.escalator;interpolation=controller.interpolation;speed=controller.speed; elevator=controller.elevator;
        travelAxis=controller.travelAxis; extendSegments=controller.extendSegments;
        lastUpdate=controller.clientUpdates;
        direction=controller.rampDirection();
        lastStartHalf=controller.startHalfSteps(); lastEndHalf=controller.endHalfSteps();
        xSize=RampDialog.WIDTH; ySize=RampDialog.HEIGHT;
    }
    RampDialog.State editingState(){return dialogState();}
    GuiButton control(int id){for(GuiButton button:buttonList)if(button.id==id)return button;return null;}
    GuiTextField channelEditor(){return channelField;}
    int dialogLeft(){return guiLeft;}int dialogTop(){return guiTop;}
    boolean controlsFit(){if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height)return false;for(GuiButton b:buttonList)if(b.visible&&(b.x<guiLeft||b.y<guiTop||b.x+b.width>guiLeft+xSize||b.y+b.height>guiTop+ySize))return false;return true;}
    private RampDialog.State dialogState(){
        RampDialog.State state=new RampDialog.State();state.start=startSlider==null?lastStartHalf:startSlider.getValueInt();state.end=endSlider==null?lastEndHalf:endSlider.getValueInt();
        state.tread=treadField==null?controller.treadPixels:parseOffset(treadField);if(!ControllerPlatform.validTreadPixels(state.tread))state.tread=controller.treadPixels;
        state.power=powerOn;state.elevator=elevator;state.extend=extendSegments;state.speed=speed;state.match=matchTextures;state.interpolation=interpolation;state.escalator=escalator;state.moat=moat;state.vertical=verticalLayout;state.travel=travelAxis;state.direction=direction.getIndex();state.analog=analogDeployment;
        state.scanFacing=com.vandorlabs.blocks.BlockRampController.scanFacing(controller.getWorld().getBlockState(controller.getPos())).getIndex();return state;
    }
    @Override public void initGui() {
        RampDialog.Panel panel=new RampDialog.Panel(height);ySize=panel.height;super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        RampDialog.State state=dialogState();
        RampDialog.Control start=RampDialog.control(panel,state,0,20),end=RampDialog.control(panel,state,0,21);
        startSlider=new GuiSlider(20,guiLeft+start.x,guiTop+start.y,start.width,start.height,"","",start.minimum,start.maximum,state.start,false,true,slider->{int half=slider.getValueInt();slider.setValue(half);slider.displayString=RampDialog.offsetLabel("Start / off",half);});
        endSlider=new GuiSlider(21,guiLeft+end.x,guiTop+end.y,end.width,end.height,"","",end.minimum,end.maximum,state.end,false,true,slider->{int half=slider.getValueInt();slider.setValue(half);slider.displayString=RampDialog.offsetLabel(escalator==0?"End / on":"Ramp height",half);});
        buttonList.add(startSlider);buttonList.add(endSlider);
        RampDialog.Control tread=RampDialog.control(panel,state,0,16),channels=RampDialog.control(panel,state,1,8);
        treadField=new GuiTextField(16,fontRenderer,guiLeft+tread.x,guiTop+tread.y,tread.width,tread.height);treadField.setMaxStringLength(2);treadField.setValidator(text->text.matches("[0-9]{0,2}"));treadField.setText(Integer.toString(controller.treadPixels));
        channelField=new GuiTextField(8,fontRenderer,guiLeft+channels.x,guiTop+channels.y,channels.width,channels.height);ChannelFields.configure(channelField);channelField.setText(ChannelFields.text(controller.getRedstoneChannels()));
        java.util.Set<Integer> seen=new java.util.HashSet<>();
        for(int p=0;p<2;p++)for(RampDialog.Control control:RampDialog.controls(panel,state,p))if(control.kind==RampDialog.Kind.BUTTON&&seen.add(control.id)){
            GuiButton button=control.tab?new DialogTabButton(control.id,guiLeft+control.x,guiTop+control.y,control.width,control.height,control.label)
                    :control.id==3?new RedstoneActivationButton(3,guiLeft+control.x,guiTop+control.y,control.width,powerOn?1:2)
                    :new GuiButton(control.id,guiLeft+control.x,guiTop+control.y,control.width,control.height,control.label);
            buttonList.add(button);
        }
        refresh();
    }
    private void refresh() {
        RampDialog.Panel panel=new RampDialog.Panel(height);RampDialog.State state=dialogState();
        java.util.Map<Integer,RampDialog.Control> visible=new java.util.HashMap<>();for(RampDialog.Control control:RampDialog.controls(panel,state,page))visible.put(control.id,control);
        for(GuiButton button:buttonList){RampDialog.Control control=visible.get(button.id);button.visible=control!=null;if(control==null)continue;button.x=guiLeft+control.x;button.y=guiTop+control.y;button.width=control.width;button.height=control.height;button.enabled=control.enabled;button.displayString=control.label;}
        RampDialog.Control tread=RampDialog.control(panel,state,0,16),channels=RampDialog.control(panel,state,1,8);
        treadField.x=guiLeft+tread.x;treadField.y=guiTop+tread.y;treadField.width=tread.width;treadField.setVisible(page==0);treadField.setEnabled(tread.enabled);
        channelField.x=guiLeft+channels.x;channelField.y=guiTop+channels.y;channelField.width=channels.width;channelField.setVisible(page==1);
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id>=30){page=button.id-30;refresh();return;}
        if(button.id==23){return;}
        RampDialog.State state=RampDialog.action(dialogState(),button.id,GuiOptionCycle.direction());
        powerOn=state.power;elevator=state.elevator;extendSegments=state.extend;speed=state.speed;matchTextures=state.match;interpolation=state.interpolation;escalator=state.escalator;moat=state.moat;verticalLayout=state.vertical;travelAxis=state.travel;direction=net.minecraft.util.EnumFacing.getFront(state.direction);analogDeployment=state.analog;
        startSlider.setValue(state.start);endSlider.setValue(state.end);
        if(button.id==14||button.id==15)treadField.setText(Integer.toString(state.tread));
        if(button.id==7)mc.player.closeScreen();
        if (button.id!=7 && button.id!=20 && button.id!=21) submit();
        refresh();
    }
    private static String offsetLabel(String label,int half) {
        return RampDialog.offsetLabel(label,half);
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
                    pixels,powerOn,speed==2,elevator,direction,channel(),travelAxis,extendSegments,speed,matchTextures).withVerticalLayout(verticalLayout).withAnalogDeployment(analogDeployment).withEscalator(escalator).withMoat(moat).withInterpolation(interpolation).withChannels(ChannelFields.parse(channelField)));
        }
    }
    private int channel() {return ChannelFields.first(channelField);}
    @Override public void updateScreen() {
        super.updateScreen(); treadField.updateCursorCounter(); channelField.updateCursorCounter();
        if (lastUpdate!=controller.clientUpdates) {
            lastUpdate=controller.clientUpdates;
            if (controller.error) {
                // A rejected reset retains old server settings; do not display unsaved toggles.
                verticalLayout=controller.verticalLayout;analogDeployment=controller.analogDeployment;powerOn=controller.activateOnPower; matchTextures=controller.matchTextures;moat=controller.moat;
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
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,RampDialog.reverseOperations()))return;
        super.mouseClicked(x,y,button); treadField.mouseClicked(x,y,button); channelField.mouseClicked(x,y,button);
    }
    @Override protected void mouseReleased(int x,int y,int button) {
        super.mouseReleased(x,y,button);
        if (startSlider.getValueInt()!=lastStartHalf || endSlider.getValueInt()!=lastEndHalf) submit();
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,RampDialog.STYLE.body);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+RampDialog.STYLE.headerHeight,RampDialog.STYLE.header);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString(controller.getBlockType().getLocalizedName(),RampDialog.STYLE.titleX,RampDialog.STYLE.titleY,0xFFFFFF);
        DialogHeader.logo(0,0,xSize);
        if(page==0)fontRenderer.drawString(RampDialog.TREAD_LABEL,164,new RampDialog.Panel(height).treadLabelY(),elevator?0x78848C:0xDAE8F0);
        fontRenderer.drawSplitString((controller.error?"Error: ":"")+controller.status,
                14,ySize-24,292,controller.error?0xFF9988:0xE5C76B);
        if(page==1)fontRenderer.drawString(RampDialog.RECEIVE_LABEL,14,54,0xDAE8F0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX,mouseY,partial);DialogOverlay.prepare(); treadField.drawTextBox(); channelField.drawTextBox();
    }
}
