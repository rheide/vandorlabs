package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.neoforge.shape.NativeWallDialog;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Inactive ordinary/Large Door editor; activation requires complete placed-door and credential adapters. */
public final class NativeDoorScreen extends AbstractContainerScreen<NativeDoorMenu> {
    private final NativeCredentialFilter credentials;
    private final Map<Integer,NativeMaterialPicker> pickers=new HashMap<>();
    private final Map<Integer,Button> buttons=new HashMap<>();
    private final List<DialogControl> tabs=new ArrayList<>();
    private ProgrammableDialogLayout panel;
    private NativeMaterialPicker picker;
    private EditBox channels;
    private ChannelList lastValidChannels;
    private NativeDoorGlass glass;
    private FrameSettings frames,frameDraft;
    private int page,frameFace,design,detail,depth,trigger,panelMode,securityMode,allowed,faceTexture,layout;
    private boolean framed,hinges;
    private SpaceDoorMotion motion;
    public NativeDoorScreen(NativeDoorMenu menu,Inventory inventory,Component title,NativeCredentialFilter credentials){
        super(menu,inventory,title);this.credentials=Objects.requireNonNull(credentials);var initial=menu.tile.settings();var door=initial.door();
        design=door.design;detail=0;framed=door.framed;depth=initial.depth();trigger=door.trigger;panelMode=initial.panelMode();securityMode=initial.securityMode();allowed=initial.securityAllowed();faceTexture=initial.faceTexture();layout=initial.textureLayout();motion=SpaceDoorMotion.fromSettings(door.sliding,door.direction);hinges=door.hinges;glass=initial.glass();frames=initial.frames();lastValidChannels=menu.tile.getRedstoneChannels();
        if(faceTexture>=0&&ModernFinishCatalog.option(faceTexture).door()!=null)faceTexture=doorChoice(ModernFinishCatalog.option(faceTexture).door().design(),0);
    }
    private static int doorChoice(int design,int detail){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var metadata=ModernFinishCatalog.ENTRIES.get(i).door();if(metadata!=null&&metadata.design()==design&&metadata.detail()==Math.min(detail,1))return i;}return 0;}
    private static String securityLabel(int mode){return mode==0?"Security: Off":new String[]{"","Return After Open: Pulse","Stay Inserted","Stay Inserted: Pulse","Return After Open: Toggle"}[mode];}
    public int page(){return page;}public NativeDoorEdit edit(){return new NativeDoorEdit(design,detail,framed,motion.direction,depth,motion.sliding,hinges,trigger,panelMode>0,panelMode,securityMode,allowed,faceTexture,layout);}
    public EditBox channelField(){return channels;}public Button button(int id){return buttons.get(id);}
    private int selected(){return page==4?glass.material==DoorGlassData.ORIGINAL?-1:glass.material<0?PortholeDialog.shadeChoice(glass.shade):glass.material:page==1?frameFace==0?frameDraft.front:frameDraft.interior:faceTexture>=0?faceTexture:doorChoice(design,detail);}
    private NativeMaterialPicker createPicker(int key){
        if(key==4){var p=new NativeMaterialPicker(selected(),id->{var entry=ModernFinishCatalog.option(id);return entry.general()||entry.displaySelectable();},this::choose).sampling(false).originalArtwork("vandorlabs:glass-palette/clear");for(int shade=0;shade<GlassTextures.IDS.length;shade++)p.specialMaterial(PortholeDialog.shadeChoice(shade),GlassTextures.LABELS[shade],"Glass","vandorlabs:glass-palette/"+GlassTextures.IDS[shade]);return p;}
        if(key>=10)return new NativeMaterialPicker(selected(),key==10?NativeDoorSettings::frame:NativeDoorSettings::surface,this::choose).sampling(false).originalArtwork(key==10?"vandorlabs:frame-default/double_frame_metal":"vandorlabs:frame-default/metal_side");
        return new NativeMaterialPicker(selected(),id->{var entry=ModernFinishCatalog.option(id);return entry.general()||(entry.category().equals("Doors")||menu.tile.large()&&entry.category().equals("Double Doors"))&&(entry.door()==null||entry.door().detail()==detail);},this::choose).presentation(NativeDoorAssets::preview);
    }
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);imageWidth=panel.width;imageHeight=panel.height;leftPos=panel.left;topPos=panel.top;rebuild(true);}
    private void option(int id,int x,int y,int width,int height,String text,boolean active){var button=Button.builder(Component.literal(text),ignored->action(id,1)).bounds(x,y,width,height).build();button.active=active;buttons.put(id,button);addRenderableWidget(button);}
    private void option(int id,int relativeY,String text,boolean active){option(id,panel.controlsX,panel.top+relativeY,154,20,text,active);}
    private static final class Tab extends DialogControl {Tab(int id,int x,int y,int width,String text,boolean enabled){super(id,x,y,width,18,text,true,enabled);}}
    private void tab(int id,int x,int y,int width,String text,boolean enabled){tabs.add(new Tab(id,x,y,width,text,enabled));}
    private void rebuild(boolean center){
        String channelText=channels==null?lastValidChannels.toString():channels.getValue();clearWidgets();buttons.clear();tabs.clear();picker=null;
        if(page==4&&!SpaceDoorDialog.glassPage(design))page=0;var pages=SpaceDoorDialog.pages(design);int tabWidth=(panel.width-22)/pages.length;for(int i=0;i<pages.length;i++)tab(190+pages[i],panel.left+12+i*tabWidth,panel.top+30,tabWidth-2,SpaceDoorDialog.pageLabel(pages[i]),pages[i]!=page);
        if(page==0){option(10,54,motion.label,true);option(15,78,SpaceDoorDialog.hingesLabel(motion,hinges),SpaceDoorDialog.hingesEnabled(motion));option(13,102,SpaceDoorDialog.positionLabel(depth),true);option(18,126,"Texture: "+TextureLayoutMode.label(layout),true);}
        if(page==1){if(frameDraft==null)frameDraft=frames.editor(false,false,NativeWallDialog.defaultScreen());int span=(panel.width-24)/2;tab(3000,panel.left+12,panel.top+54,span-2,"Front",frameFace!=0);tab(3001,panel.left+12+span,panel.top+54,span-2,"Interior",frameFace!=1);option(3008,78,framed?"Framed":"Bare",true);}
        if(page==2){channels=new EditBox(font,panel.left+12,panel.top+68,panel.width-24,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(channelText);channels.setResponder(value->{validity();if(ChannelList.parse(value)!=null)send();});addRenderableWidget(channels);option(16,panel.left+12,panel.top+96,panel.width-24,20,"Activation: "+RedstoneActivation.label(trigger),true);}
        if(page==3){option(17,64,SpaceDoorDialog.panelLabel(panelMode),true);option(19,90,securityLabel(securityMode),true);}
        if(page==4){option(20,54,"Tint: "+GlassTextures.tintLabel(glass.tint),glass.material!=DoorGlassData.ORIGINAL);if(glass.material>=0)option(21,78,"Texture: "+TextureLayoutMode.label(glass.layout),true);option(22,102,"Original glass",glass.material!=DoorGlassData.ORIGINAL);}
        int doneHeight=panel.height<246?16:20,doneY=panel.height<246?panel.top+panel.height-18:panel.doneY;option(1,panel.controlsX,doneY,154,doneHeight,Component.translatable("gui.done").getString(),true);validity();
        if(page==0||page==1||page==4){int key=page==1?10+frameFace:page;picker=pickers.computeIfAbsent(key,this::createPicker);picker.select(selected(),false);picker.layout(panel.listX,panel.top+(page==1?78:54),panel.listWidth,Math.max(2,(panel.height-(page==1?88:64))/ProgrammableDialogLayout.ROW_HEIGHT),center);addRenderableWidget(picker.searchField());}
    }
    private boolean validChannels(){return channels==null||ChannelList.parse(channels.getValue())!=null;}
    private void validity(){var done=buttons.get(1);if(done!=null)done.active=validChannels();if(channels!=null)channels.setTextColor(validChannels()?0xE0E0E0:0xFF7777);}
    private void send(){if(channels!=null){var parsed=ChannelList.parse(channels.getValue());if(parsed!=null)lastValidChannels=parsed;}PacketDistributor.sendToServer(new DoorSettingsPayload(menu.tile.getBlockPos(),menu.containerId,edit(),lastValidChannels));}
    private void sendFrame(){frames=frameDraft;PacketDistributor.sendToServer(new DoorFramePayload(menu.tile.getBlockPos(),menu.containerId,frames));}
    private void sendGlass(){PacketDistributor.sendToServer(new DoorGlassPayload(menu.tile.getBlockPos(),menu.containerId,glass));}
    public void action(int id,int direction){
        if(picker!=null)picker.closeInventory();if(id==1){if(validChannels()){send();onClose();}return;}if(id>=190&&id<=194){page=id-190;rebuild(true);return;}if(id==3000||id==3001){frameFace=id-3000;rebuild(true);return;}
        switch(id){case 10->motion=SpaceDoorDialog.nextMotion(motion,menu.tile.large(),direction);case 12,3008->framed=!framed;case 13->{depth=DialogInput.cycle(depth,3,direction);panelMode=SpaceDoorDialog.panelAfterPosition(panelMode,depth);}case 15->{if(!motion.sliding)hinges=!hinges;}case 16->trigger=DialogInput.cycle(trigger,3,direction);case 17->panelMode=SpaceDoorDialog.nextPanel(panelMode,depth,direction);case 18->layout=DialogInput.cycle(layout,3,direction);case 19->securityMode=SpaceDoorDialog.nextSecurity(securityMode,direction);case 20->{glass=new NativeDoorGlass(glass.material,glass.shade,DialogInput.cycle(glass.tint,GlassTextures.TINT_COUNT,direction),glass.layout);sendGlass();rebuild(false);return;}case 21->{glass=new NativeDoorGlass(glass.material,glass.shade,glass.tint,DialogInput.cycle(glass.layout,3,direction));sendGlass();rebuild(false);return;}case 22->{glass=NativeDoorGlass.DEFAULT;sendGlass();rebuild(false);return;}default->{return;}}
        send();rebuild(false);
    }
    public void choose(int choice){
        if(page==4){int shade=PortholeDialog.choiceShade(choice);glass=new NativeDoorGlass(choice==-1?DoorGlassData.ORIGINAL:shade>=0?-1:choice,shade>=0?shade:glass.shade,glass.tint,glass.layout);faceTexture=-1;send();sendGlass();}
        else if(page==1){frameDraft=new FrameSettings(frameFace==0?choice:frameDraft.front,frameFace==1?choice:frameDraft.interior,frameDraft.exterior,frameDraft.artworkVersion,frameDraft.splitTop,frameDraft.splitBottom,frameDraft.tile,frameDraft.displayFit,frameDraft.split,frameDraft.bare);sendFrame();}
        else{var artwork=ModernFinishCatalog.option(choice).door();if(artwork!=null){design=artwork.design();detail=artwork.detail();faceTexture=-1;}else faceTexture=choice;send();}rebuild(false);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0||button==1){for(var tab:tabs)if(tab.enabled&&x>=tab.x&&x<tab.x+tab.width&&y>=tab.y&&y<tab.y+tab.height){action(tab.id,1);return true;}if(button==1)for(var entry:buttons.entrySet())if(entry.getKey()!=1&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}}
        if(page==3&&credentials.click(x,y,button,panel.controlsX,panel.top+136,allowed,value->{allowed=value;send();}))return true;return picker!=null&&picker.click(x,y,button)||super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(picker!=null&&picker.keyPressed(key,scan,modifiers))return true;if(page==2&&channels!=null&&channels.isFocused()&&key!=256&&key!=257&&key!=335){channels.keyPressed(key,scan,modifiers);return true;}if(key==257||key==335){action(1,1);return true;}if(page==0&&(key==264||key==265)){int next=design+(key==264?1:-1);if(next>=0&&next<30&&(menu.tile.large()||next<21||next==29)){design=next;faceTexture=-1;send();rebuild(true);}return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);g.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF202028);g.drawString(font,title,panel.left+8,panel.top+8,0xFFFFFFFF,false);for(var tab:tabs){g.fill(tab.x,tab.y,tab.x+tab.width,tab.y+tab.height,tab.enabled?0xFF203540:0xFF365568);g.drawCenteredString(font,tab.label,tab.x+tab.width/2,tab.y+5,0xFFE0F0FF);}if(picker!=null)picker.render(g,x,y);if(page==3)credentials.render(g,font,panel.controlsX,panel.top+136,allowed);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
