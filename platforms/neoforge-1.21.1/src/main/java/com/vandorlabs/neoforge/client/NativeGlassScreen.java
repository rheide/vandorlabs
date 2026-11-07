package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated four-page Source glass editor with shared picker, frame draft and reverse-option behavior. */
public final class NativeGlassScreen extends AbstractContainerScreen<NativeGlassMenu> {
 private static final int SHADE_BASE=0x10000000;
 private final Consumer<GlassSettingsPayload> send;private final Consumer<GlassFramePayload> sendFrames;
 private final Map<Integer,NativeMaterialPicker> pickers=new HashMap<>();private final Map<Integer,Button> buttons=new LinkedHashMap<>();
 private GlassDialog.State state;private FrameSettings frames,draft;private PanelPlayback playback;private NativeMaterialPicker picker;private ProgrammableDialogLayout panel;
 public NativeGlassScreen(NativeGlassMenu menu,Inventory inventory,Component title){this(menu,inventory,title,PacketDistributor::sendToServer,PacketDistributor::sendToServer);}
 public NativeGlassScreen(NativeGlassMenu menu,Inventory inventory,Component title,Consumer<GlassSettingsPayload> send,Consumer<GlassFramePayload> sendFrames){super(menu,inventory,title);this.send=Objects.requireNonNull(send);this.sendFrames=Objects.requireNonNull(sendFrames);var old=menu.tile.settings();var g=old.glass();state=new GlassDialog.State(g.shade,g.tint,g.material,g.textureLayout(),g.join,g.framed,g.stretch);frames=old.frames();playback=old.playback();}
 public ProgrammableDialogLayout panel(){return panel;}public NativeMaterialPicker activePicker(){return picker;}public GlassDialog.State state(){return state;}public FrameSettings frames(){return frames;}public PanelPlayback playback(){return playback;}public Button button(int id){return buttons.get(id);}
 @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;rebuild(true);}
 private void option(DialogControl c){option(c.id,c.x,c.y,c.width,c.height,c.label,c.enabled);}
 private void option(int id,int x,int y,int width,int height,String text,boolean enabled){var b=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(x,y,width,height).build();b.active=enabled;buttons.put(id,b);addRenderableWidget(b);}
 private int selected(){return state.page==0?state.material<0?PortholeDialog.shadeChoice(state.shade):state.material:state.page==1?draft.front:state.page==2?draft.interior:draft.exterior;}
 private NativeMaterialPicker createPicker(int page){
  if(page>0)return new NativeMaterialPicker(selected(),page==1?choice->NativeWallEntity.validFrame(choice)&&NativeWallDialog.surfaceOption(choice):NativeWallDialog::surfaceOption,this::choose).originalArtwork(page==1?"vandorlabs:frame-default/double_frame_metal":"vandorlabs:frame-default/metal_side").sampling(false);
  var result=new NativeMaterialPicker(selected(),choice->{var e=ModernFinishCatalog.option(choice);return e.general()||e.displaySelectable();},this::choose).presentation(NativeDoorAssets::preview).sampling(false);
  for(int shade=0;shade<GlassTextures.IDS.length;shade++)result.specialMaterial(PortholeDialog.shadeChoice(shade),GlassTextures.LABELS[shade],"Glass","vandorlabs:glass-palette/"+GlassTextures.IDS[shade]);return result;
 }
 private void rebuild(boolean center){if(picker!=null)picker.closeInventory();clearWidgets();buttons.clear();for(var c:GlassDialog.tabs(panel,state))option(c);
  if(!state.framePage()){for(var c:GlassDialog.body(panel,state))if(c.visible)option(c);if(state.material>=0&&ModernFinishCatalog.option(state.material).playback()!=null){int width=(ProgrammableDialogLayout.CONTROLS_WIDTH+2)/3;String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};for(int i=0;i<3;i++){option(700+i,panel.controlsX+i*width,panel.top+130,width-2,18,Component.translatable("gui.vandorlabs.selector."+modes[i]).getString(),playback.mode()!=i);option(710+i,panel.controlsX+i*width,panel.top+154,width-2,18,Component.translatable("gui.vandorlabs.selector."+speeds[i]).getString(),playback.speed()!=i);}}}
  else{option(3003,panel.controlsX,panel.top+54,154,20,"Texture: "+(draft.tile?"Tile":"Fit"),true);option(3008,panel.controlsX,panel.top+78,154,20,state.framed?"Framed":"Bare",true);}
  option(GlassDialog.done(panel,Component.translatable("gui.done").getString()));picker=pickers.computeIfAbsent(state.page,this::createPicker);picker.select(selected(),false);picker.layout(panel.listX,GlassDialog.listY(panel),GlassDialog.listWidth(panel),state.framePage()?Math.max(2,(panel.height-88)/ProgrammableDialogLayout.ROW_HEIGHT):GlassDialog.listRows(panel),center);addRenderableWidget(picker.searchField());
 }
 private void primary(){send.accept(new GlassSettingsPayload(menu.tile.getBlockPos(),menu.containerId,state.size,state.shade,state.tint,state.material,state.layout,state.join,state.framed,playback.mode(),playback.speed()));}
 private void frame(){frames=draft;sendFrames.accept(new GlassFramePayload(menu.tile.getBlockPos(),menu.containerId,frames));}
 public void choose(int choice){if(!state.framePage()){int shade=PortholeDialog.choiceShade(choice);if(shade<0&&!ModernFinishCatalog.validChoice(choice))throw new IllegalArgumentException("Invalid Glass choice");state=state.choose(shade<0?choice:SHADE_BASE+shade,SHADE_BASE);primary();}else{if(choice!=-1&&!(state.page==1?NativeWallEntity.validFrame(choice):NativeWallDialog.surfaceOption(choice)))throw new IllegalArgumentException("Invalid Glass frame choice");draft=new FrameSettings(state.page==1?choice:draft.front,state.page==2?choice:draft.interior,state.page==3?choice:draft.exterior,draft.artworkVersion,draft.splitTop,draft.splitBottom,draft.tile,draft.displayFit,draft.split,draft.bare);frame();}rebuild(false);}
 public void action(int id,int direction){picker.closeInventory();if(GlassDialog.isPage(id)){if(id!=GlassDialog.FIRST_PAGE&&draft==null)draft=frames.editor(false,false,NativeWallDialog.defaultScreen());state=state.action(id,direction);rebuild(true);return;}
  if(id==GlassDialog.DONE){onClose();return;}if(id==3003){draft=new FrameSettings(draft.front,draft.interior,draft.exterior,draft.artworkVersion,draft.splitTop,draft.splitBottom,!draft.tile,draft.displayFit,draft.split,draft.bare);frame();}else if(id==3008){state=state.withFramed(!state.framed);primary();}else if(id>=700&&id<=702){playback=new PanelPlayback(id-700,playback.speed());primary();}else if(id>=710&&id<=712){playback=new PanelPlayback(playback.mode(),id-710);primary();}else{state=state.action(id,direction);primary();}rebuild(false);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(var e:buttons.entrySet())if((e.getKey()==3003||e.getKey()==3008||e.getKey()==GlassDialog.JOIN||e.getKey()==GlassDialog.TINT||e.getKey()==GlassDialog.TEXTURE_LAYOUT)&&e.getValue().active&&e.getValue().isMouseOver(x,y)){action(e.getKey(),-1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){return picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xEE18212C);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+26,0xFF304858);g.drawString(font,GlassDialog.TITLE,panel.left+12,panel.top+10,0xFFFFFFFF,false);picker.render(g,x,y);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}@Override public boolean isPauseScreen(){return false;}
}
