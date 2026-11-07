package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated two-page released Ramp controls; no world mutation or runtime success simulation. */
public final class NativeRampScreen extends AbstractContainerScreen<NativeRampMenu> {
 private final Consumer<RampSettingsPayload> send;
 private final Map<Integer,Button> buttons=new LinkedHashMap<>();
 private NativeRampDraft draft;private NativeRampMenu.Status status,observed;
 private NativeIntegerSlider start,end;private EditBox tread,channels;
 private String treadText,channelText;private int page,request,lastStart,lastEnd;
 private boolean ready,dragging;private RampSettingsPayload.Result pending;
 public NativeRampScreen(NativeRampMenu menu,Inventory inventory,Component title){this(menu,inventory,title,PacketDistributor::sendToServer);}
 public NativeRampScreen(NativeRampMenu menu,Inventory inventory,Component title,Consumer<RampSettingsPayload> send){super(menu,inventory,title);this.send=Objects.requireNonNull(send);draft=menu.draft();status=menu.status();observed=status;treadText=Integer.toString(draft.settings().treadPixels());channelText=draft.settings().channels().isEmpty()?"":draft.settings().channels().toString();lastStart=draft.settings().startHalf();lastEnd=draft.settings().endHalf();}
 public static void register(RegisterMenuScreensEvent event,MenuType<NativeRampMenu> type){event.<NativeRampMenu,NativeRampScreen>register(type,(menu,inventory,title)->new NativeRampScreen(menu,inventory,title));RampSettingsPayload.bindResults(result->{if(Minecraft.getInstance().screen instanceof NativeRampScreen screen)screen.acknowledge(result);});}
 public NativeRampDraft editingDraft(){return draft;}public NativeRampMenu.Status displayedStatus(){return status;}public Button button(int id){return buttons.get(id);}public EditBox treadField(){return tread;}public EditBox channelField(){return channels;}public NativeIntegerSlider startSlider(){return start;}public NativeIntegerSlider endSlider(){return end;}public int page(){return page;}
 @Override protected void init(){imageWidth=320;imageHeight=Math.min(248,height-8);super.init();rebuild();}
 private void option(int id,int x,int y,int width,int height,String text,boolean active){var button=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(leftPos+x,topPos+y,width,height).build();button.active=active;buttons.put(id,button);addRenderableWidget(button);}
 private static String offset(String title,int half){return title+" offset: "+half/2D+" blocks";}
 private void remember(){if(tread!=null)treadText=tread.getValue();if(channels!=null)channelText=channels.getValue();}
 private void rebuild(){ready=false;remember();clearWidgets();buttons.clear();var s=draft.settings();boolean compact=imageHeight<248;int h=compact?18:20;
  option(30,14,30,140,20,"Ramp",page!=0);option(31,164,30,142,20,"Redstone",page!=1);
  if(page==0){
   start=new NativeIntegerSlider(leftPos+14,topPos+(compact?54:56),292,h,-ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,s.startHalf(),n->Component.literal(offset("Start / off",n)),n->offsets(n,draft.settings().endHalf()));start.active=s.escalator()==0;addRenderableWidget(start);
   end=new NativeIntegerSlider(leftPos+14,topPos+(compact?76:80),292,h,-ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,s.endHalf(),n->Component.literal(offset(s.escalator()==0?"End / on":"Ramp height",n)),n->offsets(draft.settings().startHalf(),n));addRenderableWidget(end);
   String mode=new String[]{"ramp","filled ramp","lift","extend","escalator up","escalator down","Moat"}[draft.mode()];
   option(1,14,compact?98:104,142,h,"Mode: "+mode,true);
   var direction=net.minecraft.core.Direction.from2DDataValue(s.direction());option(24,164,compact?98:104,64,h,"Axis: "+(s.verticalLayout()?"Y":direction.getAxis().name()),true);option(6,232,compact?98:104,74,h,"Dir: "+direction.getName().toUpperCase(Locale.ROOT),!s.verticalLayout());
   option(4,14,compact?120:128,142,h,"Speed: "+new String[]{"fast","medium","slow"}[s.speed()],true);option(17,164,compact?120:128,142,h,"Travel: "+(s.travelAxis()==0?(s.verticalLayout()?"out / in":"up / down"):"left / right"),s.escalator()==0&&!s.moat());
   tread=new EditBox(font,leftPos+216,topPos+(compact?142:153),36,18,Component.literal("Tread pixels"));tread.setMaxLength(2);tread.setFilter(text->text.matches("[0-9]{0,2}"));tread.setValue(treadText);tread.setEditable(!s.elevator());tread.setResponder(ignored->{String value=tread.getValue();if(value.equals(treadText))return;treadText=value;apply();});addRenderableWidget(tread);
   option(14,260,compact?142:152,20,h,"-",!s.elevator());option(15,282,compact?142:152,20,h,"+",!s.elevator());
   option(19,14,compact?164:176,292,h,"Interpolation: "+new String[]{"Linear","Curve In","Curve Out"}[s.interpolation()],!s.elevator());
   option(18,14,compact?186:200,142,h,"Match textures: "+(s.matchTextures()?"On":"Off"),true);channels=null;
  }else{
   start=end=null;tread=null;channels=new EditBox(font,leftPos+14,topPos+68,292,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(text->text.matches("[0-9, ]*"));channels.setValue(channelText);channels.setTextColor(ChannelList.parse(channelText)==null?0xFF7777:0xE0E0E0);channels.setResponder(ignored->{String value=channels.getValue();if(value.equals(channelText))return;channelText=value;apply();});addRenderableWidget(channels);
   option(3,14,96,292,20,"Activation: "+(s.powerOn()?"Powered":"Unpowered"),true);option(22,14,120,292,20,"Deployment: "+(s.analogDeployment()?"Analog (0-15)":"Digital (on/off)"),s.escalator()==0);
  }
  option(7,164,compact?186:200,142,h,Component.translatable("gui.done").getString(),true);ready=true;
 }
 private void offsets(int a,int b){draft=draft.offsets(a,b,draft.settings().treadPixels(),draft.settings().channels());}
 private static int pixels(String text){try{return Integer.parseInt(text);}catch(NumberFormatException invalid){return Integer.MIN_VALUE;}}
 public boolean apply(){if(!ready)return false;remember();var bank=ChannelList.parse(channelText);if(channels!=null)channels.setTextColor(bank==null?0xFF7777:0xE0E0E0);int value=pixels(treadText);if(bank==null||!ControllerPlatform.validTreadPixels(value))return false;draft=draft.offsets(draft.settings().startHalf(),draft.settings().endHalf(),value,bank);lastStart=draft.settings().startHalf();lastEnd=draft.settings().endHalf();send.accept(new RampSettingsPayload(new NativeRampEdit(menu.tile.getBlockPos(),menu.containerId,++request,draft.settings())));return true;}
 public void action(int id,int direction){if(id==7){onClose();return;}if(id==30||id==31){remember();page=id-30;rebuild();return;}if(id==14||id==15){remember();int value=pixels(treadText);if(value<1||value>16)value=menu.tile.settings().treadPixels();String next=Integer.toString(ControllerPlatform.stepTreadPixels(value,id==15));if(tread!=null){boolean same=next.equals(tread.getValue());tread.setValue(next);if(same)apply();}else{treadText=next;apply();}return;}remember();draft=draft.action(id,direction);apply();rebuild();}
 public void acknowledge(RampSettingsPayload.Result result){var edit=result.authoritative();if(edit.window()!=menu.containerId||!edit.pos().equals(menu.tile.getBlockPos())||edit.request()!=request)return;pending=result;reconcile();}
 private void reconcile(){if(pending==null||dragging||minecraft.mouseHandler.isLeftPressed())return;var result=pending;pending=null;if(result.authoritative().request()!=request)return;status=result.status();if(!status.error())return;remember();draft=new NativeRampDraft(result.authoritative().settings(),menu.scanFacing,menu.horizontalFacing);treadText=Integer.toString(draft.settings().treadPixels());tread=null;lastStart=draft.settings().startHalf();lastEnd=draft.settings().endHalf();rebuild();}
 public void advanceEditor(){reconcile();var current=menu.status();if(!current.equals(observed)){observed=current;status=current;}}
 @Override protected void containerTick(){super.containerTick();advanceEditor();}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var entry:buttons.entrySet()){int id=entry.getKey();var widget=entry.getValue();if(widget.active&&widget.isMouseOver(x,y)&&(button==0||id==1||id==3||id==4||id==6||id==17||id==18||id==19||id==22||id==24)){action(id,button==1?-1:1);return true;}}boolean handled=super.mouseClicked(x,y,button);if(button==0&&getFocused() instanceof NativeIntegerSlider)dragging=handled;return handled;}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(button==0){dragging=false;if(draft.settings().startHalf()!=lastStart||draft.settings().endHalf()!=lastEnd)apply();reconcile();}return super.mouseReleased(x,y,button);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+28,0xFF304858);g.drawString(font,title,leftPos+14,topPos+10,0xFFFFFF,false);if(page==0)g.drawString(font,"Tread px",leftPos+164,topPos+(imageHeight<248?148:158),draft.settings().elevator()?0x78848C:0xDAE8F0,false);else g.drawString(font,"Receive channels",leftPos+14,topPos+54,0xDAE8F0,false);String text=(status.error()?"Error: ":"")+status.text();g.enableScissor(leftPos+14,topPos+imageHeight-24,leftPos+306,topPos+imageHeight-2);g.drawWordWrap(font,Component.literal(text),leftPos+14,topPos+imageHeight-24,292,status.error()?0xFF9988:0xE5C76B);g.disableScissor();}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public boolean isPauseScreen(){return false;}
}
