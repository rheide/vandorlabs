package com.vandorlabs.client;

import com.vandorlabs.garage.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.*;
import java.io.IOException;

/** Shared material lists, activation selector and fixed-size mechanism tabs. */
public final class GuiGarageDoor extends GuiMaterialPicker {
    private final GarageDoorTile tile;
    private int page,ticks,texture;private HousingTextureList textures,glassTextures;private boolean local,opens;
    private GuiTextField channels;private String draft,error="",last="";private GarageDoorSettings settings,lastSettings;private ProgrammableDialogLayout layout;
    public GuiGarageDoor(GarageDoorContainer container){super(container);tile=container.tile;texture=tile.texture;ticks=tile.ticksPerSection;local=tile.localRedstone;opens=tile.signalOpens;settings=tile.settings;draft=tile.getRedstoneChannels().toString();xSize=460;ySize=332;}
    @Override protected HousingTextureList materialPicker(){return page==2?textures:page==3?glassTextures:null;}
    @Override public void initGui(){
        if(channels!=null)draft=channels.getText();layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();org.lwjgl.input.Keyboard.enableRepeatEvents(true);buttonList.clear();if(page==3&&!settings.window)page=2;
        int[] pages=settings.window?new int[]{0,2,3,1}:new int[]{0,2,1};String[] labels={"Mechanism","Redstone","Door","Glass"};for(int i=0;i<pages.length;i++){GuiButton b=layout.fullTab(90+pages[i],i,pages.length,labels[pages[i]]);b.enabled=page!=pages[i];buttonList.add(b);}
        channels=new GuiTextField(6,fontRenderer,guiLeft+12,guiTop+68,xSize-24,18);channels.setMaxStringLength(256);channels.setText(draft);channels.setVisible(page==1);
        if(page==0){buttonList.add(new GuiButton(6,guiLeft+12,guiTop+54,xSize-24,20,"Slide Direction: "+new String[]{"Up","Down","Left","Right"}[settings.direction]));buttonList.add(new GuiButton(7,guiLeft+12,guiTop+78,xSize-24,20,"Slide Angle: "+(settings.straight?"Straight":"Perpendicular")));buttonList.add(new GuiButton(5,guiLeft+12,guiTop+102,xSize-24,20,"Speed: "+(ticks<=5?"Fast":ticks<=10?"Medium":"Slow")));}
        if(page==1){buttonList.add(new RedstoneActivationButton(2,guiLeft+12,guiTop+96,xSize-24,Math.max(0,settings.activation)));buttonList.add(new GuiButton(1,guiLeft+12,guiTop+120,xSize-24,20,"Local Redstone: "+(local?"On":"Off")));}
        if(page==2){java.util.List<HousingTextureList.Option> options=new java.util.ArrayList<>();options.add(new HousingTextureList.Option(-1,"Original Sectional Panel","Industrial","vandorlabs:blocks/garage/garage_panel"));for(int i=0;i<ScreenHousingTextures.IDS.length;i++){int choice=ScreenHousingTextures.choiceAt(i);if(GarageDoorTile.validTexture(choice))options.add(new HousingTextureList.Option(choice,HousingTextureList.name(choice),ScreenHousingTextures.menuCategory(choice),ScreenHousingTextures.fullTexture(choice)));}
            textures=new HousingTextureList(layout.listX,guiTop+54,layout.listWidth,texture,8,options).visibleRows(Math.max(2,(ySize-64)/22)).keepView(textures).custom(value->{texture=value;apply();});
            buttonList.add(layout.control(8,54,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(settings.layout)));buttonList.add(layout.control(9,78,"Segment: "+(settings.window?"Window":"Solid")));
            if(settings.window){buttonList.add(layout.control(10,102,"Shape: "+new String[]{"Hexagon","Octagon","Square","Round"}[settings.shape]));buttonList.add(layout.control(11,126,"Window: "+(settings.join?"Fit":"Tile")));}
        }
        if(page==3){glassTextures=HousingTextureList.forGlassMaterials(0,layout.listX,guiTop+54,layout.listWidth,settings.glass.shade,settings.glass.material).visibleRows(Math.max(2,(ySize-64)/22)).keepView(glassTextures).custom(this::glass);buttonList.add(layout.control(12,54,"Tint: "+GlassTextures.tintLabel(settings.glass.tint)));if(settings.glass.material>=0)buttonList.add(layout.control(13,78,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(settings.glass.layout)));}
        GuiButton open=layout.control(3,ySize-76,"Open"),close=layout.control(4,ySize-52,"Close");open.enabled=close.enabled=settings.activation<=0;buttonList.add(open);buttonList.add(close);buttonList.add(layout.done(0));
    }
    private void update(int direction,boolean straight,int mode,int activation,boolean window,int shape,boolean join,DoorGlassAppearance glass){settings=new GarageDoorSettings(direction,straight,mode,activation,window,shape,join,glass);apply();initGui();}
    private void apply(){try{ChannelList selected=ChannelList.parse(channels==null?draft:channels.getText());if(selected==null)throw new IllegalArgumentException("Invalid channels");String key=ticks+":"+local+":"+opens+":"+texture+":"+selected;if(!key.equals(last)||!settings.equals(lastSettings)){PacketHandler.INSTANCE.sendToServer(new GarageDoorMessage(inventorySlots.windowId,0,ticks,local,opens,selected).settings(texture,settings));last=key;lastSettings=settings;}error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}}
    private void glass(int choice){int shade=settings.glass.shade,material=choice;if(choice>=HousingTextureList.CANOPY_GLASS_BASE&&choice<HousingTextureList.CANOPY_GLASS_BASE+GlassTextures.IDS.length){shade=choice-HousingTextureList.CANOPY_GLASS_BASE;material=-1;}update(settings.direction,settings.straight,settings.layout,settings.activation,settings.window,settings.shape,settings.join,new DoorGlassAppearance(material,shade,settings.glass.tint,settings.glass.layout));}
    @Override protected void actionPerformed(GuiButton b){
        if(b.id>=90){page=b.id-90;initGui();return;}
        if(b.id==0){apply();if(error.isEmpty())mc.player.closeScreen();return;}
        if(b.id==3||b.id==4){apply();if(error.isEmpty())PacketHandler.INSTANCE.sendToServer(new GarageDoorMessage(inventorySlots.windowId,b.id==3?1:2,ticks,local,opens,ChannelList.parse(channels.getText())));return;}
        if(b.id==1){local=!local;apply();initGui();return;}if(b.id==5){int speed=ticks<=5?0:ticks<=10?1:2;speed=GuiOptionCycle.next(speed,3);ticks=new int[]{5,10,20}[speed];apply();initGui();return;}
        int direction=settings.direction,mode=settings.layout,activation=settings.activation,shape=settings.shape;boolean straight=settings.straight,window=settings.window,join=settings.join;DoorGlassAppearance g=settings.glass;
        if(b.id==2)activation=GuiOptionCycle.next(Math.max(0,activation),3);if(b.id==6)direction=GuiOptionCycle.next(direction,4);if(b.id==7)straight=!straight;if(b.id==8)mode=GuiOptionCycle.next(mode,3);if(b.id==9)window=!window;if(b.id==10)shape=GuiOptionCycle.next(shape,4);if(b.id==11)join=!join;if(b.id==12)g=new DoorGlassAppearance(g.material,g.shade,GuiOptionCycle.next(g.tint,4),g.layout);if(b.id==13)g=new DoorGlassAppearance(g.material,g.shade,g.tint,GuiOptionCycle.next(g.layout,3));update(direction,straight,mode,activation,window,shape,join,g);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xee18212c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+26,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,26);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Sectional Door",12,10,0xffffff);if(page==0){fontRenderer.drawString("Height: "+tile.height+" sections; width: "+tile.columns,12,134,0xcbd6e2);fontRenderer.drawSplitString(settings.straight?"Keep the straight slide path clear.":"Keep the turning track clear.",12,152,Math.max(80,layout.listWidth),0x9fb1c5);}if(page==1){fontRenderer.drawString("Channels",12,56,0xcbd6e2);fontRenderer.drawSplitString("Configure any section with Shift-right-click or the Configurizer. Redstone activation disables manual opening and closing.",12,152,Math.max(80,layout.listWidth),0x9fb1c5);}if(!error.isEmpty())fontRenderer.drawString(fontRenderer.trimStringToWidth(error,xSize-24),12,ySize-90,0xff7777);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();if(page==1)channels.drawTextBox();HousingTextureList picker=materialPicker();if(picker!=null)picker.draw(fontRenderer,x,y);renderHoveredToolTip(x,y);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2,5,6,7,8,9,10,11,12,13))return;HousingTextureList picker=materialPicker();if(picker!=null&&picker.click(x,y,button)){if(picker.picked()){if(page==2){texture=picker.selected();apply();}else glass(picker.selected());}return;}if(button==0)for(GuiButton option:new java.util.ArrayList<>(buttonList))if(option.mousePressed(mc,x,y)){option.playPressSound(mc.getSoundHandler());actionPerformed(option);return;}super.mouseClicked(x,y,button);if(page==1)channels.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char typed,int key)throws IOException{if(page==1&&channels.textboxKeyTyped(typed,key)){apply();return;}if(key==1||key==mc.gameSettings.keyBindInventory.getKeyCode())apply();super.keyTyped(typed,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
}
