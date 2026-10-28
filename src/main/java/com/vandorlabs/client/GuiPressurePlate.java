package com.vandorlabs.client;

import com.vandorlabs.plate.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

/** The ordinary categorized material picker, with Trapdoors included in the main collection. */
public final class GuiPressurePlate extends GuiMaterialPicker {
    private final PlateContainer container;private HousingTextureList textures;private GuiTextField channels;private int mode,sensitivity,page=1;
    public GuiPressurePlate(PlateContainer container){super(container);this.container=container;mode=container.tile.mode();sensitivity=container.tile.sensitivity();}
    @Override protected HousingTextureList materialPicker(){return page==0?textures:null;}
    @Override public void initGui(){
        String draft=channels==null?container.tile.getRedstoneChannels().toString():channels.getText();ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);xSize=layout.width;ySize=layout.height;super.initGui();Keyboard.enableRepeatEvents(true);
        textures=HousingTextureList.forCategory("Trapdoors",layout.listX,guiTop+62,layout.listWidth,textures==null?container.tile.texture():textures.selected()).visibleRows(layout.rows(true)-1).keepView(textures);
        channels=new GuiTextField(0,fontRenderer,layout.controlsX,guiTop+49,154,18);ChannelFields.configure(channels);channels.setText(draft);channels.setVisible(false);buttonList.add(layout.tab(100,0,2,"General"));buttonList.add(layout.tab(101,1,2,"Redstone"));for(GuiButton b:buttonList)b.enabled=b.id!=100+page;if(page==1)buttonList.add(layout.control(90,64,"Redstone Transmitter..."));
        if(page==1){buttonList.add(layout.control(1,94,label()));buttonList.add(layout.control(3,120,"Sensitivity: "+PlateTile.sensitivityName(sensitivity)));}buttonList.add(layout.done(2));
    }
    private String label(){return new String[]{"Channels: Disabled","Channels: Pressed","Channels: Released"}[mode];}
    private void send(){com.vandorlabs.redstone.ChannelList list=ChannelFields.parse(channels);if(list!=null)com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new PlateMessage(inventorySlots.windowId,textures.selected(),mode,sensitivity,list.toString()));}
    @Override protected void actionPerformed(GuiButton button){if(button.id==100||button.id==101){page=button.id-100;initGui();return;}if(button.id==90){send();com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(container.tile.getPos(),0));return;}if(button.id==2){send();mc.player.closeScreen();return;}if(button.id==3){sensitivity=GuiOptionCycle.next(sensitivity,3);button.displayString="Sensitivity: "+PlateTile.sensitivityName(sensitivity);}else{mode=GuiOptionCycle.next(mode,3);button.displayString=label();}send();}
    @Override protected void mouseClicked(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,3))return;channels.mouseClicked(x,y,button);if(page==0&&textures.click(x,y,button)){if(textures.picked())send();return;}super.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws java.io.IOException{if(channels.textboxKeyTyped(c,key)){send();return;}if(key==Keyboard.KEY_ESCAPE||key==mc.gameSettings.keyBindInventory.getKeyCode())send();super.keyTyped(c,key);}
    @Override public void updateScreen(){super.updateScreen();channels.updateCursorCounter();}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize);if(page==0)textures.draw(fontRenderer,x,y);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString("Programmable Pressure Plate",12,8,0xffffff);int cx=xSize-166;if(page==1)fontRenderer.drawSplitString("Wood: On/Off. Gold: full power at 15 entities. Iron: full power at 150.",cx,154,154,0xc0c8d0);}
    @Override public void drawScreen(int x,int y,float partial){net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();channels.drawTextBox();}
}
