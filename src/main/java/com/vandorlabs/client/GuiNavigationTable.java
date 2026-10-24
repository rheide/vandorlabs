package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import java.io.IOException;

/** Shared material picker and reader controls for both navigation modules. */
public final class GuiNavigationTable extends GuiMaterialPicker {
    private final TileEntityNavigationTable tile;
    private HousingTextureList textures;private ProgrammableDialogLayout panel;private GuiTextField channels;
    private int texture,speed,allowed,behavior;private boolean animated,redstone,security;
    public GuiNavigationTable(ContainerTokenSlot container){super(container);tile=(TileEntityNavigationTable)container.tile;NavigationScreenSettings s=tile.screenSettings();texture=s.texture;speed=s.speed;animated=s.animated;redstone=s.redstone;security=s.security;allowed=tile.effectiveAllowed();behavior=tile.behavior;}
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui(){
        String text=channels==null?tile.getRedstoneChannels().toString():channels.getText();panel=new ProgrammableDialogLayout(width,height);
        xSize=panel.width;ySize=panel.height;super.initGui();buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        textures=HousingTextureList.forNavigation(panel.listX,panel.top+30,panel.listWidth,texture,(panel.height-42)/12).keepView(textures).custom(value->{texture=value;save();});
        for(int id=1;id<=5;id++){GuiButton button=panel.control(id,controlY(id),"");if(panel.height<280)button.height=18;buttonList.add(button);}
        channels=new GuiTextField(6,fontRenderer,panel.controlsX,panel.top+channelY(),ProgrammableDialogLayout.CONTROLS_WIDTH,18);ChannelFields.configure(channels);channels.setText(text);buttonList.add(panel.done(7));labels();
    }
    int controlY(int id){return panel.height<280?30+(id-1)*20:32+(id-1)*24;}
    private int channelLabelY(){return panel.height<280?134:152;}
    private int channelY(){return panel.height<280?146:166;}
    private int filterLabelY(){return panel.height<280?172:196;}
    int filterY(){return panel.height<280?184:210;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 1:b.displayString="Redstone: "+(redstone?"Enabled":"Disabled");break;case 2:b.displayString="Security: "+(security?"Enabled":"Disabled");break;case 3:b.displayString=animated?"Animated":"Static";break;case 4:b.displayString="Speed: "+new String[]{"Slow","Normal","Fast"}[speed];break;case 5:b.displayString=com.vandorlabs.tiles.TileEntityTokenSlot.behaviorName(behavior);break;}}
    private void save(){ChannelList list=ChannelList.parse(channels.getText());if(list==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);PacketHandler.INSTANCE.sendToServer(new MessageNavigationTable(inventorySlots.windowId,new NavigationScreenSettings(texture,animated,speed,redstone,security),allowed,behavior,list));}
    @Override protected void actionPerformed(GuiButton b){switch(b.id){case 1:redstone=!redstone;break;case 2:security=!security;break;case 3:animated=!animated;break;case 4:speed=GuiOptionCycle.next(speed,3);break;case 5:behavior=GuiOptionCycle.next(behavior,4);break;case 7:mc.player.closeScreen();return;}save();labels();}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2,3,4,5))return;
        if(textures.click(x,y,button)){if(textures.picked()){int choice=textures.selected();texture=choice==HousingTextureList.NAVIGATION_BLACK?-1:choice;save();}return;}
        if(CredentialFilterPicker.click(this,x,y,button,panel.controlsX,panel.top+filterY(),true,allowed,mask->{allowed=mask;save();}))return;
        channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override public void updateScreen(){channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+26,0xff304858);textures.draw(fontRenderer,x,y);CredentialFilterPicker.draw(mc,fontRenderer,panel.controlsX,panel.top+filterY(),true,allowed);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Navigation Display",12,10,0xffffff);fontRenderer.drawString("Input channels (0 = none)",panel.controlsX-guiLeft,channelLabelY(),0xffffff);fontRenderer.drawString("Accepted Data Chip",panel.controlsX-guiLeft,filterLabelY(),0xffffff);if(panel.height>=280)fontRenderer.drawSplitString("Display only; no redstone output.",panel.controlsX-guiLeft,240,154,0xabcfe8);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
