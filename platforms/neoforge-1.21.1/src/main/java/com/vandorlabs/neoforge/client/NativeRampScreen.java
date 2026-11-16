package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ui.RampDialog;
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
 @Override protected void init(){imageWidth=RampDialog.WIDTH;imageHeight=new RampDialog.Panel(height).height;super.init();rebuild();}
 private void option(int id,int x,int y,int width,int height,String text,boolean active){Button button;if(id==30||id==31)button=new NativeDialogTabButton(leftPos+x,topPos+y,width,height,Component.literal(text),ignored->action(id,1));else if(id==3)button=new Button(leftPos+x,topPos+y,width,height,Component.literal(text),ignored->action(id,1),java.util.function.Supplier::get){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int edge=active?(isHovered()?0xffa4d4e8:0xff607c8c):0xff3e505c;g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),active?(isHovered()?0xff354958:0xff24323e):0xff202a32);g.fill(getX(),getY(),getX()+getWidth(),getY()+1,edge);g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),edge);g.fill(getX(),getY(),getX()+1,getY()+getHeight(),edge);g.fill(getX()+getWidth()-1,getY(),getX()+getWidth(),getY()+getHeight(),edge);g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xffe0e8ec:0xff82939f);}};else button=new NativeDialogButton(leftPos+x,topPos+y,width,height,Component.literal(text),ignored->action(id,1));button.active=active;buttons.put(id,button);addRenderableWidget(button);}

 private static String offset(String title,int half){return RampDialog.offsetLabel(title,half);}
 private void remember(){if(tread!=null)treadText=tread.getValue();if(channels!=null)channelText=channels.getValue();}
 private void rebuild(){ready=false;remember();clearWidgets();buttons.clear();start=end=null;tread=channels=null;var state=draft.dialogState();var panel=new RampDialog.Panel(height);
  for(var control:RampDialog.controls(panel,state,page)){
   if(control.kind==RampDialog.Kind.BUTTON){option(control.id,control.x,control.y,control.width,control.height,control.label,control.enabled);continue;}
   if(control.kind==RampDialog.Kind.SLIDER){boolean first=control.id==20;String label=first?"Start / off":state.escalator==0?"End / on":"Ramp height";var slider=new NativeIntegerSlider(leftPos+control.x,topPos+control.y,control.width,control.height,control.minimum,control.maximum,control.value,n->Component.literal(offset(label,n)),n->{if(first)offsets(n,draft.settings().endHalf());else offsets(draft.settings().startHalf(),n);});slider.active=control.enabled;addRenderableWidget(slider);if(first)start=slider;else end=slider;continue;}
   if(control.id==16){tread=new EditBox(font,leftPos+control.x,topPos+control.y,control.width,control.height,Component.literal(control.label));tread.setMaxLength(2);tread.setFilter(text->text.matches("[0-9]{0,2}"));tread.setValue(treadText);tread.setEditable(control.enabled);tread.setResponder(ignored->{String value=tread.getValue();if(value.equals(treadText))return;treadText=value;apply();});addRenderableWidget(tread);}
   else if(control.id==8){channels=new EditBox(font,leftPos+control.x,topPos+control.y,control.width,control.height,Component.literal(control.label));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(text->text.matches("[0-9, ]*"));channels.setValue(channelText);channels.setTextColor(ChannelList.parse(channelText)==null?0xFF7777:0xE0E0E0);channels.setResponder(ignored->{String value=channels.getValue();if(value.equals(channelText))return;channelText=value;apply();});addRenderableWidget(channels);}
  }ready=true;
 }
 private void offsets(int a,int b){draft=draft.offsets(a,b,draft.settings().treadPixels(),draft.settings().channels());}
 private static int pixels(String text){try{return Integer.parseInt(text);}catch(NumberFormatException invalid){return Integer.MIN_VALUE;}}
 public boolean apply(){if(!ready)return false;remember();var bank=ChannelList.parse(channelText);if(channels!=null)channels.setTextColor(bank==null?0xFF7777:0xE0E0E0);int value=pixels(treadText);if(bank==null||!ControllerPlatform.validTreadPixels(value))return false;draft=draft.offsets(draft.settings().startHalf(),draft.settings().endHalf(),value,bank);lastStart=draft.settings().startHalf();lastEnd=draft.settings().endHalf();send.accept(new RampSettingsPayload(new NativeRampEdit(menu.tile.getBlockPos(),menu.containerId,++request,draft.settings())));return true;}
 public void action(int id,int direction){if(id==7){onClose();return;}if(id==30||id==31){remember();page=id-30;rebuild();return;}if(id==14||id==15){remember();int value=pixels(treadText);if(value<1||value>16)value=menu.tile.settings().treadPixels();String next=Integer.toString(ControllerPlatform.stepTreadPixels(value,id==15));if(tread!=null){boolean same=next.equals(tread.getValue());tread.setValue(next);if(same)apply();}else{treadText=next;apply();}return;}remember();draft=draft.action(id,direction);apply();rebuild();}
 public void acknowledge(RampSettingsPayload.Result result){var edit=result.authoritative();if(edit.window()!=menu.containerId||!edit.pos().equals(menu.tile.getBlockPos())||edit.request()!=request)return;pending=result;reconcile();}
 private void reconcile(){if(pending==null||dragging||minecraft.mouseHandler.isLeftPressed())return;var result=pending;pending=null;if(result.authoritative().request()!=request)return;status=result.status();if(!status.error())return;remember();draft=new NativeRampDraft(result.authoritative().settings(),menu.scanFacing,menu.horizontalFacing);treadText=Integer.toString(draft.settings().treadPixels());tread=null;lastStart=draft.settings().startHalf();lastEnd=draft.settings().endHalf();rebuild();}
 public void advanceEditor(){reconcile();var current=menu.status();if(!current.equals(observed)){observed=current;status=current;}}
 @Override protected void containerTick(){super.containerTick();advanceEditor();}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1)for(var entry:buttons.entrySet()){int id=entry.getKey();var widget=entry.getValue();if(widget.active&&widget.isMouseOver(x,y)&&(button==0||RampDialog.reverse(id))){action(id,button==1?-1:1);return true;}}boolean handled=super.mouseClicked(x,y,button);if(button==0&&getFocused() instanceof NativeIntegerSlider)dragging=handled;return handled;}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return NativeIntegerSlider.dragFocused(getFocused(),x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){NativeIntegerSlider.releaseFocused(getFocused(),x,y,button);if(button==0){dragging=false;if(draft.settings().startHalf()!=lastStart||draft.settings().endHalf()!=lastEnd)apply();reconcile();}return super.mouseReleased(x,y,button);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){NativeDialogVisuals.panel(g,font,title,leftPos,topPos,imageWidth,imageHeight,RampDialog.STYLE);if(page==0)g.drawString(font,RampDialog.TREAD_LABEL,leftPos+164,topPos+new RampDialog.Panel(height).treadLabelY(),draft.settings().elevator()?0x78848C:0xDAE8F0,false);else g.drawString(font,RampDialog.RECEIVE_LABEL,leftPos+14,topPos+54,0xDAE8F0,false);String text=(status.error()?"Error: ":"")+status.text();g.enableScissor(leftPos+14,topPos+imageHeight-24,leftPos+306,topPos+imageHeight-2);g.drawWordWrap(font,Component.literal(text),leftPos+14,topPos+imageHeight-24,292,status.error()?0xFF9988:0xE5C76B);g.disableScissor();}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public boolean isPauseScreen(){return false;}
}
