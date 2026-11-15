package com.vandorlabs.neoforge.legacylanding.client;
import com.vandorlabs.neoforge.legacylanding.*;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogInput;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
/** Source General/Redstone layout and previous-value cycling, with one bounded settings packet. */
public final class NativeLegacyLandingScreen extends AbstractContainerScreen<NativeLegacyLandingMenu> {
 private NativeLegacyLandingEntity seenRoot;
 private int page,mode,pixels,size;private EditBox channels;private String channelText;private boolean dirty;private final Map<Integer,Button> options=new HashMap<>();
 public NativeLegacyLandingScreen(NativeLegacyLandingMenu menu,Inventory inventory,Component title){super(menu,inventory,title);seenRoot=menu.root();var cfg=seenRoot==null?NativeLegacyLandingData.defaults():seenRoot.settings();mode=cfg.mode();pixels=cfg.pixels();size=cfg.size();channelText=cfg.channels().toString();}
 @Override protected void init(){imageWidth=Math.min(300,width-12);imageHeight=Math.min(246,height-12);super.init();rebuild();}
 private void add(int id,int y,String text){var button=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(leftPos+14,topPos+y,imageWidth-28,20).build();options.put(id,button);addRenderableWidget(button);}
 private void rebuild(){if(channels!=null)channelText=channels.getValue();channels=null;clearWidgets();options.clear();if(page==0){add(4,66,"Size: "+sizeName());addRenderableWidget(new NativeIntegerSlider(leftPos+14,topPos+98,imageWidth-28,20,0,8,pixels/8,n->Component.literal("Extended length: "+n/2D+" blocks"),n->{pixels=n*8;dirty=true;}));}else{channels=new EditBox(font,leftPos+14,topPos+68,imageWidth-28,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(channelText);channels.setResponder(value->{channels.setTextColor(ChannelList.parse(value)==null?0xFF8888:0xFFFFFF);dirty=true;});addRenderableWidget(channels);add(0,96,"Activation: "+new String[]{"Manual","Powered","Unpowered"}[mode]);}add(3,imageHeight-30,"Done");}
 private String sizeName(){if(menu.root().kind().nonWheeled)return size==4?"Large (2x2)":"Small (1 block)";return new String[]{"Small","Medium","Large","Extra large","Extra large (2x2)"}[size];}
 private boolean send(){var parsed=ChannelList.parse(channels==null?channelText:channels.getValue());if(parsed==null)return false;PacketDistributor.sendToServer(new NativeLegacyLandingPayload(menu.selected,menu.identity,menu.containerId,new NativeLegacyLandingData.Configuration(mode,parsed,pixels,size)));dirty=false;return true;}
 public void action(int id,int direction){if(id==3){if(send())onClose();return;}if(id==0)mode=DialogInput.cycle(mode,3,direction);if(id==4)size=menu.root().kind().nonWheeled?(size==0?4:0):DialogInput.cycle(size,5,direction);dirty=true;send();rebuild();}
 @Override protected void containerTick(){super.containerTick();var root=menu.root();if(root!=null&&root!=seenRoot&&!dirty){seenRoot=root;var cfg=root.settings();mode=cfg.mode();pixels=cfg.pixels();size=cfg.size();channelText=cfg.channels().toString();channels=null;rebuild();}}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&y>=topPos+32&&y<topPos+52&&x>=leftPos+14&&x<leftPos+imageWidth-14){send();page=x<leftPos+imageWidth/2?0:1;rebuild();return true;}if(button==1)for(var e:options.entrySet())if(e.getKey()!=3&&e.getValue().isMouseOver(x,y)){action(e.getKey(),-1);return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(dirty)send();return super.mouseReleased(x,y,button);}
 @Override public void onClose(){if(dirty)send();super.onClose();}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);g.drawString(font,title,leftPos+14,topPos+12,0xFFFFFF,false);for(int tab=0;tab<2;tab++){int tx=leftPos+14+tab*(imageWidth-28)/2;g.fill(tx,topPos+32,tx+(imageWidth-28)/2,topPos+52,page==tab?0xFF365568:0xFF203540);g.drawCenteredString(font,tab==0?"General":"Redstone",tx+(imageWidth-28)/4,topPos+38,0xFFFFFF);}if(page==1){g.drawString(font,"Receive channels",leftPos+14,topPos+54,0xDDDDDD,false);g.drawString(font,"Manual: right-click to move",leftPos+14,topPos+146,0xBBBBBB,false);}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
