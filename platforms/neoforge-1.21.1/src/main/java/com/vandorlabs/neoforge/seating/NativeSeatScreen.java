package com.vandorlabs.neoforge.seating.client;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.neoforge.seating.*;
import com.vandorlabs.ui.DialogInput;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
/** Source chair choices and cached configured-item preview; no Vehicle controls in the stationary seat. */
public final class NativeSeatScreen extends AbstractContainerScreen<NativeSeatMenu> {
    private int armrest,low,high,throttleCount;
    private net.minecraft.client.gui.components.EditBox channels;
    private Button applyArmrest;
    private NativeIntegerSlider armrestMinimum;
    private static final String[] STYLES={"Command","Companion","Operator","Conference","Mess Hall"};
    private final NativeSeatKind kind;private int style,height,angle;private boolean join;private ItemStack preview;private NativeIntegerSlider angleSlider;private final Map<Integer,Button> options=new HashMap<>();
    public NativeSeatScreen(NativeSeatMenu menu,Inventory inventory,Component title){super(menu,inventory,title);kind=menu.tile.kind();armrest=menu.initialArmrest;throttleCount=menu.tile.throttleCount();var settings=menu.tile.settings();style=settings.style;height=kind.adjustablePilotHeight()?menu.tile.pilotHeight():settings.height;angle=settings.angle;join=settings.join;readArmrest();preview();}
    @Override protected void init(){imageWidth=Math.min(260,width-12);imageHeight=kind==NativeSeatKind.CHAIR?210:kind==NativeSeatKind.ADVANCED||kind==NativeSeatKind.COCKPIT?204:140;super.init();rebuild();}
    private void rebuild(){clearWidgets();options.clear();if(kind==NativeSeatKind.COCKPIT){
        add(13,leftPos+14,topPos+35,imageWidth-28,throttleCount==1?"One throttle":"Two throttles");
        buildArmrests();
    }else if(kind==NativeSeatKind.CHAIR){for(int i=0;i<5;i++)add(i,leftPos+14,topPos+25+i*24,126,STYLES[i]);add(11,leftPos+14,topPos+151,112,"Height: "+new String[]{"Low (-2 px)","Middle","High (+2 px)"}[height]);angleSlider=new NativeIntegerSlider(leftPos+130,topPos+151,112,20,0,7,angle,value->Component.literal("Angle: "+value*45+"\u00b0"),value->{if(angle!=value){angle=value;send();}});addRenderableWidget(angleSlider);}else{if(kind.connected())add(0,leftPos+14,topPos+30,imageWidth-28,"Join: "+(join?"On":"Off"));add(11,leftPos+14,topPos+(kind.connected()?57:35),imageWidth-28,"Height: "+(kind==NativeSeatKind.ADVANCED?"+"+(2*height)+" px":kind==NativeSeatKind.PILOT?height:new String[]{"Low (-2 px)","Default","High (+2 px)"}[height]));}if(kind==NativeSeatKind.ADVANCED)buildArmrests();addRenderableWidget(com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(leftPos+imageWidth-102,topPos+imageHeight-35,88,20).build());}
    private void readArmrest(){low=menu.tile.armrestMin(armrest);high=menu.tile.armrestMax(armrest);}
    private void buildArmrests(){
        int x=leftPos+14,w=imageWidth-28,y=topPos+60;
        if(kind!=NativeSeatKind.COCKPIT||throttleCount==2)
            add(12,x,y,w,kind==NativeSeatKind.COCKPIT?(armrest==3?"Left throttle":"Right throttle")
                :(armrest==3?"Left Armrest Slider":"Right Armrest Slider"));
        channels=new net.minecraft.client.gui.components.EditBox(font,x,y+25,w,18,Component.literal("Armrest channels"));
        channels.setMaxLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);
        channels.setValue(menu.tile.armrestChannels(armrest).toString());
        addRenderableWidget(channels);
        int half=(w-4)/2;
        armrestMinimum=new NativeIntegerSlider(x,y+49,half,20,0,13,low,value->Component.literal("Min: "+value),value->{low=value;validateArmrest();});
        addRenderableWidget(armrestMinimum);
        addRenderableWidget(new NativeIntegerSlider(x+half+4,y+49,half,20,2,15,high,value->Component.literal("Max: "+value),value->{high=value;validateArmrest();}));
        applyArmrest=com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(kind==NativeSeatKind.COCKPIT?"Apply Throttle":"Apply Armrest"),ignored->sendArmrest()).bounds(x,topPos+imageHeight-35,104,20).build();
        addRenderableWidget(applyArmrest);
        channels.setResponder(ignored->validateArmrest());
        validateArmrest();
    }
    private void validateArmrest(){
        applyArmrest.active=high-low>=2&&com.vandorlabs.redstone.ChannelList.parse(channels.getValue())!=null;
    }
    private void sendArmrest(){
        var bank=com.vandorlabs.redstone.ChannelList.parse(channels.getValue());
        if(bank!=null&&high-low>=2)PacketDistributor.sendToServer(new NativeChairArmrestPayload(menu.tile.getBlockPos(),menu.containerId,armrest,low,high,bank));
    }
    public net.minecraft.client.gui.components.EditBox armrestChannelsField(){return channels;}
    public Button armrestApply(){return applyArmrest;}
    public NativeIntegerSlider armrestMinimum(){return armrestMinimum;}
    private void add(int id,int x,int y,int w,String text){var button=com.vandorlabs.neoforge.client.NativeDialogButton.builder(Component.literal(text),ignored->action(id,1)).bounds(x,y,w,20).build();options.put(id,button);addRenderableWidget(button);}
    private void action(int id,int direction){if(id==13){
        throttleCount=3-throttleCount;
        if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,throttleCount);
        if(throttleCount==1){armrest=3;readArmrest();}
        rebuild();
        return;
    }if(id==12){armrest=armrest==3?4:3;readArmrest();rebuild();return;}if(kind==NativeSeatKind.CHAIR&&id<5)style=id;else if(id==11)height=DialogInput.cycle(height,kind.adjustablePilotHeight()?4:3,direction);else if(kind.connected()&&id==0)join=!join;send();rebuild();}
    private void preview(){var tag=new net.minecraft.nbt.CompoundTag();var settings=new net.minecraft.nbt.CompoundTag();settings.putInt("ChairStyle",style);settings.putInt("ChairHeight",height);settings.putInt("ChairAngle",angle);settings.putBoolean("SeatJoin",join);settings.putInt("PilotHeight",height);tag.put(NativeChairEntity.ITEM_SETTINGS,settings);preview=new ItemStack(NativeSeatBindings.ITEMS.get(kind));preview.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
    private void send(){preview();PacketDistributor.sendToServer(new NativeSeatPayload(menu.tile.getBlockPos(),menu.containerId,style,height,angle,join));}
    public Button option(int id){return options.get(id);}public NativeIntegerSlider angleSlider(){return angleSlider;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var entry:options.entrySet())if(entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){com.vandorlabs.neoforge.client.NativeDialogVisuals.frame(g,leftPos,topPos,imageWidth,imageHeight,kind==NativeSeatKind.CHAIR?com.vandorlabs.ui.DialogStyle.CHAIR:com.vandorlabs.ui.DialogStyle.LANDING);if(kind==NativeSeatKind.CHAIR)g.fill(leftPos+6,topPos+6,leftPos+imageWidth-6,topPos+imageHeight-6,0xFF343C49);g.drawString(font,title,leftPos+14,topPos+(kind==NativeSeatKind.CHAIR?11:12),0xFFFFFFFF,false);com.vandorlabs.neoforge.client.NativeDialogVisuals.logo(g,leftPos,topPos,imageWidth,24);if(kind==NativeSeatKind.CHAIR){g.fill(leftPos+151,topPos+26,leftPos+243,topPos+135,0xFF111820);g.pose().pushPose();g.pose().translate(leftPos+158,topPos+38,0);g.pose().scale(4.8F,4.8F,1);g.renderItem(preview,0,0);g.pose().popPose();}else if(kind.connected())g.drawString(font,"Default legs: +1 px",leftPos+14,topPos+84,0xFFBBBBBB,false);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
