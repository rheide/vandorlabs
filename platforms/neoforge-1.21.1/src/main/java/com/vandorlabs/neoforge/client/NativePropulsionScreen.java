package com.vandorlabs.neoforge.client;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.propulsion.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
/** Dedicated Source engine dialog. Ordinary edits remain drafts; shape changes submit immediately. */
public final class NativePropulsionScreen extends AbstractContainerScreen<NativePropulsionMenu> {
 private final Consumer<NativePropulsionSettingsEdit> send;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
 private ProgrammableDialogLayout panel;private NativeMaterialPicker picker;private EditBox channels;private int page,particles,shape,side,threshold;private boolean join,inverted,analog,connected,shapeSubmitted,otherEdited;private final boolean programmable;private String draft;private ChannelList bank;
 public NativePropulsionScreen(NativePropulsionMenu menu,Inventory inventory,Component title){this(menu,inventory,title,e->PacketDistributor.sendToServer(new PropulsionSettingsPayload(e)));}
 public NativePropulsionScreen(NativePropulsionMenu menu,Inventory inventory,Component title,Consumer<NativePropulsionSettingsEdit> send){super(menu,inventory,title);this.send=Objects.requireNonNull(send);var data=menu.tile.settings().data();var definition=menu.tile.visual().definition();particles=data.particleLevel;side=data.sideTexture;threshold=data.particleThreshold;join=data.join;inverted=data.inverted;analog=data.analog;shape=definition.shape;connected=definition.maxConnected>0;programmable=!definition.family.isEmpty();bank=menu.tile.getRedstoneChannels();draft=bank.toString();}
 public ProgrammableDialogLayout panel(){return panel;}public Button button(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public NativeMaterialPicker activePicker(){return page==0?picker:null;}public int page(){return page;}
 public NativePropulsionConfiguration.Draft choices(){return new NativePropulsionConfiguration.Draft(bank,true,particles,connected,join,true,side,programmable,shape,inverted,analog,threshold);}
 public NativePropulsionSettingsEdit edit(){return new NativePropulsionSettingsEdit(menu.tile.getBlockPos(),menu.containerId,choices());}
 @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;rebuild();}
 private void option(int id,int x,int y,int width,String label,boolean active){var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(x,y,width,20).build();button.active=active;buttons.put(id,button);addRenderableWidget(button);}
 private void rebuild(){if(channels!=null)draft=channels.getValue();if(picker!=null)picker.closeInventory();clearWidgets();buttons.clear();int tw=(panel.width-24)/2;option(100,panel.left+12,panel.top+30,tw-2,"General",page!=0);option(101,panel.left+12+tw,panel.top+30,tw-2,"Redstone",page!=1);
  if(page==0){if(picker==null)picker=new NativeMaterialPicker(side,ModernFinishCatalog::validChoice,this::choose).inventoryChosen(value->{choose(value);submit();});picker.layout(panel.listX,panel.top+64,panel.listWidth,panel.rows(true),false);addRenderableWidget(picker.searchField());option(2,panel.controlsX,panel.top+64,154,"Particles: "+new String[]{"Off","Light","Medium","Heavy"}[particles],true);if(programmable)option(4,panel.controlsX,panel.top+88,154,"Shape: "+new String[]{"Block","Hexagon","Wedge"}[shape],true);if(connected)option(3,panel.controlsX,panel.top+(programmable?112:88),154,join?"Join: On":"Join: Off",true);}
  channels=new EditBox(font,panel.left+12,panel.top+68,panel.width-24,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setFilter(v->v.matches("[0-9, ]*"));channels.setValue(draft);channels.visible=channels.active=page==1;channels.setResponder(v->{draft=v;otherEdited=true;validate();});addRenderableWidget(channels);if(page==1){setFocused(channels);channels.setFocused(true);option(16,panel.left+12,panel.top+96,panel.width-24,"Activation: "+RedstoneActivation.label(inverted?2:1),true);option(5,panel.left+12,panel.top+120,panel.width-24,analog?"Brightness: Signal":"Brightness: On/Off",true);option(6,panel.left+12,panel.top+144,panel.width-24,"Particles at level: "+threshold,true);}option(1,panel.controlsX,panel.doneY,154,"Done",true);validate();}
 private boolean validate(){boolean valid=ChannelList.parse(channels==null?draft:channels.getValue())!=null;if(buttons.containsKey(1))buttons.get(1).active=valid;if(channels!=null)channels.setTextColor(valid?0xE0E0E0:0xFF7777);return valid;}
 private boolean submit(){var parsed=ChannelList.parse(channels==null?draft:channels.getValue());if(parsed==null){validate();return false;}bank=parsed;send.accept(edit());return true;}
 public void choose(int value){if(!ModernFinishCatalog.validChoice(value))throw new IllegalArgumentException("Invalid engine material");side=value;if(picker!=null)picker.select(value,false);otherEdited=true;}
 public void action(int id,int direction){if(id==100||id==101){page=id-100;rebuild();return;}if(id==1){if(!validate())return;if((!shapeSubmitted||otherEdited)&&!submit())return;if(minecraft.player!=null)onClose();else minecraft.setScreen(null);return;}if(id!=4)otherEdited=true;switch(id){case 2->particles=DialogInput.cycle(particles,4,direction);case 3->join=!join;case 4->{if(!programmable)return;shape=DialogInput.cycle(shape,3,direction);connected=shape!=2;if(submit())shapeSubmitted=true;}case 16->inverted=!inverted;case 5->analog=!analog;case 6->threshold=DialogInput.cycle(threshold,16,direction);default->{return;}}rebuild();}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var e:buttons.entrySet())if(e.getKey()!=1&&e.getKey()<100&&e.getValue().active&&e.getValue().isMouseOver(x,y)){action(e.getKey(),-1);return true;}return page==0&&picker!=null&&picker.click(x,y,button)||super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(page==0&&picker!=null&&picker.keyPressed(key,scan,modifiers))return true;if(page==1&&channels.isFocused()&&key!=256&&key!=257&&key!=335){channels.keyPressed(key,scan,modifiers);return true;}if(key==257||key==335){action(1,1);return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return page==0&&picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return page==0&&picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xEE18212C);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+26,0xFF304858);g.drawString(font,title,panel.left+12,panel.top+10,0xFFFFFFFF,false);if(page==0&&picker!=null)picker.render(g,x,y);if(page==1)g.drawString(font,"Receive channels",panel.left+12,panel.top+54,0xFFDAE8F0,false);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
