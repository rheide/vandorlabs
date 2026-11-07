package com.vandorlabs.neoforge.client;

import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated reader dialog: immediate Source option edits and a separate main transmission draft. */
public final class NativeCredentialReaderScreen extends AbstractContainerScreen<NativeCredentialReaderMenu> {
    private final NativeCredentialFilter credentials;
    private final Consumer<ReaderSettingsPayload> sendSettings;
    private final Consumer<ReaderTransmissionPayload> sendTransmission;
    private final Map<Integer,Button> buttons=new HashMap<>();
    private NativeInlineTransmissionWidget transmission;
    private EditBox legacyChannels;
    private int page=1,behavior,allowed,mountHeight,tilt,direction;
    public NativeCredentialReaderScreen(NativeCredentialReaderMenu menu,Inventory inventory,Component title){this(menu,inventory,title,new NativeCredentialFilterPicker(company->menu.tile.chip()?NativeCredentialItems.dataChip(company):NativeCredentialItems.accessCard(company),menu.tile.chip()),PacketDistributor::sendToServer,PacketDistributor::sendToServer);}
    public NativeCredentialReaderScreen(NativeCredentialReaderMenu menu,Inventory inventory,Component title,NativeCredentialFilter credentials,Consumer<ReaderSettingsPayload> settings,Consumer<ReaderTransmissionPayload> transmission){
        super(menu,inventory,title);this.credentials=Objects.requireNonNull(credentials);sendSettings=Objects.requireNonNull(settings);sendTransmission=Objects.requireNonNull(transmission);var visual=menu.tile.visual();behavior=visual.behavior();allowed=visual.allowed();mountHeight=visual.mount().height;tilt=visual.mount().tilt;direction=visual.mount().direction;imageWidth=308;imageHeight=224;
    }
    public int page(){return page;}
    public int behaviorDraft(){return behavior;}
    public int acceptedCompany(){return allowed;}
    public ControlMountSettings mountDraft(){return ControlMountSettings.of(mountHeight,tilt,direction);}
    public Button button(int id){return buttons.get(id);}
    public EditBox channelField(){return legacyChannels;}
    public NativeInlineTransmissionWidget inlineWidget(){return transmission;}
    public int panelLeft(){return leftPos;}public int panelTop(){return topPos;}
    @Override protected void init(){imageWidth=Math.min(308,width-12);imageHeight=224;super.init();
        String text=legacyChannels==null?menu.tile.getRedstoneChannels().toString():legacyChannels.getValue();legacyChannels=new EditBox(font,leftPos+12,topPos+76,imageWidth-24,18,Component.literal("Reader channel compatibility field"));legacyChannels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);legacyChannels.setValue(text);legacyChannels.visible=false;
        if(transmission==null)transmission=new NativeInlineTransmissionWidget(font,leftPos+12,topPos+74,imageWidth-24,menu.tile.transmittedChannels(),menu.tile.transmissionRange(),menu.tile.localRedstone(),(channels,range,local)->sendTransmission.accept(new ReaderTransmissionPayload(menu.containerId,channels,range,local)));
        else transmission.layout(leftPos+12,topPos+74,imageWidth-24);rebuild();
    }
    private void option(int id,int x,int y,int width,String text){var button=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(x,y,width,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void rebuild(){setFocused(null);clearWidgets();buttons.clear();
        option(101,leftPos+12,topPos+34,138,"Security");option(100,leftPos+154,topPos+34,Math.max(40,imageWidth-166),"Redstone");buttons.get(101).active=page!=1;buttons.get(100).active=page!=0&&page!=2;
        if(page==0){option(90,leftPos+12,topPos+74,imageWidth-24,"Redstone Transmitter...");option(1,leftPos+12,topPos+144,imageWidth-24,CredentialCatalog.behaviorName(behavior));}
        else if(page==1){option(4,leftPos+12,topPos+114,imageWidth-24,heightLabel());int half=(imageWidth-28)/2;option(5,leftPos+12,topPos+138,half,tiltLabel());option(6,leftPos+16+half,topPos+138,imageWidth-28-half,directionLabel());}
        else {for(var control:transmission.controls())addRenderableWidget(control);option(90,leftPos+12,topPos+144,imageWidth-24,"Back");}
        option(3,leftPos+12,topPos+imageHeight-30,imageWidth-24,"Done");
    }
    private String heightLabel(){return "Base height: "+(mountHeight==0?"Standard":"+"+mountHeight*2+" px");}
    private String tiltLabel(){return "Tilt: "+(tilt==0?"Flat":tilt*15+" deg");}
    private String directionLabel(){return new String[]{"Forward","Right","Backward","Left"}[direction];}
    private boolean save(){String text=legacyChannels.getValue();if(text.length()>256||ChannelList.parse(text)==null){legacyChannels.setTextColor(0xFF7777);return false;}legacyChannels.setTextColor(0xFFFFFF);sendSettings.accept(new ReaderSettingsPayload(menu.containerId,behavior,false,allowed,text,mountDraft()));return true;}
    private void action(int id,int step){
        if(id==100||id==101){if(page==2)transmission.hide();page=id-100;rebuild();return;}
        if(id==3){onClose();return;}
        if(id==90){if(page==2){transmission.hide();page=0;}else {save();page=2;}rebuild();return;}
        if(id==1)behavior=DialogInput.cycle(behavior,4,step);if(id==4)mountHeight=DialogInput.cycle(mountHeight,4,step);if(id==5)tilt=DialogInput.cycle(tilt,4,step);if(id==6)direction=DialogInput.cycle(direction,4,step);save();rebuild();
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1)for(int id:new int[]{1,4,5,6}){var control=buttons.get(id);if(control!=null&&control.active&&control.isMouseOver(x,y)){control.playDownSound(minecraft.getSoundManager());action(id,-1);return true;}}
        if(page==1&&credentials.click(x,y,button,leftPos+12,topPos+82,allowed,next->{allowed=next;save();}))return true;
        if(page==2&&transmission.mouseClicked(x,y,button))return true;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==2&&transmission.specialKey(key))return true;return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(transmission!=null)transmission.flush();super.onClose();}
    @Override public void removed(){if(transmission!=null)transmission.flush();super.removed();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+28,0xFF304858);graphics.drawString(font,title,leftPos+12,topPos+10,0xFFFFFF,false);if(page==1){graphics.drawString(font,"Accepted credential",leftPos+12,topPos+68,0xDAE8F0,false);credentials.render(graphics,font,leftPos+12,topPos+82,allowed);}else if(page==2)transmission.renderLabels(graphics,font);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
