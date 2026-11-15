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
    private static final String[] STYLES={"Command","Companion","Operator","Conference","Mess Hall"};
    private final NativeSeatKind kind;private int style,height,angle;private boolean join;private ItemStack preview;private NativeIntegerSlider angleSlider;private final Map<Integer,Button> options=new HashMap<>();
    public NativeSeatScreen(NativeSeatMenu menu,Inventory inventory,Component title){super(menu,inventory,title);kind=menu.tile.kind();var settings=menu.tile.settings();style=settings.style;height=kind==NativeSeatKind.PILOT?menu.tile.pilotHeight():settings.height;angle=settings.angle;join=settings.join;preview();}
    @Override protected void init(){imageWidth=Math.min(260,width-12);imageHeight=kind==NativeSeatKind.CHAIR?210:140;super.init();rebuild();}
    private void rebuild(){clearWidgets();options.clear();if(kind==NativeSeatKind.CHAIR){for(int i=0;i<5;i++)add(i,leftPos+14,topPos+25+i*24,126,STYLES[i]);add(11,leftPos+14,topPos+151,112,"Height: "+new String[]{"Low (-2 px)","Middle","High (+2 px)"}[height]);angleSlider=new NativeIntegerSlider(leftPos+130,topPos+151,112,20,0,7,angle,value->Component.literal("Angle: "+value*45+"\u00b0"),value->{if(angle!=value){angle=value;send();}});addRenderableWidget(angleSlider);}else{if(kind.connected())add(0,leftPos+14,topPos+30,imageWidth-28,"Join: "+(join?"On":"Off"));add(11,leftPos+14,topPos+(kind.connected()?57:35),imageWidth-28,"Height: "+(kind==NativeSeatKind.PILOT?height:new String[]{"Low (-2 px)","Default","High (+2 px)"}[height]));}addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(leftPos+imageWidth-102,topPos+imageHeight-35,88,20).build());}
    private void add(int id,int x,int y,int w,String text){var button=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(x,y,w,20).build();options.put(id,button);addRenderableWidget(button);}
    private void action(int id,int direction){if(kind==NativeSeatKind.CHAIR&&id<5)style=id;else if(id==11)height=DialogInput.cycle(height,kind==NativeSeatKind.PILOT?4:3,direction);else if(kind.connected()&&id==0)join=!join;send();rebuild();}
    private void preview(){var tag=new net.minecraft.nbt.CompoundTag();var settings=new net.minecraft.nbt.CompoundTag();settings.putInt("ChairStyle",style);settings.putInt("ChairHeight",height);settings.putInt("ChairAngle",angle);settings.putBoolean("SeatJoin",join);settings.putInt("PilotHeight",height);tag.put(NativeChairEntity.ITEM_SETTINGS,settings);preview=new ItemStack(NativeSeatBindings.ITEMS.get(kind));preview.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
    private void send(){preview();PacketDistributor.sendToServer(new NativeSeatPayload(menu.tile.getBlockPos(),menu.containerId,style,height,angle,join));}
    public Button option(int id){return options.get(id);}public NativeIntegerSlider angleSlider(){return angleSlider;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var entry:options.entrySet())if(entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);return super.mouseReleased(x,y,button);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xEE171B23);g.fill(leftPos+6,topPos+6,leftPos+imageWidth-6,topPos+imageHeight-6,0xFF343C49);g.drawString(font,title,leftPos+14,topPos+10,0xFFFFFFFF,false);if(kind==NativeSeatKind.CHAIR){g.fill(leftPos+151,topPos+26,leftPos+243,topPos+135,0xFF20242D);g.pose().pushPose();g.pose().translate(leftPos+158,topPos+38,0);g.pose().scale(4.8F,4.8F,1);g.renderItem(preview,0,0);g.pose().popPose();}else if(kind.connected())g.drawString(font,"Default legs: +1 px",leftPos+14,topPos+84,0xFFBBBBBB,false);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
