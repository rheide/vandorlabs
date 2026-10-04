package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;

/** A bounded row list, with one label/channel editor and the existing housing picker. */
public final class GuiRedstoneScreen extends GuiContainer {
    private final TileEntityRedstoneScreen tile;
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private GuiTextField labelField,channelField;
    private HousingTextureList housing;
    private int selected=-1,scroll,texture;
    private boolean materials;
    public GuiRedstoneScreen(InventoryPlayer inventory,TileEntityRedstoneScreen tile){
        super(new ContainerAnimatedScreenSelector(inventory,tile));this.tile=tile;texture=tile.getHousingTexture();
        for(TileEntityRedstoneScreen.Row row:tile.rows()){labels.add(row.label);channels.add(row.channels);}
        if(!labels.isEmpty())selected=0;
    }
    public void initGui(){
        if(labelField!=null)store();
        xSize=Math.min(420,width-12);ySize=Math.min(240,height-12);super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=guiLeft+xSize-164;
        labelField=new GuiTextField(0,fontRenderer,cx,guiTop+64,148,18);labelField.setMaxStringLength(TileEntityRedstoneScreen.MAX_LABEL);
        channelField=new GuiTextField(1,fontRenderer,cx,guiTop+106,148,18);ChannelFields.configure(channelField);
        housing=new HousingTextureList(guiLeft+12,guiTop+54,xSize-188,texture).visibleRows(Math.max(2,(ySize-88)/HousingTextureList.ROW_HEIGHT)).custom(value->{texture=value;});
        buttonList.add(new GuiButton(0,guiLeft+12,guiTop+28,76,20,"Rows"));
        buttonList.add(new GuiButton(1,guiLeft+92,guiTop+28,90,20,"Housing"));
        buttonList.add(new GuiButton(2,cx,guiTop+144,70,20,"Add"));
        buttonList.add(new GuiButton(3,cx+76,guiTop+144,72,20,"Remove"));
        buttonList.add(new GuiButton(4,cx,guiTop+ySize-30,148,20,"Done"));load();refresh();
    }
    private boolean store(){
        if(selected<0)return true;
        ChannelList list=ChannelFields.parse(channelField);boolean valid=TileEntityRedstoneScreen.validLabel(labelField.getText());
        labelField.setTextColor(valid?0xE0E0E0:0xFF7777);
        if(!valid || list==null)return false;
        labels.set(selected,labelField.getText().trim());channels.set(selected,list);return true;
    }
    private void load(){labelField.setText(selected<0?"":labels.get(selected));channelField.setText(selected<0?"0":channels.get(selected).toString());}
    private void refresh(){
        labelField.setVisible(!materials && selected>=0);channelField.setVisible(!materials && selected>=0);
        for(GuiButton b:buttonList){if(b.id<2)b.enabled=(b.id==1)!=materials;if(b.id==2){b.visible=!materials;b.enabled=labels.size()<TileEntityRedstoneScreen.MAX_ROWS;}if(b.id==3){b.visible=!materials;b.enabled=selected>=0;}}
    }
    private void send(){if(!store())return;texture=housing.selected();PacketHandler.INSTANCE.sendToServer(new MessageRedstoneScreen(tile.getPos(),labels,channels,texture));mc.player.closeScreen();}
    protected void actionPerformed(GuiButton b){
        if(b.id==3 && selected>=0){labels.remove(selected);channels.remove(selected);selected=Math.min(selected,labels.size()-1);scroll=Math.min(scroll,Math.max(0,labels.size()-visibleRows()));load();refresh();return;}
        if(!store())return;
        if(b.id<2){materials=b.id==1;labelField.setFocused(false);channelField.setFocused(false);refresh();}
        else if(b.id==2 && labels.size()<TileEntityRedstoneScreen.MAX_ROWS){labels.add("Item "+(labels.size()+1));channels.add(ChannelList.EMPTY);selected=labels.size()-1;scroll=Math.max(0,selected-visibleRows()+1);load();refresh();labelField.setFocused(true);}
        else if(b.id==4)send();
    }
    private int visibleRows(){return Math.max(1,(ySize-66)/20);}
    protected void mouseClicked(int x,int y,int button)throws IOException{
        super.mouseClicked(x,y,button);
        if(materials){if(housing.click(x,y,button))texture=housing.selected();return;}
        labelField.mouseClicked(x,y,button);channelField.mouseClicked(x,y,button);
        if(x>=guiLeft+12 && x<guiLeft+xSize-180 && y>=guiTop+54 && y<guiTop+54+visibleRows()*20){
            int next=scroll+(y-guiTop-54)/20;if(next<labels.size() && store()){selected=next;load();refresh();}
        }
    }
    public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){
        if(materials)housing.wheel(guiLeft+20,guiTop+60,wheel);else scroll=Math.max(0,Math.min(Math.max(0,labels.size()-visibleRows()),scroll+(wheel>0?-1:1)));
    }}
    protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER){send();return;}
        if(!materials && (labelField.textboxKeyTyped(c,key)||channelField.textboxKeyTyped(c,key))){store();return;}super.keyTyped(c,key);
    }
    public void updateScreen(){super.updateScreen();labelField.updateCursorCounter();channelField.updateCursorCounter();}
    public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);}
    public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();super.drawScreen(x,y,partial);
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        fontRenderer.drawString("Programmable Redstone Screen",guiLeft+12,guiTop+10,0xFFFFFF);
        if(materials){housing.draw(fontRenderer,x,y);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableLighting();return;}
        for(int i=0;i<visibleRows() && scroll+i<labels.size();i++){
            int row=scroll+i,yy=guiTop+54+i*20;drawRect(guiLeft+12,yy,guiLeft+xSize-180,yy+18,row==selected?0xFF365C70:0xFF22313E);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(labels.get(row),xSize-204),guiLeft+18,yy+5,0xE0ECF5);
        }
        int cx=guiLeft+xSize-164;
        if(selected>=0){fontRenderer.drawString("Label",cx,guiTop+52,0xDAE8F0);fontRenderer.drawString("Channels (0 = none)",cx,guiTop+94,0xDAE8F0);labelField.drawTextBox();channelField.drawTextBox();}
        else fontRenderer.drawSplitString("Add an item to create a control.",cx,guiTop+64,148,0xADBECA);
        net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableLighting();
    }
}
