package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

/** Primary controls and motion/appearance settings share one compact page. */
public final class GuiModularGear extends GuiContainer {
    private GearTileEntity tile;
    private int trigger,direction,page;
    private boolean deployed,rotate,join,load,push;
    private int range,heightLimit;
    private double angle,startAngle;
    private net.minecraftforge.fml.client.config.GuiSlider rangeSlider,loadSlider,angleSlider,startSlider;
    private GuiTextField channels;
    private String error="";
    public GuiModularGear(ContainerModularGear container){
        super(container);tile=container.tile;trigger=tile.triggerMode;direction=tile.direction().ordinal();deployed=tile.target();push=tile.pushBlocks();rotate=tile.rotatePayload();join=tile.joinEnabled();load=tile.carryLoad()&&tile.canCarryLoad();angle=tile.configuredEndAngle();startAngle=tile.startAngle();range=tile.joinRange();heightLimit=tile.loadHeight();xSize=380;ySize=218;
    }
    @Override public void initGui(){
        xSize=Math.min(380,width-12);ySize=218;super.initGui();buttonList.clear();int col=(xSize-36)/2,right=guiLeft+24+col;
        channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+70,col,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(ChannelFields.text(tile.getRedstoneChannels()));
        buttonList.add(new RedstoneActivationButton(0,guiLeft+12,guiTop+60,col,trigger));add(1,guiLeft+12,56,col);add(2,guiLeft+12,82,col);add(7,guiLeft+12,108,col);add(4,guiLeft+(xSize-200)/2,164,200);
        add(14,guiLeft+12,134,col);add(9,right,56,col);add(11,right,108,col);
        rangeSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,right,guiTop+82,col,20,"Range: "," blocks",1,32,range,false,true,slider->{int value=slider.getValueInt();slider.setValue(value);if(value!=range){range=value;submit();}});buttonList.add(rangeSlider);
        loadSlider=new net.minecraftforge.fml.client.config.GuiSlider(12,right,guiTop+134,col,20,"Height: "," blocks",1,32,heightLimit,false,true,slider->{int value=slider.getValueInt();slider.setValue(value);if(value!=heightLimit){heightLimit=value;submit();}});buttonList.add(loadSlider);
        angleSlider=new net.minecraftforge.fml.client.config.GuiSlider(13,guiLeft+12,guiTop+134,col,20,"End: "," degrees",-180,180,angle,false,true,slider->{double value=Math.round(slider.getValue()/15)*15;slider.setValue(value);if(value!=angle){angle=value;submit();}});buttonList.add(angleSlider);
        startSlider=new net.minecraftforge.fml.client.config.GuiSlider(16,right,guiTop+134,col,20,"Start: "," degrees",-180,180,startAngle,false,true,slider->{double value=Math.round(slider.getValue()/15)*15;slider.setValue(value);if(value!=startAngle){startAngle=value;submit();}});buttonList.add(startSlider);
        buttonList.add(new DialogTabButton(20,guiLeft+12,guiTop+30,col,20,"Mechanism"));
        buttonList.add(new DialogTabButton(21,right,guiTop+30,col,20,"Redstone"));
        labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    private void add(int id,int x,int y,int width){buttonList.add(new GuiButton(id,x,guiTop+y,width,20,""));}
    private GuiButton button(int id){for(GuiButton b:buttonList)if(b.id==id)return b;throw new IllegalArgumentException();}
    private void labels(){

        boolean rest=tile.fullyDeployed();
        for(GuiButton button:buttonList){button.visible=button.id>=20||button.id==4||((page==1)==(button.id==0));if(button.id>=20)button.enabled=button.id!=20+page;}
        channels.setVisible(page==1);channels.y=guiTop+98;button(0).y=guiTop+60;
        button(14).visible=page==0&&tile.canPushBlocks();button(14).enabled=tile.progress()==0||tile.progress()==1;button(14).displayString="Push blocks: "+(push?"On":"Off");
        angleSlider.visible=page==0&&tile.isSwingArmController()&&!tile.isLandingAssembly();angleSlider.enabled=rest;startSlider.visible=angleSlider.visible;startSlider.enabled=rest;
        rangeSlider.visible=page==0&&join;loadSlider.visible=page==0&&join&&load&&tile.canCarryLoad();
        button(9).displayString="Join arms: "+(join?"On":"Off");button(9).enabled=rest&&!tile.isLandingAssembly()&&!tile.isFixedMount();rangeSlider.enabled=rest;
        button(11).displayString="Carry load: "+(load?"On":"Off");button(11).enabled=rest&&join&&tile.canCarryLoad();loadSlider.enabled=rest;
        ((RedstoneActivationButton)button(0)).setMode(trigger);
        net.minecraft.util.math.BlockPos fold=com.vandorlabs.gear.ArmOrientation.vector(tile.mountDirection(),new net.minecraft.util.math.BlockPos(net.minecraft.util.EnumFacing.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[direction]).getDirectionVec()));
        button(1).displayString="Fold: "+net.minecraft.util.EnumFacing.getFacingFromVector(fold.getX(),fold.getY(),fold.getZ()).getName();button(1).enabled=tile.isSwingArmController()&&rest&&deployed;
        button(2).displayString=tile.isLandingAssembly()&&!tile.isExtendedTrackStroke()?(deployed?"Target: Deployed":"Target: Stowed"):(deployed?"Target: Off (rest)":"Target: On (active)");button(2).enabled=trigger==0;
        button(7).displayString=rotate?"Payload: rotates":"Payload: stays level";button(7).enabled=tile.isSwingArmController()&&rest;
        button(4).displayString="Done";
        if(tile.isFixedMount()){button(0).displayString="Fixed Arm";button(0).enabled=false;button(1).displayString="No folding";button(1).enabled=false;button(2).displayString="Always deployed";button(2).enabled=false;channels.setEnabled(false);}
    }
    private void submit(){
        if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){error="Invalid channel list";return;}
        NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("PushBlocks",push);tag.setDouble("ActiveAngle",angle);tag.setDouble("StartAngle",startAngle);tag.setInteger("Trigger",trigger);tag.setInteger("Direction",direction);tag.setBoolean("Deployed",deployed);tag.setString("Channels",channels.getText());tag.setBoolean("RotatePayload",rotate);tag.setBoolean("JoinArms",join);tag.setInteger("JoinRange",range);tag.setBoolean("CarryLoad",load);tag.setInteger("LoadHeight",heightLimit);
        error="";PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,tag));
    }
    @Override protected void actionPerformed(GuiButton b){
        if(b.id>=20){page=b.id-20;labels();return;}
        if(b.id==4){mc.player.closeScreen();return;}
        if(b.id==0)trigger=GuiOptionCycle.next(trigger,3);else if(b.id==1)direction=GuiOptionCycle.next(direction,4);else if(b.id==2)deployed=!deployed;else if(b.id==7)rotate=!rotate;else if(b.id==9){join=!join;if(!join)load=false;}else if(b.id==11)load=!load;else if(b.id==14)push=!push;else return;
        submit();labels();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(fontRenderer.trimStringToWidth(tile.getBlockType().getLocalizedName(),xSize-64),12,12,0xffffff);
        DialogHeader.logo(0,0,xSize);
        if(page==1)fontRenderer.drawString(tile.usesLegacyInput()?"Channels (any + local)":"Channels (0 = local)",12,85,0xcdd7e4);
        String failure=error.isEmpty()?tile.error():error;String status=failure.isEmpty()?(tile.isController()?tile.columns().size()+" arms / "+(tile.isLandingAssembly()?tile.assembly().feet().size():0)+" feet":"Track appearance"):"ERROR: "+failure;
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,xSize-24);for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),12,198+i*9,failure.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,0,1,2,7,9,11,14))return;super.mouseClicked(x,y,b);channels.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(channels.textboxKeyTyped(c,key))submit();else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();GearTileEntity current=((ContainerModularGear)inventorySlots).currentTile();if(current!=null)tile=current;channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
