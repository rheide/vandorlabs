package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.*;
import java.io.IOException;

/** Shared material lists, activation selector and fixed-size mechanism tabs. */
public final class GuiSegmentedDoor extends GuiMaterialPicker {
    private final SegmentedDoorTile tile;
    private int page=2,ticks,texture,cellTexture;private boolean thisSegment=true;private HousingTextureList textures,glassTextures;private boolean local,opens;
    private GuiTextField channels;private String draft,error="",last="";private SegmentedDoorSettings settings,lastSettings;private ProgrammableDialogLayout layout;
    public GuiSegmentedDoor(SegmentedDoorContainer container){super(container);tile=container.tile;texture=tile.texture;cellTexture=tile.effectiveTexture();ticks=tile.ticksPerSection;local=tile.localRedstone;opens=tile.signalOpens;settings=tile.settings;draft=tile.getRedstoneChannels().toString();xSize=460;ySize=332;}
    @Override protected HousingTextureList materialPicker(){return page==2?textures:page==3?glassTextures:null;}
    @Override public void initGui(){
        if(channels!=null)draft=channels.getText();layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();if(page==0)page=2;if(page==3&&!glassAvailable())page=2;
        int[] pages=glassAvailable()?new int[]{2,3,1}:new int[]{2,1};String[] labels={"Door","Redstone","Door","Glass"};for(int i=0;i<pages.length;i++){GuiButton b=layout.fullTab(90+pages[i],i,pages.length,labels[pages[i]]);b.enabled=page!=pages[i];buttonList.add(b);}
        channels=new GuiTextField(6,fontRenderer,guiLeft+12,guiTop+68,xSize-24,18);channels.setMaxStringLength(com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH);channels.setText(draft);channels.setVisible(page==1);
        if(page==1){buttonList.add(new RedstoneActivationButton(2,guiLeft+12,guiTop+96,154,Math.max(0,settings.activation)));buttonList.add(new GuiButton(1,guiLeft+12,guiTop+120,154,20,"Local Redstone: "+(local?"On":"Off")));}
        if(page==2){java.util.List<HousingTextureList.Option> options=new java.util.ArrayList<>();options.add(new HousingTextureList.Option(-1,"Dark Segmented Panel",SegmentedArtworkPolicy.CATEGORY,"vandorlabs:blocks/segmented/segmented_panel"));for(int i=0;i<ScreenHousingTextures.IDS.length;i++){int choice=ScreenHousingTextures.choiceAt(i);if(SegmentedDoorTile.validTexture(choice))options.add(new HousingTextureList.Option(choice,HousingTextureList.name(choice),ScreenHousingTextures.menuCategory(choice),ScreenHousingTextures.fullTexture(choice)));}
            textures=new HousingTextureList(guiLeft+12,guiTop+54,xSize-190,selectedArtwork(),8,options).visibleRows(Math.max(2,(ySize-64)/22)).keepView(textures).custom(value->{selectedTexture(value);});
            buttonList.add(control(6,54,"Slide: "+new String[]{"Up","Down","Left","Right"}[settings.direction]));buttonList.add(control(7,78,"Angle: "+(settings.straight?"Straight":"Perpendicular")));buttonList.add(control(5,102,"Speed: "+(ticks<=5?"Fast":ticks<=10?"Medium":"Slow")));buttonList.add(control(14,126,"Pos: "+new String[]{"Middle","Near","Far"}[settings.position]));
            if(!gateSelected()){buttonList.add(control(8,150,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(settings.layout)));buttonList.add(control(9,174,"Segment: "+(settings.window?"Window":"Solid")));}
            buttonList.add(control(15,246,"Artwork: "+(thisSegment?"This segment":"All segments")));GuiButton inherit=control(16,270,"Use shared artwork");inherit.enabled=thisSegment;buttonList.add(inherit);
            if(!gateSelected()&&settings.window){buttonList.add(control(10,198,"Shape: "+new String[]{"Hexagon","Octagon","Square","Round"}[settings.shape]));buttonList.add(control(11,222,"Window: "+(settings.join?"Fit":"Tile")));}
        }
        if(page==3){glassTextures=HousingTextureList.forGlassMaterials(0,guiLeft+12,guiTop+54,xSize-190,settings.glass.shade,settings.glass.material).visibleRows(Math.max(2,(ySize-64)/22)).keepView(glassTextures).custom(this::glass);buttonList.add(control(12,54,"Tint: "+GlassTextures.tintLabel(settings.glass.tint)));if(settings.glass.material>=0)buttonList.add(control(13,78,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(settings.glass.layout)));}
        buttonList.add(control(0,ySize-26,"Done"));
    }
    private GuiButton control(int id,int y,String label){int height=20;if(page==2&&id!=0){int gap=Math.min(24,(ySize-88)/10);y=54+(y-54)*gap/24;height=Math.min(20,gap-2);}return new GuiButton(id,guiLeft+(page==2||page==3?xSize-166:12),guiTop+y,154,height,label);}
    private int selectedArtwork(){return thisSegment?cellTexture:texture;}
    private boolean gateSelected(){return SegmentedArtwork.gate(cellTexture)>=0;}
    private boolean glassAvailable(){if(gateSelected())return false;int choice=selectedArtwork();String category=choice<0?"":ScreenHousingTextures.category(choice);return settings.window||"Frame".equals(category)||"Frames".equals(category);}
    private void selectedTexture(int choice){if(!artwork(choice,false))return;if(glassAvailable()&&!settings.window)settings=new SegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,true,2,false,settings.glass,settings.position);apply();initGui();}
    private boolean artwork(int choice,boolean inherit){ChannelList selected=ChannelList.parse(channels==null?draft:channels.getText());if(selected==null){error="Invalid channels";return false;}SegmentedDoorMessage message=new SegmentedDoorMessage(inventorySlots.windowId,0,ticks,local,opens,selected);if(thisSegment||inherit){message.cellTexture(choice,inherit);cellTexture=inherit?texture:choice;}else{message.texture(choice);texture=cellTexture=choice;}PacketHandler.INSTANCE.sendToServer(message);return true;}
    private void update(int direction,boolean straight,int mode,int activation,boolean window,int shape,boolean join,DoorGlassAppearance glass){settings=new SegmentedDoorSettings(direction,straight,mode,activation,window,shape,join,glass,settings.position);apply();initGui();}
    private void apply(){try{ChannelList selected=ChannelList.parse(channels==null?draft:channels.getText());if(selected==null)throw new IllegalArgumentException("Invalid channels");String key=ticks+":"+local+":"+opens+":"+texture+":"+selected;if(!key.equals(last)||!settings.equals(lastSettings)){PacketHandler.INSTANCE.sendToServer(new SegmentedDoorMessage(inventorySlots.windowId,0,ticks,local,opens,selected).partSettings(texture,settings));last=key;lastSettings=settings;}error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}}
    private void glass(int choice){int shade=settings.glass.shade,material=choice;if(choice>=HousingTextureList.CANOPY_GLASS_BASE&&choice<HousingTextureList.CANOPY_GLASS_BASE+GlassTextures.IDS.length){shade=choice-HousingTextureList.CANOPY_GLASS_BASE;material=-1;}update(settings.direction,settings.straight,settings.layout,settings.activation,settings.window,settings.shape,settings.join,new DoorGlassAppearance(material,shade,settings.glass.tint,settings.glass.layout));}
    @Override protected void actionPerformed(GuiButton b){
        if(gateSelected()&&b.id>=8&&b.id<=11)return;
        if(b.id>=90){page=b.id-90;if(page==3&&!settings.window&&glassAvailable()){settings=new SegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,true,2,false,settings.glass,settings.position);apply();}initGui();return;}
        if(b.id==15){thisSegment=!thisSegment;initGui();return;}if(b.id==16){if(artwork(-1,true))initGui();return;}
        if(b.id==0){apply();if(error.isEmpty())mc.player.closeScreen();return;}
        if(b.id==1){local=!local;apply();initGui();return;}if(b.id==5){int speed=ticks<=5?2:ticks<=10?1:0;speed=GuiOptionCycle.next(speed,3);ticks=new int[]{20,10,5}[speed];apply();initGui();return;}
        if(b.id==14){settings=new SegmentedDoorSettings(settings.direction,settings.straight,settings.layout,settings.activation,settings.window,settings.shape,settings.join,settings.glass,GuiOptionCycle.next(settings.position,3));apply();initGui();return;}
        int direction=settings.direction,mode=settings.layout,activation=settings.activation,shape=settings.shape;boolean straight=settings.straight,window=settings.window,join=settings.join;DoorGlassAppearance g=settings.glass;
        if(b.id==2)activation=GuiOptionCycle.next(Math.max(0,activation),3);if(b.id==6)direction=GuiOptionCycle.next(direction,4);if(b.id==7)straight=!straight;if(b.id==8)mode=GuiOptionCycle.next(mode,3);if(b.id==9)window=!window;if(b.id==10)shape=GuiOptionCycle.next(shape,4);if(b.id==11)join=!join;if(b.id==12)g=new DoorGlassAppearance(g.material,g.shade,GuiOptionCycle.next(g.tint,4),g.layout);if(b.id==13)g=new DoorGlassAppearance(g.material,g.shade,g.tint,GuiOptionCycle.next(g.layout,3));update(direction,straight,mode,activation,window,shape,join,g);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+26,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,26);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Segmented Door",12,10,0xffffff);if(page==2&&gateSelected()){int gap=Math.min(24,(ySize-88)/10);fontRenderer.drawSplitString("Authored gate model. Window settings are retained.",xSize-166,54+96*gap/24,154,0x9fb1c5);}if(page==1){fontRenderer.drawString("Channels",12,56,0xcbd6e2);fontRenderer.drawSplitString("Configure any section with Shift-right-click or the Configurizer. Redstone activation disables manual opening and closing.",12,152,Math.max(80,layout.listWidth),0x9fb1c5);}if(!error.isEmpty())fontRenderer.drawString(fontRenderer.trimStringToWidth(error,xSize-24),12,ySize-90,0xff7777);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();if(page==1)channels.drawTextBox();HousingTextureList picker=materialPicker();if(picker!=null)picker.draw(fontRenderer,x,y);renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2,5,6,7,8,9,10,11,12,13,14,15))return;HousingTextureList picker=materialPicker();if(picker!=null&&picker.click(x,y,button)){if(picker.picked()){if(page==2){selectedTexture(picker.selected());}else glass(picker.selected());}return;}if(button==0)for(GuiButton option:new java.util.ArrayList<>(buttonList))if(option.mousePressed(mc,x,y)){option.playPressSound(mc.getSoundHandler());actionPerformed(option);return;}super.mouseClicked(x,y,button);if(page==1)channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char typed,int key)throws IOException{if(page==1&&channels.textboxKeyTyped(typed,key)){apply();return;}if(key==1||key==mc.gameSettings.keyBindInventory.getKeyCode())apply();super.keyTyped(typed,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
}
