package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ui.GearDialog;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;

/** Primary controls and motion/appearance settings share one compact page. */
public class GuiModularGear extends GuiContainer {
    private GearTileEntity tile;
    private int trigger,direction,page,footFacing,kink;
    private boolean kinkExplicit;
    private int landingAnglesVersion=-1;
    private com.vandorlabs.gear.core.ArmHinge.Mode hinge;
    private boolean deployed,rotate,join,load,push,middle;
    private int range,heightLimit;
    private ArmResource resource;
    private boolean resourceOut;
    private double angle,startAngle,submittedAngle,submittedStart;
    private GearTileEntity observedTile;
    private int observedRevision,observedClientUpdates;
    private net.minecraftforge.fml.client.config.GuiSlider rangeSlider,loadSlider,angleSlider,startSlider;
    private GuiTextField channels;
    private String error="";
    public GuiModularGear(ContainerModularGear container){
        super(container);tile=container.tile;resource=tile.resourceTransfer();resourceOut=tile.resourceOut();trigger=tile.triggerMode;direction=tile.direction().ordinal();footFacing=tile.footFacing().getHorizontalIndex();deployed=tile.target();push=tile.pushBlocks();rotate=tile.rotatePayload();middle=tile.middleHinge();hinge=tile.hingeMode();kink=tile.kinkDirection().ordinal();kinkExplicit=tile.explicitKink();join=tile.joinEnabled();load=tile.carryLoad()&&tile.canCarryLoad();angle=tile.configuredEndAngle();startAngle=tile.startAngle();range=tile.joinRange();heightLimit=tile.loadHeight();submittedAngle=angle;submittedStart=startAngle;observeAngles();observedTile=tile;observedRevision=tile.motionRevision();observedClientUpdates=tile.clientUpdates;xSize=380;ySize=244;
    }
    @Override public void initGui(){
        com.vandorlabs.ui.GearDialog.Panel panel=new com.vandorlabs.ui.GearDialog.Panel(width,height);xSize=panel.width;ySize=panel.height;super.initGui();buttonList.clear();int col=(xSize-36)/2,right=guiLeft+24+col;
        channels=new GuiTextField(0,fontRenderer,guiLeft+12,guiTop+98,xSize-24,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(ChannelFields.text(tile.getRedstoneChannels()));
        buttonList.add(new RedstoneActivationButton(0,guiLeft+12,guiTop+60,xSize-24,trigger));add(1,guiLeft+12,56,col);add(2,guiLeft+12,82,col);add(7,guiLeft+12,108,col);add(4,guiLeft+(xSize-200)/2,ySize-54,200);
        add(GearDialog.RESOURCE,right,56,col);add(GearDialog.RESOURCE_DIRECTION,right,82,col);add(19,guiLeft+12,160,col);add(18,right,108,col);add(17,right,134,col);add(14,guiLeft+12,134,col);add(9,right,56,col);add(11,right,108,col);
        rangeSlider=new net.minecraftforge.fml.client.config.GuiSlider(10,right,guiTop+82,col,20,"Join range: "," blocks",1,32,range,false,true,slider->{int value=slider.getValueInt();slider.setValue(value);if(value!=range){range=value;submit();}});buttonList.add(rangeSlider);
        loadSlider=new net.minecraftforge.fml.client.config.GuiSlider(12,right,guiTop+134,col,20,"Height: "," blocks",1,32,heightLimit,false,true,slider->{int value=slider.getValueInt();slider.setValue(value);if(value!=heightLimit){heightLimit=value;submit();}});buttonList.add(loadSlider);
        angleSlider=new net.minecraftforge.fml.client.config.GuiSlider(13,right,guiTop+134,col,20,"End: "," degrees",-180,180,angle,false,true,slider->{double value=Math.round(slider.getValue()/15)*15;slider.setValue(value);slider.displayString=slider.dispString+(int)value+slider.suffix;if(value!=angle){angle=value;landingAnglesVersion=1;}});buttonList.add(angleSlider);
        startSlider=new net.minecraftforge.fml.client.config.GuiSlider(16,guiLeft+12,guiTop+134,col,20,"Start: "," degrees",-180,180,startAngle,false,true,slider->{double value=Math.round(slider.getValue()/15)*15;slider.setValue(value);slider.displayString=slider.dispString+(int)value+slider.suffix;if(value!=startAngle){startAngle=value;landingAnglesVersion=1;}});buttonList.add(startSlider);
        buttonList.add(new DialogTabButton(20,guiLeft+12,guiTop+30,col,20,"Mechanism"));
        buttonList.add(new DialogTabButton(21,right,guiTop+30,col,20,"Redstone"));
        labels();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    private void add(int id,int x,int y,int width){buttonList.add(new GuiButton(id,x,guiTop+y,width,20,""));}
    private GuiButton button(int id){for(GuiButton b:buttonList)if(b.id==id)return b;throw new IllegalArgumentException();}
    private com.vandorlabs.ui.GearDialog.State layoutState(){
        com.vandorlabs.ui.GearDialog.State s=new com.vandorlabs.ui.GearDialog.State();
        s.rotating=tile.isSwingArmController();s.fixed=tile.isFixedMount();s.landing=tile.isLandingAssembly();s.rest=tile.fullyDeployed();s.atEndpoint=tile.progress()==0||tile.progress()==1;
        s.hingeEditable=s.rest&&new com.vandorlabs.gear.core.ArmLandingAngles(landingAnglesVersion,startAngle,angle).start(s.landing)==0;s.canJoin=!s.landing&&!s.fixed;s.canCarry=tile.canCarryLoad();s.canPush=tile.canPushBlocks();s.canFoot=tile.canConfigureFootFacing();
        s.canTransferResources=tile.canTransferResources();s.resource=resource;s.resourceOut=resourceOut;
        s.join=join;s.carry=load;s.push=push;s.rotate=rotate;s.target=deployed;s.anglesConfigured=landingAnglesVersion==1;s.trigger=trigger;s.joinRange=range;s.loadHeight=heightLimit;s.start=startAngle;s.end=angle;
        s.hinge=middle?"Double":"Single";s.kink=com.vandorlabs.gear.core.ArmHinge.kinkLabel(direction,kink);
        net.minecraft.util.math.BlockPos fv=com.vandorlabs.gear.ArmOrientation.vector(tile.mountDirection(),new net.minecraft.util.math.BlockPos(net.minecraft.util.EnumFacing.getHorizontal(footFacing).getDirectionVec()));s.foot=net.minecraft.util.EnumFacing.getFacingFromVector(fv.getX(),fv.getY(),fv.getZ()).getName();
        net.minecraft.util.math.BlockPos dv=com.vandorlabs.gear.ArmOrientation.vector(tile.mountDirection(),new net.minecraft.util.math.BlockPos(net.minecraft.util.EnumFacing.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[direction]).getDirectionVec()));s.fold=net.minecraft.util.EnumFacing.getFacingFromVector(dv.getX(),dv.getY(),dv.getZ()).getName();return s;
    }
    private void labels(){
        for(GuiButton button:buttonList){button.visible=false;button.enabled=false;}channels.setVisible(false);
        for(com.vandorlabs.ui.GearDialog.Control control:com.vandorlabs.ui.GearDialog.controls(new com.vandorlabs.ui.GearDialog.Panel(width,height),layoutState(),page)){
            if(control.kind==com.vandorlabs.ui.GearDialog.Kind.FIELD){channels.setVisible(true);channels.x=guiLeft+control.x;channels.y=guiTop+control.y;channels.width=control.width;channels.setEnabled(control.enabled);channels.setTextColor(control.enabled?0xe0e8ec:0x82939f);continue;}
            GuiButton widget=button(control.id);widget.visible=true;widget.enabled=control.enabled;widget.x=guiLeft+control.x;widget.y=guiTop+control.y;widget.width=control.width;widget.height=control.height;
            widget.displayString=control.kind==com.vandorlabs.ui.GearDialog.Kind.SLIDER?com.vandorlabs.ui.GearDialog.sliderLabel(control.id,control.value):control.label;
        }
        ((RedstoneActivationButton)button(0)).setMode(trigger);
    }
    private void observeAngles(){
        NBTTagCompound raw=tile.settings();landingAnglesVersion=com.vandorlabs.gear.core.ArmLandingAngles.version(raw.hasKey("LandingAnglesVersion"),raw.hasKey("LandingAnglesVersion",3),raw.getInteger("LandingAnglesVersion"));
        com.vandorlabs.gear.core.ArmLandingAngles angles=new com.vandorlabs.gear.core.ArmLandingAngles(landingAnglesVersion,tile.startAngle(),tile.configuredEndAngle());startAngle=angles.start(tile.isLandingAssembly());angle=angles.end(tile.isLandingAssembly());submittedAngle=angle;submittedStart=startAngle;
    }
    private void submit(){
        if(com.vandorlabs.redstone.ChannelList.parse(channels.getText())==null){error="Invalid channel list";return;}
        NBTTagCompound tag=new NBTTagCompound();if(tile.canTransferResources())tag.setInteger(ArmResource.CHOICE,resource.ordinal());tag.setBoolean(ArmResource.OUT,resourceOut);tag.setBoolean("PushBlocks",push);tag.setDouble("ActiveAngle",angle);tag.setDouble("StartAngle",startAngle);tag.setInteger("Trigger",trigger);tag.setInteger("Direction",direction);tag.setInteger("FootFacing",footFacing);tag.setBoolean("Deployed",deployed);tag.setString("Channels",channels.getText());tag.setBoolean("RotatePayload",rotate);tag.setBoolean("MiddleHinge",middle);tag.setInteger("HingeMode",hinge.ordinal());if(kinkExplicit)tag.setInteger("KinkDirection",kink);tag.setBoolean("JoinArms",join);tag.setInteger("JoinRange",range);tag.setBoolean("CarryLoad",load);tag.setInteger("LoadHeight",heightLimit);
        if(landingAnglesVersion>=0)tag.setInteger("LandingAnglesVersion",landingAnglesVersion);
        error="";submittedAngle=angle;submittedStart=startAngle;sendSettings(tag);
    }
    protected void sendSettings(NBTTagCompound tag){PacketHandler.INSTANCE.sendToServer(new MessageModularGear(inventorySlots.windowId,tag));}
    private boolean draggingAngles(){return angleSlider!=null&&angleSlider.dragging||startSlider!=null&&startSlider.dragging;}
    private void reconcileAngles(){
        if(draggingAngles()||tile==observedTile&&tile.motionRevision()==observedRevision&&tile.clientUpdates==observedClientUpdates)return;
        observedTile=tile;observedRevision=tile.motionRevision();observedClientUpdates=tile.clientUpdates;resource=tile.resourceTransfer();resourceOut=tile.resourceOut();footFacing=tile.footFacing().getHorizontalIndex();middle=tile.middleHinge();hinge=tile.hingeMode();kink=tile.kinkDirection().ordinal();kinkExplicit=tile.explicitKink();observeAngles();
        angleSlider.setValue(angle);angleSlider.displayString=angleSlider.dispString+(int)Math.round(angle)+angleSlider.suffix;
        startSlider.setValue(startAngle);startSlider.displayString=startSlider.dispString+(int)Math.round(startAngle)+startSlider.suffix;
    }
    @Override protected void mouseReleased(int x,int y,int button){
        boolean angles=draggingAngles();super.mouseReleased(x,y,button);
        if(button==0&&angles&&(angle!=submittedAngle||startAngle!=submittedStart))submit();
    }
    @Override protected void actionPerformed(GuiButton b){
        if(b.id==GearDialog.MECHANISM||b.id==GearDialog.REDSTONE){page=b.id-GearDialog.MECHANISM;labels();return;}
        if(b.id==4){mc.player.closeScreen();return;}
        if(b.id==0)trigger=GuiOptionCycle.next(trigger,3);else if(b.id==1){int next=GuiOptionCycle.next(direction,4);kink=com.vandorlabs.gear.core.ArmHinge.reorientKink(direction,kink,next);kinkExplicit=true;direction=next;}else if(b.id==2)deployed=!deployed;else if(b.id==7)rotate=!rotate;else if(b.id==9){join=!join;if(!join)load=false;}else if(b.id==11)load=!load;else if(b.id==14)push=!push;else if(b.id==18){middle=!middle;hinge=middle?com.vandorlabs.gear.core.ArmHinge.Mode.DOUBLE:com.vandorlabs.gear.core.ArmHinge.Mode.SINGLE;}else if(b.id==19){kink=com.vandorlabs.gear.core.ArmHinge.cycleRelativeKink(direction,kink,GuiOptionCycle.direction());kinkExplicit=true;}else if(b.id==17)footFacing=GuiOptionCycle.next(footFacing,4);else if(b.id==GearDialog.RESOURCE_DIRECTION)resourceOut=!resourceOut;else if(b.id==GearDialog.RESOURCE)resource=ArmResource.cycle(resource,GuiOptionCycle.direction());else return;
        submit();labels();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString(fontRenderer.trimStringToWidth(tile.getBlockType().getLocalizedName(),xSize-64),12,12,0xffffff);
        DialogHeader.logo(0,0,xSize);
        if(page==1){
            fontRenderer.drawString("Receive channels",12,86,tile.isFixedMount()?0x82939f:0xcdd7e4);
            if(tile.isFixedMount())fontRenderer.drawSplitString("Fixed arms remain deployed and do not use activation signals.",12,126,xSize-24,0x82939f);
        }
        String failure=error.isEmpty()?tile.error():error;String status=failure.isEmpty()?(tile.isController()?tile.columns().size()+" arms / "+(tile.isLandingAssembly()?tile.assembly().feet().size():0)+" feet":"No assembled mechanism"):"ERROR: "+failure;
        java.util.List<String> lines=fontRenderer.listFormattedStringToWidth(status,xSize-24);for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),12,ySize-20+i*9,failure.isEmpty()?0xcdd7e4:0xff8888);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,b,this::actionPerformed,0,1,2,7,9,11,14,17,18,19,GearDialog.RESOURCE,GearDialog.RESOURCE_DIRECTION))return;super.mouseClicked(x,y,b);channels.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(channels.textboxKeyTyped(c,key))submit();else super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();GearTileEntity current=((ContainerModularGear)inventorySlots).currentTile();if(current!=null)tile=current;reconcileAngles();channels.updateCursorCounter();labels();}
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
