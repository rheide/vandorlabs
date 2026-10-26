package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

/** Primary controls and motion/appearance settings share one compact single-column page. */
public final class GuiModularGear extends GuiContainer {
    private final GearTileEntity tile;
    private int trigger,direction;
    private boolean deployed,rotate;
    private GuiTextField channels;
    private String error="";
    public GuiModularGear(ContainerModularGear container){
        super(container);tile=container.tile;trigger=tile.triggerMode;direction=tile.direction().ordinal();deployed=tile.target();rotate=tile.rotatePayload();xSize=252;ySize=228;
    }
    @Override public void initGui(){
        xSize=Math.min(252,width-12);ySize=228;super.initGui();buttonList.clear();int col=xSize-24;
        channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+70,col,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(tile.getRedstoneChannels().toString());
        add(0,guiLeft+12,30,col);add(1,guiLeft+12,94,col);add(2,guiLeft+12,120,col);add(7,guiLeft+12,146,col);add(4,guiLeft+(xSize-200)/2,200,200);
        labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    private void add(int id,int x,int y,int width){buttonList.add(new GuiButton(id,x,guiTop+y,width,20,""));}
    private GuiButton button(int id){for(GuiButton b:buttonList)if(b.id==id)return b;throw new IllegalArgumentException();}
    private void labels(){

        boolean rest=tile.fullyDeployed();
        button(0).displayString="Input: "+(tile.isLandingAssembly()&&!tile.isExtendedTrackStroke()?new String[]{"Manual","ON deploys","OFF deploys"}:new String[]{"Manual","ON activates","OFF activates"})[trigger];
        net.minecraft.util.math.BlockPos fold=com.vandorlabs.gear.ArmOrientation.vector(tile.mountDirection(),new net.minecraft.util.math.BlockPos(net.minecraft.util.EnumFacing.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[direction]).getDirectionVec()));
        button(1).displayString="Fold: "+net.minecraft.util.EnumFacing.getFacingFromVector(fold.getX(),fold.getY(),fold.getZ()).getName();button(1).enabled=tile.isSwingArmController()&&rest&&deployed;
        button(2).displayString=tile.isLandingAssembly()&&!tile.isExtendedTrackStroke()?(deployed?"Target: Deployed":"Target: Stowed"):(deployed?"Target: Off (rest)":"Target: On (active)");button(2).enabled=trigger==0;
        button(7).displayString=rotate?"Payload: rotates":"Payload: stays level";button(7).enabled=tile.isSwingArmController()&&rest;
        button(4).displayString="Done";
        if(tile.isFixedMount()){button(0).displayString="Fixed Arm";button(0).enabled=false;button(1).displayString="No folding";button(1).enabled=false;button(2).displayString="Always deployed";button(2).enabled=false;channels.setEnabled(false);}
    }
    private void submit(){
        if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){error="Invalid channel list";return;}
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Trigger",trigger);tag.setInteger("Direction",direction);tag.setBoolean("Deployed",deployed);tag.setString("Channels",channels.getText());tag.setBoolean("RotatePayload",rotate);
        error="";PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,tag));
    }
    @Override protected void actionPerformed(GuiButton b){
        if(b.id==4){mc.player.closeScreen();return;}
        if(b.id==0)trigger=GuiOptionCycle.next(trigger,3);else if(b.id==1)direction=GuiOptionCycle.next(direction,4);else if(b.id==2)deployed=!deployed;else if(b.id==7)rotate=!rotate;else return;
        submit();labels();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Arms and Landing Gear",12,12,0xffffff);
        fontRenderer.drawString(tile.usesLegacyInput()?"Channels (any + local)":"Channels (0 = local)",12,57,0xcdd7e4);
        String failure=error.isEmpty()?tile.error():error;String status=failure.isEmpty()?(tile.isController()?tile.columns().size()+" arms / "+(tile.isLandingAssembly()?tile.assembly().feet().size():0)+" feet":"Track appearance"):"ERROR: "+failure;
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,xSize-24);for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),12,180+i*9,failure.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,0,1,2,7))return;super.mouseClicked(x,y,b);channels.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(channels.textboxKeyTyped(c,key))submit();else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
