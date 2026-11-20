package com.vandorlabs.neoforge.rack.client;

import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** The shared Rack notebook and native housing/transmission/sample widgets. */
public final class NativeRackScreen extends AbstractContainerScreen<NativeRackMenu> {
    private int page,subpage,housing,knownKinds;private boolean emit,powerOut;
    private final AutopilotModuleSettings[] drafts=new AutopilotModuleSettings[4];
    private final long[] sequences=new long[4];
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    private NativeMaterialPicker picker;private NativeInlineTransmissionWidget transmission;
    private ItemStack marker=ItemStack.EMPTY;
    private NativeInventorySamplePicker samples;private EditBox channels;private String error="";
    public NativeRackScreen(NativeRackMenu menu,Inventory inventory,Component title){super(menu,inventory,title);housing=menu.tile.housing();emit=menu.tile.emits();powerOut=menu.tile.powerOut();reload();}
    private void reload(){knownKinds=menu.tile.kinds();for(int slot=0;slot<4;slot++){drafts[slot]=menu.tile.kind(slot)==2?menu.tile.moduleSettings(slot):null;sequences[slot]=menu.tile.sequence(slot);}if(page>=2&&drafts[page-2]==null)page=0;}
    public Button control(int id){return buttons.get(id);}
    public EditBox channelField(){return channels;}
    public NativeMaterialPicker housingPicker(){return picker;}
    public NativeInlineTransmissionWidget transmission(){return transmission;}
    public NativeInventorySamplePicker samplePicker(){return samples;}
    @Override protected void init(){imageWidth=Math.min(348,width-12);imageHeight=Math.min(250,height-12);super.init();rebuild();}
    private void button(int id,int x,int y,int w,String caption){Button button=(id>=100)?new com.vandorlabs.neoforge.client.NativeDialogTabButton(leftPos+x,topPos+y,w,20,Component.literal(caption),ignored->action(id,1)):com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(caption),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void rebuild(){
        var previous=picker;clearWidgets();buttons.clear();picker=null;samples=null;channels=null;int count=ModuleRackPolicy.autopilots(knownKinds),available=imageWidth-24,index=2;
        button(100,12,32,ModuleRackTabs.width(available,count,0),"Housing");button(101,12+ModuleRackTabs.offset(available,count,1),32,ModuleRackTabs.width(available,count,1),"Redstone");
        for(int slot=0;slot<4;slot++)if(drafts[slot]!=null){button(102+slot,12+ModuleRackTabs.offset(available,count,index),32,ModuleRackTabs.width(available,count,index),ModuleRackTabs.moduleLabel(slot));index++;}
        buttons.get(100+page).active=false;
        if(page==0){button(8,12,imageHeight-52,imageWidth-24,"Power Port: "+(powerOut?"Out":"In"));picker=previous!=null?previous:new NativeMaterialPicker(housing,id->ModernFinishCatalog.option(id).general(),choice->{housing=choice;sendHousing();}).specialMaterial(-1,"Original artwork","Housing","vandorlabs:blocks/module_rack/housing");picker.layout(leftPos+12,topPos+66,imageWidth-32,Math.max(2,(imageHeight-142)/20),false);addRenderableWidget(picker.searchField());}
        else if(page==1){button(0,12,64,imageWidth-24,"Emit Redstone: "+(emit?"On":"Off"));if(transmission==null){var bank=menu.tile.transmission();transmission=new NativeInlineTransmissionWidget(font,leftPos+12,topPos+92,imageWidth-24,bank.channels(),bank.range(),bank.local(),(bankChannels,range,local)->PacketDistributor.sendToServer(NativeRackSettingsPayload.housing(menu.containerId,menu.tile.getBlockPos(),housing,emit,new NativeRackData.Transmission(bankChannels,range,local,true))));}else transmission.layout(leftPos+12,topPos+92,imageWidth-24);for(var widget:transmission.controls())addRenderableWidget(widget);}
        else {int half=(imageWidth-28)/2;button(200,12,58,half,"General");button(201,16+half,58,half,"Redstone");buttons.get(200+subpage).active=false;var config=drafts[page-2];
            if(subpage==1){channels=new EditBox(font,leftPos+12,topPos+100,imageWidth-24,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(config.channels.toString());addRenderableWidget(channels);button(2,12,130,imageWidth-24,"Activation: "+com.vandorlabs.ui.RedstoneActivation.label(config.activation));}
            else {marker=NativeRackData.marker(config,minecraft.level.registryAccess());samples=NativeInventorySamplePicker.hotbar(leftPos+(imageWidth-162)/2,topPos+138,NativeRackScreen::routeBlock,stack->{var key=BuiltInRegistries.BLOCK.getKey(((BlockItem)stack.getItem()).getBlock());var old=drafts[page-2];drafts[page-2]=new AutopilotModuleSettings(old.channels,old.activation,key.toString(),0);marker=stack.copyWithCount(1);sendModule();});}
        }button(1,12,imageHeight-30,imageWidth-24,"Done");
    }
    private static boolean routeBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&item.getBlock()!=Blocks.AIR;}
    private boolean capture(){if(channels!=null){var parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null){error="Invalid receiving channels";return false;}var old=drafts[page-2];drafts[page-2]=new AutopilotModuleSettings(parsed,old.activation,old.marker,old.markerMetadata);}error="";return true;}
    private void sendModule(){int slot=page-2;if(slot>=0&&slot<4&&drafts[slot]!=null)PacketDistributor.sendToServer(NativeRackSettingsPayload.module(menu.containerId,menu.tile.getBlockPos(),slot,sequences[slot],drafts[slot]));}
    private void sendHousing(){if(transmission!=null){transmission.flush();var bank=ChannelList.parse(transmission.channelField().getValue());if(bank!=null)PacketDistributor.sendToServer(NativeRackSettingsPayload.housing(menu.containerId,menu.tile.getBlockPos(),housing,emit,new NativeRackData.Transmission(bank,com.vandorlabs.redstone.TransmissionSettings.RANGES[transmission.rangeIndex()],transmission.localRedstone(),true)));}else PacketDistributor.sendToServer(NativeRackSettingsPayload.housing(menu.containerId,menu.tile.getBlockPos(),housing,emit,menu.tile.transmission()));}
    public void action(int id,int direction){if(id==8){powerOut=!powerOut;PacketDistributor.sendToServer(NativeRackSettingsPayload.power(menu.containerId,menu.tile.getBlockPos(),powerOut));rebuild();return;}if(!capture())return;if(id>=100&&id<=105){if(page>=2)sendModule();if(transmission!=null)transmission.hide();page=id-100;rebuild();return;}if(id==200||id==201){sendModule();subpage=id-200;rebuild();return;}if(id==0){emit=!emit;sendHousing();rebuild();}else if(id==2){var old=drafts[page-2];drafts[page-2]=new AutopilotModuleSettings(old.channels,Math.floorMod(old.activation+direction,3),old.marker,old.markerMetadata);sendModule();rebuild();}else if(id==1)onClose();}
    @Override public void containerTick(){super.containerTick();boolean changed=knownKinds!=menu.tile.kinds();for(int slot=0;slot<4;slot++)if(drafts[slot]!=null&&sequences[slot]!=menu.tile.sequence(slot))changed=true;if(changed){reload();rebuild();}}
    @Override public boolean mouseClicked(double x,double y,int button){if(transmission!=null&&page==1&&transmission.mouseClicked(x,y,button))return true;if(samples!=null&&samples.click((int)x,(int)y,button))return true;if(picker!=null&&picker.click(x,y,button))return true;if(button==1)for(int id:new int[]{0,2})if(buttons.containsKey(id)&&buttons.get(id).isMouseOver(x,y)){action(id,-1);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==1&&transmission!=null&&transmission.specialKey(key))return true;return picker!=null&&picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(!capture())return;if(page>=2)sendModule();sendHousing();super.onClose();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xc0101010,0xd0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(graphics,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.DialogStyle.RACK);if(picker!=null)picker.render(graphics,x,y);else if(page==1&&transmission!=null)transmission.renderLabels(graphics,font);else if(page>=2){graphics.drawString(font,subpage==0?"Route marker block":"Receive channels",leftPos+12,topPos+84,0xdae8f0,false);if(subpage==0){var item=marker;graphics.renderItem(item,leftPos+12,topPos+102);graphics.drawString(font,font.plainSubstrByWidth(item.getHoverName().getString(),imageWidth-52),leftPos+36,topPos+102,0xffffff,false);graphics.drawString(font,font.plainSubstrByWidth(drafts[page-2].marker,imageWidth-52),leftPos+36,topPos+113,0xaaaaaa,false);}graphics.drawWordWrap(font,Component.literal("Vehicle navigation is not available on this version."),leftPos+12,topPos+imageHeight-52,imageWidth-24,0xeaaf66);}}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);if(samples!=null)samples.draw(graphics,x,y);if(!error.isEmpty())graphics.drawString(font,error,leftPos+12,topPos+imageHeight-42,0xff7777,false);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
