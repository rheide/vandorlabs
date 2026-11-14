package com.vandorlabs.neoforge.gear.client;

import com.vandorlabs.neoforge.gear.*;
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
    private NativeGearSettings draft;private boolean target,ready,dirty,dragging;private int page,request,acknowledgedRequest,delay,observedEndpoint=-2;private String error="";private EditBox channels;private int previousGuiScale=-1;private boolean closing;
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();private NativeGearSettingsPayload.Result pending;
    public NativeGearScreen(NativeGearMenu menu,Inventory inventory,Component title){super(menu,inventory,title);draft=menu.tile.settings();target=menu.tile.target();NativeGearSettingsPayload.bindResults(result->{if(Minecraft.getInstance().screen==this)acknowledge(result);});}
    public Button button(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public NativeGearSettings editingSettings(){return draft;}public int page(){return page;}public String error(){return error;}public boolean waitingForReply(){return acknowledgedRequest!=request;}
    @Override protected void init(){imageWidth=Math.min(380,width-12);imageHeight=218;if(!closing&&height<imageHeight+12&&minecraft.getWindow().getGuiScale()>1){if(previousGuiScale<0)previousGuiScale=minecraft.options.guiScale().get();minecraft.options.guiScale().set((int)minecraft.getWindow().getGuiScale()-1);minecraft.resizeDisplay();return;}super.init();rebuild();}
    @Override public void removed(){closing=true;super.removed();if(previousGuiScale>=0){int restore=previousGuiScale;previousGuiScale=-1;minecraft.options.guiScale().set(restore);minecraft.resizeDisplay();}}
    private void option(int id,int x,int y,int w,String label,boolean enabled){var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();button.active=enabled;buttons.put(id,button);addRenderableWidget(button);}
    private void slider(int id,int x,int y,int w,int low,int high,int value,String label,boolean enabled){var slider=new NativeIntegerSlider(leftPos+x,topPos+y,w,20,low,high,value,n->Component.literal(label+": "+(id==13||id==16?n*15:n)),n->{var next=draft.choices();switch(id){case 13->next.putDouble("ActiveAngle",n*15);case 16->next.putDouble("StartAngle",n*15);case 10->next.putInt("JoinRange",n);case 12->next.putInt("LoadHeight",n);default->throw new IllegalArgumentException("Unknown arm slider");}draft=NativeGearSettings.readChoices(next);dirty=true;});slider.active=enabled;addRenderableWidget(slider);}
    private void rebuild(){
        observedEndpoint=endpoint();ready=false;clearWidgets();buttons.clear();int col=(imageWidth-36)/2,right=24+col;
        boolean rest=menu.tile.progress()==1,swing=menu.tile.definition().kind==NativeGearDefinition.Kind.ROTATING,fixed=menu.tile.definition().kind==NativeGearDefinition.Kind.MOUNT;
        var foot=menu.tile.assembly()==null?com.vandorlabs.gear.core.GearTopology.FootKind.NONE:menu.tile.assembly().kind();boolean landing=foot!=com.vandorlabs.gear.core.GearTopology.FootKind.NONE;
        boolean carry=!fixed&&!swing&&!landing&&menu.tile.mount().getAxis()==net.minecraft.core.Direction.Axis.Y;
        option(20,12,30,col,"Mechanism",page!=0);option(21,right,30,col,"Redstone",page!=1);
        if(page==0){channels=null;
            option(1,12,56,col,"Fold: "+worldFacing(new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST}[draft.direction()]),swing&&rest&&target);
            option(9,right,56,col,"Join arms: "+(draft.join()?"On":"Off"),rest&&!landing&&!fixed);
            option(2,12,82,col,landing?(target?"Target: Deployed":"Target: Stowed"):(target?"Target: Off (rest)":"Target: On (active)"),draft.trigger()==0&&!fixed);
            if(draft.join())slider(10,right,82,col,1,32,draft.joinRange(),"Range",rest);
            option(7,12,108,col,draft.rotatePayload()?"Payload: rotates":"Payload: stays level",swing&&rest);
            if(swing)option(18,right,108,col,"Hinge: "+(draft.middleHinge()?"Middle":"Base"),rest&&(landing||draft.startAngle()==0));
            else option(11,right,108,col,"Carry load: "+(draft.carryLoad()?"On":"Off"),rest&&draft.join()&&carry);
            if(swing&&!landing){slider(16,12,134,col,-12,12,(int)draft.startAngle()/15,"Start",rest);slider(13,right,134,col,-12,12,(int)draft.activeAngle()/15,"End",rest);}
            if(menu.tile.canPushBlocks())option(14,12,134,col,"Push blocks: "+(draft.pushBlocks()?"On":"Off"),menu.tile.progress()==0||menu.tile.progress()==1);
            if(!swing&&draft.join()&&draft.carryLoad()&&carry)slider(12,right,134,col,1,32,draft.loadHeight(),"Height",rest);
            if(foot==com.vandorlabs.gear.core.GearTopology.FootKind.SKI||foot==com.vandorlabs.gear.core.GearTopology.FootKind.WHEEL)option(17,right,134,col,"Foot: "+worldFacing(net.minecraft.core.Direction.from2DDataValue(draft.footFacing())),rest);
        }else{option(0,12,60,imageWidth-24,"Activation: "+new String[]{"Manual","Powered","Unpowered"}[draft.trigger()],!fixed);channels=new EditBox(font,leftPos+12,topPos+98,imageWidth-24,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft.channels().toString());channels.setEditable(!fixed);channels.setResponder(value->{channels.setTextColor(ChannelList.parse(value)==null?0xFF8888:0xFFFFFF);dirty=true;});addRenderableWidget(channels);}
        option(4,(imageWidth-200)/2,imageHeight-54,200,"Done",true);ready=true;
    }
    private int endpoint(){return menu.tile.progress()==0?0:menu.tile.progress()==1?1:-1;}
    private String worldFacing(net.minecraft.core.Direction local){var mapped=NativeGearOrientation.vector(menu.tile.mount(),local.getStepX(),local.getStepY(),local.getStepZ());var face=net.minecraft.core.Direction.getNearest(mapped.x,mapped.y,mapped.z);String name=face.getName();return Character.toUpperCase(name.charAt(0))+name.substring(1);}
    public void action(int id,int direction){if(id==4){onClose();return;}if(id==20||id==21){apply();page=id-20;rebuild();return;}var next=draft.choices();switch(id){case 0->next.putInt("Trigger",Math.floorMod(draft.trigger()+direction,3));case 1->next.putInt("Direction",Math.floorMod(draft.direction()+direction,4));case 2->target=!target;case 7->next.putBoolean("RotatePayload",!draft.rotatePayload());case 9->next.putBoolean("JoinArms",!draft.join());case 11->next.putBoolean("CarryLoad",!draft.carryLoad());case 14->next.putBoolean("PushBlocks",!draft.pushBlocks());case 18->next.putBoolean("MiddleHinge",!draft.middleHinge());case 17->next.putInt("FootFacing",Math.floorMod(draft.footFacing()+direction,4));default->{return;}}draft=NativeGearSettings.readChoices(next);apply();rebuild();}
    public boolean apply(){if(!ready)return false;if(channels!=null){var parsed=ChannelList.parse(channels.getValue());if(parsed==null){error="Invalid channel list";return false;}var next=draft.choices();next.putString("Channels",parsed.toString());next.putBoolean("LegacyAnyChannels",false);draft=NativeGearSettings.readChoices(next);}dirty=false;PacketDistributor.sendToServer(new NativeGearSettingsPayload(menu.containerId,menu.selectedPosition(),draft,target,++request));return true;}
    public void acknowledge(NativeGearSettingsPayload.Result result){var state=result.authoritative();if(state.window()!=menu.containerId||!state.owner().equals(menu.selectedPosition())||state.request()!=request)return;pending=result;reconcile();}
    private void reconcile(){if(pending==null||dragging||minecraft.mouseHandler.isLeftPressed())return;var result=pending;if(!menu.rebind(minecraft.player,result.controller(),result.controllerIdentity()))return;pending=null;if(result.authoritative().request()!=request)return;acknowledgedRequest=result.authoritative().request();draft=result.authoritative().settings();target=result.authoritative().deployed();error=result.applied()?"":"Return to rest or clear the new arm shape.";rebuild();}
    @Override protected void containerTick(){super.containerTick();var previous=menu.tile;if(menu.resolve(minecraft.player)&&previous!=menu.tile&&!dirty&&!dragging){draft=menu.tile.settings();target=menu.tile.target();rebuild();}if(endpoint()!=observedEndpoint&&!dirty&&!dragging&&!waitingForReply())rebuild();if(dirty&&--delay<=0){apply();delay=2;}reconcile();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var entry:buttons.entrySet())if(entry.getValue().active&&entry.getValue().isMouseOver(x,y)&&(button==0||entry.getKey()!=4&&entry.getKey()<20)){action(entry.getKey(),button==1?-1:1);return true;}boolean handled=super.mouseClicked(x,y,button);if(button==0&&getFocused() instanceof NativeIntegerSlider)dragging=handled;return handled;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(button==0){dragging=false;if(dirty)apply();reconcile();}return super.mouseReleased(x,y,button);}
    @Override public void onClose(){if(dirty)apply();super.onClose();}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);g.drawString(font,title,leftPos+8,topPos+8,0xFFFFFF,false);if(page==1)g.drawString(font,"Receive channels",leftPos+12,topPos+86,0xFFFFFF,false);if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,imageWidth-24),leftPos+12,topPos+imageHeight-28,0xFFFFAA88,false);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
