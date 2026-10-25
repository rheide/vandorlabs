package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

/** Immediate shared controls with a second page for motion endpoints and track appearance. */
public final class GuiModularGear extends GuiContainer {
    private final GearTileEntity tile;
    private int trigger,direction,trackStyle;
    private boolean deployed,rotate,advanced;
    private GuiTextField channels,angle,extension;
    private String error="";
    public GuiModularGear(ContainerModularGear container){
        super(container);tile=container.tile;trigger=tile.triggerMode;direction=tile.direction().ordinal();deployed=tile.target();rotate=tile.rotatePayload();trackStyle=com.vandorlabs.gear.track.TrackStyles.style(tile);advanced=tile instanceof com.vandorlabs.gear.track.TrackTile&&!tile.isController();xSize=300;ySize=228;
    }
    @Override public void initGui(){
        xSize=Math.min(340,width-12);super.initGui();buttonList.clear();
        channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+76,xSize-24,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(tile.getRedstoneChannels().toString());
        angle=new GuiTextField(1,fontRenderer,guiLeft+12,guiTop+84,xSize-24,18);angle.setMaxStringLength(7);angle.setText(Double.toString(tile.activeAngle()));
        extension=new GuiTextField(2,fontRenderer,guiLeft+12,guiTop+132,xSize-24,18);extension.setMaxStringLength(7);extension.setText(Double.toString(tile.activeExtension()));
        add(0,30);add(1,102);add(2,128);add(6,154);add(7,30);add(5,154);add(4,200);
        labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    private void add(int id,int y){buttonList.add(new GuiButton(id,guiLeft+12,guiTop+y,xSize-24,20,""));}
    private GuiButton button(int id){for(GuiButton b:buttonList)if(b.id==id)return b;throw new IllegalArgumentException();}
    private void labels(){
        for(GuiButton b:buttonList)b.visible=b.id==4||advanced?(b.id==4||b.id==5||b.id==7):(b.id==0||b.id==1||b.id==2||b.id==6);
        channels.setVisible(!advanced);angle.setVisible(advanced);extension.setVisible(advanced);
        boolean rest=tile.fullyDeployed();
        button(0).displayString="Input: "+(tile.isLandingAssembly()?new String[]{"Manual","ON deploys","OFF deploys"}:new String[]{"Manual","ON activates","OFF activates"})[trigger];
        net.minecraft.util.math.BlockPos fold=com.vandorlabs.gear.ArmOrientation.vector(tile.mountDirection(),new net.minecraft.util.math.BlockPos(net.minecraft.util.EnumFacing.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[direction]).getDirectionVec()));
        button(1).displayString="Fold toward: "+net.minecraft.util.EnumFacing.getFacingFromVector(fold.getX(),fold.getY(),fold.getZ()).getName();button(1).enabled=tile.isSwingArmController()&&rest&&deployed;
        button(2).displayString=tile.isLandingAssembly()?(deployed?"Target: Deployed":"Target: Stowed"):(deployed?"Target: Off (construction pose)":"Target: On (configured pose)");button(2).enabled=trigger==0;
        button(6).displayString="Motion and appearance...";
        button(7).displayString=rotate?"Payload: rotates with arm":"Payload: stays level";button(7).enabled=tile.isSwingArmController()&&rest;
        button(5).displayString="Track style: "+new String[]{"Compact","Armoured","Bogie"}[Math.max(0,trackStyle)];button(5).visible=advanced&&trackStyle>=0;button(5).enabled=!tile.isController()||rest;
        angle.setEnabled(!tile.isLandingAssembly()&&tile.isSwingArmController()&&rest);extension.setEnabled(!tile.isLandingAssembly()&&!tile.isSwingArmController()&&!tile.isFixedMount()&&rest);
        button(4).displayString=advanced?"Back":"Done";
        if(tile.isFixedMount()){button(0).displayString="Fixed Arm";button(0).enabled=false;button(1).displayString="No folding";button(1).enabled=false;button(2).displayString="Always deployed";button(2).enabled=false;channels.setEnabled(false);}
    }
    private void submit(){
        if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){error="Invalid channel list";return;}
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Trigger",trigger);tag.setInteger("Direction",direction);tag.setBoolean("Deployed",deployed);tag.setString("Channels",channels.getText());tag.setBoolean("RotatePayload",rotate);if(trackStyle>=0)tag.setInteger("TrackStyle",trackStyle);
        try{double a=Double.parseDouble(angle.getText()),e=Double.parseDouble(extension.getText());if(!Double.isFinite(a)||a < -180||a > 180||!Double.isFinite(e)||e < -8||e > 8)throw new NumberFormatException();tag.setDouble("ActiveAngle",a);tag.setDouble("ActiveExtension",e);}catch(NumberFormatException invalid){error="Angle: -180 to 180; extension: -8 to 8";return;}
        error="";PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,tag));
    }
    @Override protected void actionPerformed(GuiButton b){
        if(b.id==4){if(advanced){advanced=false;labels();}else mc.player.closeScreen();return;}
        if(b.id==6){advanced=true;labels();return;}
        if(b.id==0)trigger=GuiOptionCycle.next(trigger,3);else if(b.id==1)direction=GuiOptionCycle.next(direction,4);else if(b.id==2)deployed=!deployed;else if(b.id==7)rotate=!rotate;else if(b.id==5)trackStyle=GuiOptionCycle.next(trackStyle,3);else return;
        submit();labels();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Arms and Landing Gear",12,12,0xffffff);
        if(advanced){fontRenderer.drawString("Active angle (-180 to 180 degrees)",12,64,0xcdd7e4);fontRenderer.drawString("Extension (-8 to 8 blocks)",12,112,0xcdd7e4);}
        else fontRenderer.drawString(tile.usesLegacyInput()?"Channels (any, plus local power)":"Channels (0 = local redstone)",12,62,0xcdd7e4);
        String status=error.isEmpty()?(tile.isController()?tile.columns().size()+" arms / "+(tile.isLandingAssembly()?tile.assembly().feet().size():0)+" feet":"Track appearance"):error;
        fontRenderer.drawString(status,12,182,error.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);channels.drawTextBox();angle.drawTextBox();extension.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,0,1,2,5,7))return;super.mouseClicked(x,y,b);channels.mouseClicked(x,y,b);angle.mouseClicked(x,y,b);extension.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(channels.textboxKeyTyped(c,key)||angle.textboxKeyTyped(c,key)||extension.textboxKeyTyped(c,key))submit();else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();angle.updateCursorCounter();extension.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
