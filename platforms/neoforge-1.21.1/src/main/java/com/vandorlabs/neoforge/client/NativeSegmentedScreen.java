package com.vandorlabs.neoforge.client;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.door.NativeDoorGlass;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
/** Dedicated Segmented Door dialog using the shared picker and released per-section operation. */
public final class NativeSegmentedScreen extends AbstractContainerScreen<NativeSegmentedMenu> {
 private final Consumer<NativeSegmentedEdit> send;
 private final Map<Integer,NativeMaterialPicker> pickers=new HashMap<>();
 private final Map<Integer,Button> buttons=new HashMap<>();
 private final List<DialogControl> tabs=new ArrayList<>();
 private ProgrammableDialogLayout panel;private NativeMaterialPicker picker;private EditBox channels;
 private NativeSegmentedDoorSettings settings;private NativeSegmentedEdit last;
 private int page=2,ticks,texture,cellTexture;private boolean thisSegment=true;private boolean local,opens;private String draft,error="";
 public NativeSegmentedScreen(NativeSegmentedMenu menu,Inventory inventory,Component title){this(menu,inventory,title,edit->PacketDistributor.sendToServer(new SegmentedSettingsPayload(edit)));}
 /** The transaction sink also supports isolated widget checks without opening a world. */
 public NativeSegmentedScreen(NativeSegmentedMenu menu,Inventory inventory,Component title,Consumer<NativeSegmentedEdit> send){super(menu,inventory,title);this.send=Objects.requireNonNull(send);var tile=menu.tile;settings=tile.settings();ticks=tile.ticksPerSection();texture=tile.texture();cellTexture=tile.effectiveTexture();local=tile.localRedstone();opens=tile.signalOpens();draft=tile.getRedstoneChannels().toString();}
 public ProgrammableDialogLayout panel(){return panel;}public NativeMaterialPicker activePicker(){return picker;}public EditBox channelField(){return channels;}public Button button(int id){return buttons.get(id);}public int page(){return page;}public NativeSegmentedDoorSettings settings(){return settings;}public String error(){return error;}
 public NativeSegmentedEdit edit(){var parsed=ChannelList.parse(channels==null?draft:channels.getValue());return parsed==null?null:new NativeSegmentedEdit(menu.containerId,5,ticks,local,opens,parsed,texture,settings);}
 private int selectedArtwork(){return thisSegment?cellTexture:texture;}
 private boolean glassAvailable(){int choice=selectedArtwork();String category=choice<0?"":ModernFinishCatalog.option(choice).category();return settings.window||category.equals("Frame")||category.equals("Frames")||category.startsWith("Frame/")||category.startsWith("Frames/");}
 private int selected(){return page==3?settings.glass.material<0?PortholeDialog.shadeChoice(settings.glass.shade):settings.glass.material:selectedArtwork();}
 private NativeMaterialPicker createPicker(int page){
  if(page==2)return new NativeMaterialPicker(selectedArtwork(),NativeSegmentedDoorEntity::validTexture,this::choose).specialMaterial(-1,"Original Segmented Panel","Industrial","vandorlabs:segmented-artwork/panel");
  var result=new NativeMaterialPicker(selected(),id->{var e=ModernFinishCatalog.option(id);return e.general()||e.displaySelectable();},this::choose).sampling(false);
  for(int shade=0;shade<GlassTextures.IDS.length;shade++)result.specialMaterial(PortholeDialog.shadeChoice(shade),GlassTextures.LABELS[shade],"Glass","vandorlabs:glass-palette/"+GlassTextures.IDS[shade]);return result;
 }
 @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;rebuild(true);}
 private static final class Tab extends DialogControl {Tab(int id,int x,int y,int width,String label,boolean enabled){super(id,x,y,width,18,label,true,enabled);}}
 private void option(int id,int y,String label){int height=20;if(page==2&&id!=0){int gap=Math.min(24,(panel.height-88)/10);y=54+(y-54)*gap/24;height=Math.max(8,Math.min(20,gap-2));}int x=page==2||page==3?panel.controlsX:panel.left+12;var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(x,panel.top+y,154,height).build();buttons.put(id,button);addRenderableWidget(button);}
 private void rebuild(boolean center){
  if(channels!=null)draft=channels.getValue();clearWidgets();buttons.clear();tabs.clear();picker=null;if(page==3&&!glassAvailable())page=2;
  int[] pages=glassAvailable()?new int[]{2,3,1}:new int[]{2,1};int tabWidth=(panel.width-22)/pages.length;for(int i=0;i<pages.length;i++){int p=pages[i];tabs.add(new Tab(90+p,panel.left+12+i*tabWidth,panel.top+30,tabWidth-2,p==2?"Door":p==3?"Glass":"Redstone",p!=page));}
  channels=new EditBox(font,panel.left+12,panel.top+68,panel.width-24,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft);channels.visible=channels.active=page==1;channels.setResponder(value->{draft=value;apply();});addRenderableWidget(channels);
  if(page==1){option(2,96,"Activation: "+RedstoneActivation.label(Math.max(0,settings.activation)));option(1,120,"Local Redstone: "+(local?"On":"Off"));}
  if(page==2){option(15,246,"Artwork: "+(thisSegment?"This segment":"All segments"));option(16,270,"Use shared artwork");buttons.get(16).active=thisSegment;option(6,54,"Slide: "+new String[]{"Up","Down","Left","Right"}[settings.direction]);option(7,78,"Angle: "+(settings.straight?"Straight":"Perpendicular"));option(5,102,"Speed: "+(ticks<=5?"Fast":ticks<=10?"Medium":"Slow"));option(14,126,"Pos: "+new String[]{"Middle","Near","Far"}[settings.position]);option(8,150,"Texture: "+TextureLayoutMode.label(settings.layout));option(9,174,"Segment: "+(settings.window?"Window":"Solid"));if(settings.window){option(10,198,"Shape: "+new String[]{"Hexagon","Octagon","Square","Round"}[settings.shape]);option(11,222,"Window: "+(settings.join?"Fit":"Tile"));}}
  if(page==3){option(12,54,"Tint: "+GlassTextures.tintLabel(settings.glass.tint));if(settings.glass.material>=0)option(13,78,"Texture: "+TextureLayoutMode.label(settings.glass.layout));}
  option(0,panel.height-26,Component.translatable("gui.done").getString());validate();
  if(page==2||page==3){picker=pickers.computeIfAbsent(page,this::createPicker);picker.select(selected(),false);picker.layout(panel.listX,panel.top+54,panel.listWidth,Math.max(2,(panel.height-64)/ProgrammableDialogLayout.ROW_HEIGHT),center);addRenderableWidget(picker.searchField());}
 }
 private boolean validate(){boolean valid=ChannelList.parse(channels==null?draft:channels.getValue())!=null;var done=buttons.get(0);if(done!=null)done.active=valid;if(channels!=null)channels.setTextColor(valid?0xE0E0E0:0xFF7777);return valid;}
 private boolean apply(){var edit=edit();if(edit==null){error="Invalid channels";validate();return false;}if(!edit.equals(last)){send.accept(edit);last=edit;}error="";validate();return true;}
 public void action(int id,int direction){
  if(picker!=null)picker.closeInventory();if(id>=90&&id<=93){int next=id-90;if(next==3&&!glassAvailable())return;if(next!=1&&next!=2&&next!=3)return;page=next;if(page==3&&!settings.window)settings=new NativeSegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,true,2,false,settings.glass,settings.position);if(page==3)apply();rebuild(true);return;}
  if(id==15){thisSegment=!thisSegment;rebuild(false);return;}if(id==16){if(artwork(-1,true))rebuild(false);return;}
  if(id==0){if(apply()){if(minecraft.player!=null)onClose();else minecraft.setScreen(null);}return;}
  int d=settings.direction,layout=settings.layout,a=settings.activation,shape=settings.shape,p=settings.position;boolean straight=settings.straight,window=settings.window,join=settings.join;NativeDoorGlass glass=settings.glass;
  switch(id){case 1->local=!local;case 2->a=DialogInput.cycle(Math.max(0,a),3,direction);case 5->{int speed=ticks<=5?2:ticks<=10?1:0;ticks=new int[]{20,10,5}[DialogInput.cycle(speed,3,direction)];}case 6->d=DialogInput.cycle(d,4,direction);case 7->straight=!straight;case 8->layout=DialogInput.cycle(layout,3,direction);case 9->window=!window;case 10->shape=DialogInput.cycle(shape,4,direction);case 11->join=!join;case 12->glass=new NativeDoorGlass(glass.material,glass.shade,DialogInput.cycle(glass.tint,GlassTextures.TINT_COUNT,direction),glass.layout);case 13->glass=new NativeDoorGlass(glass.material,glass.shade,glass.tint,DialogInput.cycle(glass.layout,3,direction));case 14->p=DialogInput.cycle(p,3,direction);default->{return;}}
  settings=new NativeSegmentedDoorSettings(d,straight,layout,a,window,shape,join,glass,p);apply();rebuild(false);
 }
 private boolean artwork(int choice,boolean inherit){var parsed=ChannelList.parse(channels==null?draft:channels.getValue());if(parsed==null){error="Invalid channels";validate();return false;}send.accept(new NativeSegmentedEdit(menu.containerId,inherit?7:thisSegment?6:3,ticks,local,opens,parsed,choice,null));if(thisSegment||inherit)cellTexture=inherit?texture:choice;else texture=cellTexture=choice;return true;}
 public void choose(int choice){
  if(page==3){int shade=PortholeDialog.choiceShade(choice);if(choice<0&&shade<0)throw new IllegalArgumentException("Segmented glass has no Original choice");var g=settings.glass;settings=new NativeSegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,settings.window,settings.shape,settings.join,new NativeDoorGlass(shade>=0?-1:choice,shade>=0?shade:g.shade,g.tint,g.layout),settings.position);}
  else{if(!NativeSegmentedDoorEntity.validTexture(choice))throw new IllegalArgumentException("Invalid Segmented Door texture");if(!artwork(choice,false))return;if(glassAvailable()&&!settings.window)settings=new NativeSegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,true,2,false,settings.glass,settings.position);}
  apply();rebuild(false);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0||button==1){for(var tab:tabs)if(tab.enabled&&x>=tab.x&&x<tab.x+tab.width&&y>=tab.y&&y<tab.y+tab.height){action(tab.id,1);return true;}if(button==1)for(var entry:buttons.entrySet())if(entry.getKey()!=0&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}}return picker!=null&&picker.click(x,y,button)||super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(picker!=null&&picker.keyPressed(key,scan,modifiers))return true;if(page==1&&channels.isFocused()&&key!=256&&key!=257&&key!=335){channels.keyPressed(key,scan,modifiers);return true;}if(key==257||key==335){action(0,1);return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xEE18212C);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+26,0xFF304858);g.drawString(font,title,panel.left+12,panel.top+10,0xFFFFFFFF,false);for(var tab:tabs){g.fill(tab.x,tab.y,tab.x+tab.width,tab.y+tab.height,tab.enabled?0xFF203540:0xFF365568);g.drawCenteredString(font,tab.label,tab.x+tab.width/2,tab.y+5,0xFFE0F0FF);}if(picker!=null)picker.render(g,x,y);if(page==1){g.drawString(font,"Channels",panel.left+12,panel.top+56,0xFFCBD6E2,false);g.drawWordWrap(font,Component.literal("Configure any section with Shift-right-click or the Configurizer. Redstone activation disables manual opening and closing."),panel.left+12,panel.top+152,Math.max(80,panel.listWidth),0xFF9FB1C5);}if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,panel.width-24),panel.left+12,panel.top+panel.height-90,0xFFFF7777,false);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public boolean isPauseScreen(){return false;}
}
