package com.vandorlabs.client;

import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.MessageRedstoneChannel;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class GuiRedstoneChannel extends GuiContainer {
    private final RedstoneChannelMember member;
    private GuiTextField channelField;private int page=1;
    private boolean generalPage(){return thruster||pilotSeat||adjustableBase||powerLever||selectableType;}
    private final boolean signalControl;
    private boolean transmitterControl(){return member instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel;}
    private final boolean powerLever;
    private int powerLeverSize;
    private int lowLimit=5,highLimit=15,baseHeight,baseTilt,tiltDirection;
    private boolean adjustableBase,selectableType;
    private int controlType;
    private final boolean thruster;
    private int particleLevel,threshold=8;private boolean signalBrightness;
    private boolean redstoneInverted;
    private boolean connected;
    private boolean join;
    private GuiButton joinButton;
    private final boolean programmableThruster;
    private int shape;
    private boolean shapeSubmitted,otherEdited;
    private int sideTexture;
    private HousingTextureList housingList;
    private ProgrammableDialogLayout layout;
    private GuiButton particleButton;
    private final boolean pilotSeat;
    private boolean taxiMode;private int pilotMode,pilotHeight;
    private String inspectionStatus="";
    public void inspection(net.minecraft.util.math.BlockPos pos,String status) {
        if(!pilotSeat || !member.channelTile().getPos().equals(pos))return;
        String draft=channelField==null?null:channelField.getText();inspectionStatus=status;ySize=274;initGui();if(draft!=null)channelField.setText(draft);
    }
    java.util.List<GuiButton> inspectionButtons(){return buttonList;}
    public String pilotChannelsDraft(){return channelField.getText();}
    public boolean pilotTaxiDraft(){return taxiMode;}
    public int pilotModeDraft(){return pilotMode;}
    public int pilotHeightDraft(){return pilotHeight;}


    public GuiRedstoneChannel(RedstoneChannelMember member) {
        super(new ContainerRedstoneChannel(member));
        this.member = member;
        pilotSeat=member instanceof com.vandorlabs.tiles.TileEntityPilotSeat;page=pilotSeat?0:1;
        if(pilotSeat){com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)member;taxiMode=seat.isTaxiMode();pilotMode=seat.getRedstoneMode();pilotHeight=seat.getHeight();}
        powerLever=member.channelTile().getWorld()!=null && member.channelTile().getWorld().getBlockState(member.channelTile().getPos()).getBlock() instanceof com.vandorlabs.blocks.BlockIndustrialLever;
        if(powerLever)powerLeverSize=((com.vandorlabs.tiles.TileEntityRedstoneChannel)member).getPowerLeverSize();
        signalControl=member instanceof com.vandorlabs.tiles.TileEntitySignalControl;
        if(signalControl){lowLimit=((com.vandorlabs.tiles.TileEntitySignalControl)member).getLowLimit();highLimit=((com.vandorlabs.tiles.TileEntitySignalControl)member).getHighLimit();}
        if(signalControl){com.vandorlabs.tiles.TileEntitySignalControl control=(com.vandorlabs.tiles.TileEntitySignalControl)member;
            adjustableBase=((com.vandorlabs.blocks.BlockSignalControl)control.getWorld().getBlockState(control.getPos()).getBlock()).hasAdjustableBase();
            controlType=control.getControlType();baseHeight=control.getBaseHeight();baseTilt=control.getBaseTilt();tiltDirection=control.getTiltDirection();}
        if(member instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel){
            com.vandorlabs.tiles.TileEntityRedstoneChannel control=(com.vandorlabs.tiles.TileEntityRedstoneChannel)member;
            net.minecraft.block.Block mounted=control.getWorld().getBlockState(control.getPos()).getBlock();
            adjustableBase=com.vandorlabs.blocks.MountedControlGeometry.supports(mounted);
            selectableType=mounted instanceof com.vandorlabs.blocks.BlockSignalControl && ((com.vandorlabs.blocks.BlockSignalControl)mounted).hasSelectableType();
            baseHeight=control.getBaseHeight();baseTilt=control.getBaseTilt();tiltDirection=control.getTiltDirection();
        }
        this.thruster = member instanceof TileEntityRedstoneLight
                && member.channelTile().getWorld() != null
                && member.channelTile().getWorld().getBlockState(member.channelTile().getPos())
                        .getBlock() instanceof BlockPropulsionLight;
        this.particleLevel = thruster?((TileEntityRedstoneLight)member).getParticleLevel():0;
        if(thruster){redstoneInverted=((TileEntityRedstoneLight)member).isRedstoneInverted();signalBrightness=((TileEntityRedstoneLight)member).isSignalBrightness();threshold=((TileEntityRedstoneLight)member).getParticleThreshold();}
        BlockPropulsionLight block = thruster ? (BlockPropulsionLight) member.channelTile()
                .getWorld().getBlockState(member.channelTile().getPos()).getBlock() : null;
        this.programmableThruster = block != null && !block.familyId().isEmpty();
        this.shape = programmableThruster ? block.shape() : 0;
        this.connected = block != null && block.hasJoinMode();
        this.join = thruster && ((TileEntityRedstoneLight) member).isJoin();
        page=generalPage()?0:1;
        this.sideTexture = thruster ? ((TileEntityRedstoneLight) member).getSideTexture() : 0;
        xSize = thruster ? 400 : 240;
        ySize = thruster ? 190 : pilotSeat?(!inspectionStatus.isEmpty()?274:202):adjustableBase?baseControlsY()+102:104;
    }

    @Override public void initGui() {
        String draft=channelField==null?member.getRedstoneChannels().toString():channelField.getText();
        if(thruster){layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;}else {xSize=280;ySize=pilotSeat?(!inspectionStatus.isEmpty()?300:226):adjustableBase?232:230;}
        super.initGui();buttonList.clear();housingList=null;Keyboard.enableRepeatEvents(true);
        int tx=thruster?layout.controlsX:guiLeft+116,tw=thruster?154:xSize-130;
        channelField=new GuiTextField(0,fontRenderer,tx,guiTop+64,tw,18);ChannelFields.configure(channelField);channelField.setText(draft);channelField.setVisible(page==1&&!transmitterControl());channelField.setFocused(page==1&&!transmitterControl());
        if(generalPage())buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+30,100,20,"General"));
        buttonList.add(new DialogTabButton(101,guiLeft+(generalPage()?116:12),guiTop+30,100,20,"Redstone"));
        for(GuiButton button:buttonList)button.enabled=button.id!=100+page;
        if(page==1){
            if(transmitterControl())buttonList.add(new GuiButton(90,guiLeft+14,guiTop+62,xSize-28,20,"Transmitter..."));

            if(signalControl){buttonList.add(new GuiButton(7,guiLeft+14,guiTop+116,122,20,"Low: "+lowLimit));buttonList.add(new GuiButton(8,guiLeft+144,guiTop+116,122,20,"High: "+highLimit));}
            if(thruster){buttonList.add(RedstoneActivationButton.from(layout.control(16,100,""),redstoneInverted?2:1));buttonList.add(layout.control(5,124,brightnessLabel()));buttonList.add(layout.control(6,148,thresholdLabel()));}
            if(pilotSeat)buttonList.add(new RedstoneActivationButton(18,guiLeft+14,guiTop+94,252,pilotMode));
        }else{
            if(thruster){housingList=new HousingTextureList(layout.listX,guiTop+64,layout.listWidth,sideTexture).visibleRows(Math.max(2,(ySize-98)/HousingTextureList.ROW_HEIGHT)).custom(value->{sideTexture=value;submit();});particleButton=layout.control(2,64,particleLabel());buttonList.add(particleButton);if(programmableThruster)buttonList.add(layout.control(4,88,shapeLabel()));refreshJoinButton();}
            if(adjustableBase){int y=guiTop+92;buttonList.add(new GuiButton(9,guiLeft+14,y,252,20,heightLabel()));buttonList.add(new GuiButton(10,guiLeft+14,y+24,122,20,tiltLabel()));buttonList.add(new GuiButton(11,guiLeft+144,y+24,122,20,directionLabel()));}
            if(selectableType)buttonList.add(new GuiButton(13,guiLeft+14,guiTop+64,252,20,typeLabel()));
            if(powerLever)buttonList.add(new GuiButton(12,guiLeft+14,guiTop+64,252,20,sizeLabel()));
            if(pilotSeat){buttonList.add(new GuiButton(15,guiLeft+14,guiTop+64,252,20,"Taxi Mode: "+(taxiMode?"ON":"OFF")));buttonList.add(new GuiButton(19,guiLeft+14,guiTop+88,252,20,pilotHeightLabel()));if(!inspectionStatus.isEmpty()){GuiButton assemble=new GuiButton(20,guiLeft+14,guiTop+236,252,20,"Assemble (inspection required)");assemble.enabled=false;buttonList.add(assemble);}}
        }
        buttonList.add(new GuiButton(1,guiLeft+xSize-166,guiTop+ySize-26,154,20,pilotSeat?"Close":"Done"));
    }

    private void refreshJoinButton() {
        if (joinButton != null) buttonList.remove(joinButton);
        joinButton = null;
        if (connected) {
            joinButton=layout.control(3,programmableThruster?112:88,joinLabel());
            buttonList.add(joinButton);
        }
    }

    protected int channel() {return ChannelFields.first(channelField);}

    protected void submit() {
        int value = channel();
        if(value>=0 && pilotSeat){com.vandorlabs.vehicle.VehicleNetwork.Packet setting=new com.vandorlabs.vehicle.VehicleNetwork.Packet(com.vandorlabs.vehicle.VehicleNetwork.TAXI);setting.origin=member.channelTile().getPos();setting.flag=taxiMode;PacketHandler.INSTANCE.sendToServer(setting);}
        if (value >= 0) PacketHandler.INSTANCE.sendToServer(
                new MessageRedstoneChannel(member.channelTile().getPos(), value,
                        thruster, particleLevel>0, connected, join, thruster, sideTexture,
                        programmableThruster, shape).withPilotSettings(pilotMode,pilotHeight).withParticleLevel(particleLevel).withChannels(ChannelFields.parse(channelField)).withRedstoneInverted(redstoneInverted).withSignalBrightness(signalBrightness,threshold).withControlLimits(signalControl,lowLimit,highLimit).withControlMount(adjustableBase,baseHeight,baseTilt,tiltDirection).withPowerLeverSize(powerLever,powerLeverSize).withControlType(selectableType,controlType));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==100||button.id==101){page=button.id-100;initGui();return;}
        if(button.id==90){submit();PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(member.channelTile().getPos(),0));return;}
        if(button.id!=1&&button.id!=4)otherEdited=true;

        if(button.id==18){pilotMode=GuiOptionCycle.next(pilotMode,3);((RedstoneActivationButton)button).setMode(pilotMode);}
        if(button.id==19){pilotHeight=GuiOptionCycle.next(pilotHeight,4);button.displayString=pilotHeightLabel();com.vandorlabs.vehicle.VehicleNetwork.Packet setting=new com.vandorlabs.vehicle.VehicleNetwork.Packet(com.vandorlabs.vehicle.VehicleNetwork.HEIGHT);setting.entity=-1;setting.origin=member.channelTile().getPos();setting.strafe=(byte)pilotHeight;PacketHandler.INSTANCE.sendToServer(setting);}
        if(button.id==16){redstoneInverted=!redstoneInverted;((RedstoneActivationButton)button).setMode(redstoneInverted?2:1);}
        if(button.id==15){taxiMode=!taxiMode;button.displayString="Taxi Mode: "+(taxiMode?"ON":"OFF");}
        if(button.id==13){controlType=GuiOptionCycle.next(controlType,3);button.displayString=typeLabel();}
        if(button.id==12){powerLeverSize=GuiOptionCycle.next(powerLeverSize,2);button.displayString=sizeLabel();}
        if(button.id==9){baseHeight=GuiOptionCycle.next(baseHeight,4);button.displayString=heightLabel();}
        if(button.id==10){baseTilt=GuiOptionCycle.next(baseTilt,4);button.displayString=tiltLabel();}
        if(button.id==11){tiltDirection=GuiOptionCycle.next(tiltDirection,4);button.displayString=directionLabel();}
        if(button.id==7){lowLimit=GuiOptionCycle.next(lowLimit,1,highLimit-2);button.displayString="Low: "+lowLimit;}
        if(button.id==8){highLimit=GuiOptionCycle.next(highLimit,lowLimit+2,15);button.displayString="High: "+highLimit;}
        if(button.id==5){signalBrightness=!signalBrightness;button.displayString=brightnessLabel();}
        if(button.id==6){threshold=GuiOptionCycle.next(threshold,16);button.displayString=thresholdLabel();}
        if (button.id == 1) {
            if(channel()<0)return;
            if(!shapeSubmitted||otherEdited)submit();
            mc.player.closeScreen();
        }
        if (button.id == 2 && thruster) {
            particleLevel=GuiOptionCycle.next(particleLevel,4);
            particleButton.displayString = particleLabel();
        }
        if (button.id == 3 && connected) {
            join = !join;
            button.displayString = joinLabel();
        }
        if (button.id == 4 && programmableThruster) {
            shape = GuiOptionCycle.next(shape,3);
            button.displayString = shapeLabel();
            connected = shape != 2;
            refreshJoinButton();
            submit();shapeSubmitted=true;
        }
    }


    private String pilotHeightLabel(){return "Height: "+(pilotHeight==0?"Base":"+"+(pilotHeight*2)+" px");}
    private int baseControlsY(){return signalControl?selectableType?146:122:powerLever?96:66;}
    private String dialogTitle(){return member.channelTile().getBlockType().getLocalizedName();}
    private String typeLabel(){return "Type: "+new String[]{"Standard","Twin","Grip"}[controlType];}
    private String sizeLabel(){boolean twin=member.channelTile().getWorld().getBlockState(member.channelTile().getPos()).getBlock() instanceof com.vandorlabs.blocks.BlockTwinPowerLever;return "Size: "+(twin?(powerLeverSize==0?"Small":"Large"):(powerLeverSize==0?"Compact":"Industrial"));}
    private String heightLabel(){return "Base height: "+(baseHeight==0?"Standard":"+"+(baseHeight*2)+" px");}
    private String tiltLabel(){return "Tilt: "+(baseTilt==0?"Flat":baseTilt*15+" deg");}
    private String directionLabel(){return new String[]{"Forward","Right","Backward","Left"}[tiltDirection];}
    private String brightnessLabel(){return signalBrightness?"Brightness: Signal":"Brightness: On/Off";}
    private String thresholdLabel(){return "Particles at level: "+threshold;}
    private String particleLabel() { return "Particles: "+TileEntityRedstoneLight.PARTICLE_LEVELS[particleLevel]; }
    private String joinLabel() { return join ? "Join: On" : "Join: Off"; }
    private String shapeLabel() {
        return "Shape: " + (shape == 1 ? "Hexagon" : shape == 2 ? "Wedge" : "Block");
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if(channel()<0)return;
            if(!shapeSubmitted||otherEdited)submit();
            mc.player.closeScreen();
            return;
        }
        String previous=channelField.getText();if(page!=1||transmitterControl()||!channelField.textboxKeyTyped(typedChar,keyCode))super.keyTyped(typedChar,keyCode);if(!previous.equals(channelField.getText()))otherEdited=true;
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,2,3,4,5,6,7,8,9,10,11,12,13,15,16,18,19))return;
        if (housingList != null && housingList.click(x, y, button)) {
            sideTexture = housingList.selected();otherEdited=true;
            return;
        }
        super.mouseClicked(x, y, button);
        if(page==1&&!transmitterControl())channelField.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (housingList != null && housingList.drag(y)) return;
        super.mouseClickMove(x, y, button, elapsed);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (housingList != null) housingList.release();
        super.mouseReleased(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (housingList != null) housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1,
                Mouse.getEventDWheel());
    }

    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 24, 0xFF304858);DialogHeader.logo(guiLeft,guiTop,xSize);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        GlStateManager.disableLighting();fontRenderer.drawString(fontRenderer.trimStringToWidth(dialogTitle(),xSize-50),12,8,0xffffff);
        if(page==1){if(!transmitterControl())fontRenderer.drawString("Receive channels",thruster?layout.controlsX-guiLeft:14,thruster?54:68,0xdae8f0);if(signalControl){fontRenderer.drawString("Off 0 / Medium "+((lowLimit+highLimit+1)/2),14,144,0xdae8f0);fontRenderer.drawString("Click to cycle four levels",14,158,0xdae8f0);}}
        else {if(thruster)fontRenderer.drawString("Wall texture",12,54,0xdae8f0);if(pilotSeat){fontRenderer.drawString(taxiMode?"Sit: assemble / Exit: park":"Manual Configurizer conversion",14,142,0xdae8f0);if(!inspectionStatus.isEmpty()){java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(inspectionStatus,252);for(int i=0;i<Math.min(8,lines.size());i++)fontRenderer.drawString(lines.get(i),14,158+i*9,0xffbb88);}}}
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        GlStateManager.disableLighting();
        if (housingList != null) housingList.draw(fontRenderer, mouseX, mouseY);
        channelField.drawTextBox();
    }
}
