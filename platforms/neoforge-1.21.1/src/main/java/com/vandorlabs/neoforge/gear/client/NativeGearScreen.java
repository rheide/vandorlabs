package com.vandorlabs.neoforge.gear.client;

import com.vandorlabs.neoforge.gear.*;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ui.GearDialog;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source's compact Mechanism/Redstone layout, with authoritative edit replies and previous-value right clicks. */
public final class NativeGearScreen extends AbstractContainerScreen<NativeGearMenu> {
    private NativeGearSettings draft;private boolean target,ready,dirty,dragging;private int page,request,acknowledgedRequest,delay,observedEndpoint=-2;private String error="";private EditBox channels;private int previousGuiScale=-1;private boolean closing,observedAdjustment;
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();private NativeGearSettingsPayload.Result pending;
    public NativeGearScreen(NativeGearMenu menu,Inventory inventory,Component title){super(menu,inventory,title);draft=menu.tile.settings();target=menu.tile.target();observedAdjustment=menu.tile.adjustingStart();NativeGearSettingsPayload.bindResults(result->{if(Minecraft.getInstance().screen==this)acknowledge(result);});}
    public Button button(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public NativeGearSettings editingSettings(){return draft;}public int page(){return page;}public String error(){return error;}public boolean waitingForReply(){return acknowledgedRequest!=request;}
    @Override protected void init(){if(!closing&&height<234&&minecraft.getWindow().getGuiScale()>1){if(previousGuiScale<0)previousGuiScale=minecraft.options.guiScale().get();minecraft.options.guiScale().set((int)minecraft.getWindow().getGuiScale()-1);minecraft.resizeDisplay();return;}var panel=new com.vandorlabs.ui.GearDialog.Panel(width,height);imageWidth=panel.width;imageHeight=panel.height;if(!closing&&height<imageHeight+12&&minecraft.getWindow().getGuiScale()>1){if(previousGuiScale<0)previousGuiScale=minecraft.options.guiScale().get();minecraft.options.guiScale().set((int)minecraft.getWindow().getGuiScale()-1);minecraft.resizeDisplay();return;}super.init();rebuild();}
    @Override public void removed(){closing=true;super.removed();if(previousGuiScale>=0){int restore=previousGuiScale;previousGuiScale=-1;minecraft.options.guiScale().set(restore);minecraft.resizeDisplay();}}
    private void option(int id,int x,int y,int w,String label,boolean enabled){Button button=(id==20||id==21)?new com.vandorlabs.neoforge.client.NativeDialogTabButton(leftPos+x,topPos+y,w,20,Component.literal(label),ignored->action(id,1)):com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(label),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();button.active=enabled;buttons.put(id,button);addRenderableWidget(button);}
    private boolean landing(){return menu.tile.assembly()!=null&&menu.tile.assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.NONE;}
    private void slider(com.vandorlabs.ui.GearDialog.Control control){
        int id=control.id,scale=id==13||id==16?15:1;
        var slider=new NativeIntegerSlider(leftPos+control.x,topPos+control.y,control.width,control.height,control.minimum/scale,control.maximum/scale,(int)control.value/scale,n->Component.literal(com.vandorlabs.ui.GearDialog.sliderLabel(id,n*scale)),n->{
            if(id==13||id==16)draft=draft.withAngles(draft.angles().edit(landing(),id==16,n*15));
            else{var next=draft.editableChoices();if(id==10)next.putInt("JoinRange",n);else if(id==12)next.putInt("LoadHeight",n);else throw new IllegalArgumentException("Unknown arm slider");draft=NativeGearSettings.readChoices(next);}dirty=true;
        });slider.active=control.enabled;addRenderableWidget(slider);
    }
    private com.vandorlabs.ui.GearDialog.State layoutState(){
        var s=new com.vandorlabs.ui.GearDialog.State();s.rest=menu.tile.progress()==1&&menu.tile.target();s.atEndpoint=endpoint()>=0;s.rotating=menu.tile.definition().kind==NativeGearDefinition.Kind.ROTATING;s.fixed=menu.tile.definition().kind==NativeGearDefinition.Kind.MOUNT;s.landing=landing();
        var angles=draft.angles();s.start=angles.start(s.landing);s.end=angles.end(s.landing);s.anglesConfigured=angles.configured();s.hingeEditable=s.rest&&s.start==0;s.canJoin=!s.landing&&!s.fixed;s.canCarry=!s.fixed&&!s.rotating&&!s.landing&&menu.tile.mount().getAxis()==net.minecraft.core.Direction.Axis.Y;s.canPush=menu.tile.canPushBlocks();s.canFoot=menu.tile.canConfigureFootFacing();
        s.canTransferResources=menu.tile.canTransferResources();s.resource=draft.resourceTransfer();
        s.join=draft.join();s.carry=draft.carryLoad();s.push=draft.pushBlocks();s.rotate=draft.rotatePayload();s.target=target;s.trigger=draft.trigger();s.joinRange=draft.joinRange();s.loadHeight=draft.loadHeight();
        s.hinge=draft.middleHinge()?"Double":"Single";s.kink=com.vandorlabs.gear.core.ArmHinge.kinkLabel(draft.direction(),draft.kinkDirection());s.fold=worldFacing(new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST}[draft.direction()]);s.foot=worldFacing(net.minecraft.core.Direction.from2DDataValue(draft.footFacing()));if(menu.tile.trackDescriptor()!=null)s.track=menu.tile.trackDescriptor().style().name();return s;
    }
    private void rebuild(){
        observedEndpoint=endpoint();ready=false;clearWidgets();buttons.clear();channels=null;
        for(var control:com.vandorlabs.ui.GearDialog.controls(new com.vandorlabs.ui.GearDialog.Panel(width,height),layoutState(),page)){
            if(control.kind==com.vandorlabs.ui.GearDialog.Kind.SLIDER){slider(control);continue;}
            if(control.kind==com.vandorlabs.ui.GearDialog.Kind.FIELD){channels=new EditBox(font,leftPos+control.x,topPos+control.y,control.width,control.height,Component.literal(control.label));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft.channels().toString());channels.setEditable(control.enabled);channels.setResponder(value->{channels.setTextColor(ChannelList.parse(value)==null?0xFF8888:0xFFFFFF);dirty=true;});addRenderableWidget(channels);continue;}
            option(control.id,control.x,control.y,control.width,control.label,control.enabled);buttons.get(control.id).setHeight(control.height);
        }
        ready=true;
    }
    private int endpoint(){return menu.tile.progress()==0?0:menu.tile.progress()==1?1:-1;}
    private String worldFacing(net.minecraft.core.Direction local){var mapped=NativeGearOrientation.vector(menu.tile.mount(),local.getStepX(),local.getStepY(),local.getStepZ());var face=net.minecraft.core.Direction.getNearest(mapped.x,mapped.y,mapped.z);String name=face.getName();return Character.toUpperCase(name.charAt(0))+name.substring(1);}
    public void action(int id,int direction){if(id==15&&menu.tile.trackDescriptor()!=null){PacketDistributor.sendToServer(new com.vandorlabs.neoforge.gear.track.NativeTrackStylePayload(menu.containerId,menu.selectedPosition(),menu.selectedIdentity(),Math.floorMod(menu.tile.trackDescriptor().style().ordinal()+direction,3)));return;}if(id==4){onClose();return;}if(id==20||id==21){apply();page=id-20;rebuild();return;}var next=draft.editableChoices();switch(id){case 0->next.putInt("Trigger",Math.floorMod(draft.trigger()+direction,3));case 1->{int fold=Math.floorMod(draft.direction()+direction,4);next.putInt("KinkDirection",com.vandorlabs.gear.core.ArmHinge.reorientKink(draft.direction(),draft.kinkDirection(),fold));next.putInt("Direction",fold);}case 2->target=!target;case 7->next.putBoolean("RotatePayload",!draft.rotatePayload());case 9->next.putBoolean("JoinArms",!draft.join());case 11->next.putBoolean("CarryLoad",!draft.carryLoad());case 14->next.putBoolean("PushBlocks",!draft.pushBlocks());case 18->{next.putBoolean("MiddleHinge",!draft.middleHinge());next.putInt("HingeMode",draft.middleHinge()?0:2);}case 19->next.putInt("KinkDirection",com.vandorlabs.gear.core.ArmHinge.cycleRelativeKink(draft.direction(),draft.kinkDirection(),direction));case GearDialog.RESOURCE->next.putInt(ArmResource.CHOICE,ArmResource.cycle(draft.resourceTransfer(),direction).ordinal());case 17->next.putInt("FootFacing",Math.floorMod(draft.footFacing()+direction,4));default->{return;}}draft=NativeGearSettings.readChoices(next);apply();rebuild();}
    public boolean apply(){if(!ready)return false;if(channels!=null){var parsed=ChannelList.parse(channels.getValue());if(parsed==null){error="Invalid channel list";return false;}var next=draft.editableChoices();next.putString("Channels",parsed.toString());next.putBoolean("LegacyAnyChannels",false);draft=NativeGearSettings.readChoices(next);}dirty=false;PacketDistributor.sendToServer(new NativeGearSettingsPayload(menu.containerId,menu.selectedPosition(),draft,target,++request));return true;}
    public void acknowledge(NativeGearSettingsPayload.Result result){var state=result.authoritative();if(state.window()!=menu.containerId||!state.owner().equals(menu.selectedPosition())||state.request()!=request)return;pending=result;reconcile();}
    private void reconcile(){if(pending==null||dragging||minecraft.mouseHandler.isLeftPressed())return;var result=pending;if(!menu.rebind(minecraft.player,result.controller(),result.controllerIdentity()))return;pending=null;if(result.authoritative().request()!=request)return;acknowledgedRequest=result.authoritative().request();boolean invalidLanding=!result.applied()&&landing()&&menu.tile.definition().kind==NativeGearDefinition.Kind.ROTATING&&draft.landingAnglesVersion()==1&&!menu.tile.landingEndpoints(draft).valid();draft=result.authoritative().settings();target=result.authoritative().deployed();error=result.applied()?"":invalidLanding?com.vandorlabs.gear.core.LandingAnglePolicy.ERROR:"Return to rest or clear the new arm shape.";rebuild();}
    @Override protected void containerTick(){super.containerTick();var previous=menu.tile;if(menu.resolve(minecraft.player)&&previous!=menu.tile&&!dirty&&!dragging){draft=menu.tile.settings();target=menu.tile.target();rebuild();}if(menu.tile.adjustingStart())observedAdjustment=true;else if(observedAdjustment&&!dirty&&!dragging&&!waitingForReply()){observedAdjustment=false;draft=menu.tile.settings();target=menu.tile.target();rebuild();}if(endpoint()!=observedEndpoint&&!dirty&&!dragging&&!waitingForReply())rebuild();if(dirty&&--delay<=0){apply();delay=2;}reconcile();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var entry:buttons.entrySet())if(entry.getValue().active&&entry.getValue().isMouseOver(x,y)&&(button==0||entry.getKey()!=4&&(entry.getKey()<20||entry.getKey()==GearDialog.RESOURCE))){action(entry.getKey(),button==1?-1:1);return true;}boolean handled=super.mouseClicked(x,y,button);if(button==0&&getFocused() instanceof NativeIntegerSlider)dragging=handled;return handled;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(button==0){dragging=false;if(dirty)apply();reconcile();}return super.mouseReleased(x,y,button);}
    @Override public void onClose(){if(dirty)apply();super.onClose();}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.GEAR);if(page==1)g.drawString(font,"Receive channels",leftPos+12,topPos+86,0xFFFFFF,false);if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,imageWidth-24),leftPos+12,topPos+imageHeight-28,0xFFFFAA88,false);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
