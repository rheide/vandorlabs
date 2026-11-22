package com.vandorlabs.neoforge.ports.client;

import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact Ports/Redstone tabs use the same left/previous-right interactions as other editors. */
public final class NativePortScreen extends AbstractContainerScreen<NativePortMenu> {
    private final ResourcePortSettings.Face[] faces=new ResourcePortSettings.Face[6];
    private ResourcePortSettings.OnTexture artwork;private boolean pull;private int mode,page,housing;private NativeMaterialPicker picker;private boolean blockShape;
    private com.vandorlabs.neoforge.client.NativeInlineTransmissionWidget transmitter;
    private int previousGuiScale=-1;private boolean closing;
    private String throughput,channelText,error="";private EditBox rate,channels;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativePortScreen(NativePortMenu menu,Inventory inventory,Component title){super(menu,inventory,title);var config=menu.tile.configuration();for(int i=0;i<6;i++)faces[i]=config.settings().face(i);artwork=config.settings().onTexture;pull=config.settings().pullInputs;mode=config.redstoneMode();housing=config.settings().housingChoice;blockShape=battery()!=null&&battery().blockShape();throughput=config.settings().throughput==0?"":Long.toString(config.settings().throughput);channelText=config.channels().toString();}
    private com.vandorlabs.neoforge.reservoir.NativeBatteryEntity battery(){return menu.tile.portBlockEntity() instanceof com.vandorlabs.neoforge.reservoir.NativeBatteryEntity value?value:null;}
    public Button control(int id){return buttons.get(id);}
    public EditBox rateField(){return rate;}
    public EditBox channelField(){return channels;}
    public NativeMaterialPicker housingPicker(){return picker;}
    public NativePortData.Configuration editingConfiguration(){capture();return draft();}
    @Override protected void init(){imageWidth=com.vandorlabs.ui.ResourcePortDialog.WIDTH;imageHeight=com.vandorlabs.ui.ResourcePortDialog.HEIGHT;if(!closing&&(width<imageWidth+12||height<imageHeight+12)&&minecraft.getWindow().getGuiScale()>1){if(previousGuiScale<0)previousGuiScale=minecraft.options.guiScale().get();minecraft.options.guiScale().set((int)minecraft.getWindow().getGuiScale()-1);minecraft.resizeDisplay();return;}super.init();rebuild();}
    @Override public void removed(){closing=true;super.removed();if(previousGuiScale>=0){int restore=previousGuiScale;previousGuiScale=-1;minecraft.options.guiScale().set(restore);minecraft.resizeDisplay();}}
    private void button(int id,int x,int y,int w,String label){var button=com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(label),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void capture(){if(rate!=null)throughput=rate.getValue();if(channels!=null)channelText=channels.getValue();}
    private void rebuild(){
        capture();if(transmitter!=null)transmitter.hide();clearWidgets();buttons.clear();rate=channels=null;picker=null;
        var presentation=new ResourcePortSettings(faces,0,pull,artwork,housing);
        for(var control:com.vandorlabs.ui.ResourcePortDialog.controls(leftPos,topPos,page,presentation,mode,battery()!=null,blockShape)){
            int id=com.vandorlabs.ui.ResourcePortDialog.nativeId(control.id);Button widget=control.tab?new com.vandorlabs.neoforge.client.NativeDialogTabButton(control.x,control.y,control.width,control.height,Component.literal(control.label),ignored->action(id,1)):com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(control.label),ignored->action(id,1)).bounds(control.x,control.y,control.width,control.height).build();widget.active=control.enabled;buttons.put(id,widget);addRenderableWidget(widget);
        }
        if(page==0){rate=new EditBox(font,leftPos+12,topPos+com.vandorlabs.ui.ResourcePortDialog.RATE_Y,com.vandorlabs.ui.ResourcePortDialog.FIELD_WIDTH,com.vandorlabs.ui.ResourcePortDialog.FIELD_HEIGHT,Component.literal("Throughput"));rate.setMaxLength(19);rate.setValue(throughput);rate.setResponder(value->{throughput=value;validity();});addRenderableWidget(rate);}
        else if(page==1){channels=new EditBox(font,leftPos+12,topPos+com.vandorlabs.ui.ResourcePortDialog.CHANNEL_Y,com.vandorlabs.ui.ResourcePortDialog.FIELD_WIDTH,com.vandorlabs.ui.ResourcePortDialog.FIELD_HEIGHT,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(channelText);channels.setResponder(value->{channelText=value;validity();});addRenderableWidget(channels);
            if(battery()!=null){
                if(transmitter==null)transmitter=com.vandorlabs.neoforge.reservoir.client.NativeBufferTransmitWidget.create(battery(),menu.containerId,font,leftPos+12,topPos+166,imageWidth-24);
                transmitter.layout(leftPos+12,topPos+166,imageWidth-24);
                transmitter.controls().forEach(this::addRenderableWidget);
            }
        }
        else{picker=new NativeMaterialPicker(housing,id->ModernFinishCatalog.option(id).general(),choice->{housing=choice;send();}).specialMaterial(-1,"Original artwork","Housing",battery()==null?"vandorlabs:blocks/resource_ports/port_hull":"vandorlabs:blocks/imported/power/battery_cell_bank");picker.layout(leftPos+12,topPos+62,com.vandorlabs.ui.ResourcePortDialog.housingWidth(battery()!=null),com.vandorlabs.ui.ResourcePortDialog.housingRows(),false);addRenderableWidget(picker.searchField());}
        validity();
    }
    private NativePortData.Configuration draft(){Long amount=ResourcePortSettings.parseThroughput(throughput);var bank=ChannelList.parse(channelText);if(amount==null||bank==null)throw new IllegalArgumentException("Invalid throughput or channels");return new NativePortData.Configuration(new ResourcePortSettings(faces,amount,pull,artwork,housing),mode,bank);}
    private boolean validity(){try{draft();error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}if(buttons.containsKey(9))buttons.get(9).active=error.isEmpty();return error.isEmpty();}
    private boolean send(){capture();if(!validity())return false;PacketDistributor.sendToServer(new NativePortSettingsPayload(menu.containerId,menu.tile.getBlockPos(),draft()));return true;}
    public void action(int id,int direction){capture();if(id==9){if(send())super.onClose();return;}if(id==20||id==21||id==22){if(!send())return;page=id-20;rebuild();return;}if(id==10&&battery()!=null){blockShape=!blockShape;PacketDistributor.sendToServer(new com.vandorlabs.neoforge.reservoir.NativeBufferSettingsPayload(menu.containerId,menu.tile.getBlockPos(),housing,1,blockShape));rebuild();return;}if(id>=0&&id<6)faces[id]=ResourcePortSettings.Face.values()[Math.floorMod(faces[id].ordinal()+direction,3)];else if(id==6)pull=!pull;else if(id==7)artwork=ResourcePortSettings.OnTexture.values()[Math.floorMod(artwork.ordinal()+direction,3)];else if(id==8)mode=Math.floorMod(mode+direction,3);else return;send();rebuild();}
    @Override public boolean mouseClicked(double x,double y,int mouse){if(page==1&&transmitter!=null&&transmitter.mouseClicked(x,y,mouse))return true;if(mouse==1)for(var entry:buttons.entrySet())if((entry.getKey()<9||entry.getKey()==10)&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}return picker!=null&&picker.click(x,y,mouse)||super.mouseClicked(x,y,mouse);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==1&&transmitter!=null&&transmitter.specialKey(key))return true;if(picker!=null&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE&&picker.inventoryPicker()!=null){picker.closeInventory();return true;}return picker!=null&&picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(transmitter!=null)transmitter.hide();send();super.onClose();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
        com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(graphics,font,title,leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.ResourcePortDialog.STYLE);if(picker!=null)picker.render(graphics,x,y);
        if(page==0)graphics.drawString(font,com.vandorlabs.ui.ResourcePortDialog.RATE_LABEL,leftPos+12,topPos+145,0xd5e0e8,false);
        else if(page==1){graphics.drawString(font,com.vandorlabs.ui.ResourcePortDialog.CHANNEL_LABEL,leftPos+12,topPos+70,0xd5e0e8,false);if(battery()!=null){graphics.drawString(font,"Transmit fullness",leftPos+12,topPos+150,0xd5e0e8,false);transmitter.renderLabels(graphics,font);}else graphics.drawWordWrap(font,Component.literal(com.vandorlabs.ui.ResourcePortDialog.CHANNEL_HINT),leftPos+12,topPos+150,imageWidth-24,0xc9d9e2);}
        else if(battery()!=null){int gx=leftPos+imageWidth/2+8,gy=topPos+68,amount=battery().amount();graphics.drawString(font,"Charge",gx,gy,0xd5e0e8,false);graphics.drawString(font,amount+" / "+battery().capacity()+" RF",gx,gy+18,0xc9d9e2,false);graphics.fill(gx,gy+42,leftPos+imageWidth-12,gy+57,0xff14241a);graphics.fill(gx+1,gy+43,gx+1+(int)((imageWidth-21-(imageWidth/2+8))*Math.clamp(amount/(double)battery().capacity(),0,1)),gy+56,0xff62ed82);}
        if(!error.isEmpty())graphics.drawString(font,font.plainSubstrByWidth(error,imageWidth-24),leftPos+12,topPos+imageHeight-44,0xff8888,false);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);if(page==0&&rate!=null&&rate.getValue().isEmpty()&&!rate.isFocused())graphics.drawString(font,"Unlimited",leftPos+16,topPos+163,0x8294a4,false);if(page==0&&rate!=null)graphics.drawString(font,menu.tile.portKind().units+"/tick",rate.getX()+rate.getWidth()-font.width(menu.tile.portKind().units+"/tick")-4,rate.getY()+5,0x8294a4,false);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
