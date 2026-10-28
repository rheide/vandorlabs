package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.RedstoneScreenContents;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.*;
import java.io.IOException;
import java.util.*;

/** Standalone block entry point; the row contents are shared with native host pages. */
public final class GuiRedstoneScreen extends GuiMaterialPicker {
    private final RedstoneScreenContents tile;
    private final boolean header;
    private RedstoneRowsPane rows;
    private HousingTextureList housing;
    private GuiTextField titleField,labelField,channelField;
    private boolean materials;
    private String title;
    private int texture;
    public GuiRedstoneScreen(InventoryPlayer inventory,RedstoneScreenContents tile){super(new ContainerAnimatedScreenSelector(inventory,tile.tile(),tile.slot()));this.tile=tile;header=!com.vandorlabs.blocks.RedstoneScreenInteractions.half(tile.getBlockType(),tile.slot());title=tile.title();texture=tile.getHousingTexture();}
    @Override protected HousingTextureList materialPicker(){return materials?housing:null;}
    public void initGui(){xSize=Math.min(420,width-12);ySize=Math.min(300,height-12);super.initGui();buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        titleField=new GuiTextField(2,fontRenderer,guiLeft+66,guiTop+52,xSize-82,18);titleField.setMaxStringLength(RedstoneScreenContents.MAX_TITLE);titleField.setText(title);titleField.setVisible(header&&!materials);
        if(rows==null){NBTTagList list=tile.rowConfiguration();for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);row.setInteger("Kind",row.getBoolean("Pulse")?2:row.getBoolean("Slider")?1:0);}rows=new RedstoneRowsPane(fontRenderer,tile.maxRows(),true,list,index->index<tile.rows().size()&&!TransmitterSlots.get(tile.rows().get(index)).isEmpty(),this::applyRows);}
        rows.layout(guiLeft+12,guiTop+(header?78:54),xSize-24,ySize-(header?78:54)-28,buttonList);labelField=rows.labelField();channelField=rows.channelField();
        int[] legacy={2,3,5,6,7,8,9,90};for(GuiButton b:rows.buttons()){int action=b.id-RedstoneRowsPane.BASE;b.id=legacy[action];b.visible&=!materials;}
        housing=new HousingTextureList(guiLeft+12,guiTop+54,xSize-24,texture).visibleRows(Math.max(2,(ySize-88)/HousingTextureList.ROW_HEIGHT)).custom(value->{texture=value;});
        GuiButton rowTab=new DialogTabButton(0,guiLeft+12,guiTop+28,76,20,"Rows"),sidesTab=new DialogTabButton(1,guiLeft+92,guiTop+28,90,20,"Sides");rowTab.enabled=materials;sidesTab.enabled=!materials;buttonList.add(rowTab);buttonList.add(sidesTab);buttonList.add(new GuiButton(4,guiLeft+xSize-96,guiTop+ySize-24,84,20,"Done"));
    }
    private void applyRows(NBTTagList values){List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();List<Boolean> sliders=new ArrayList<>(),pulses=new ArrayList<>();List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();for(int i=0;i<values.tagCount();i++){NBTTagCompound row=values.getCompoundTagAt(i);labels.add(row.getString("Label"));channels.add(ChannelData.read(row,0));sliders.add(row.getInteger("Kind")==1);pulses.add(row.getInteger("Kind")==2);mins.add(row.getInteger("Min"));maxs.add(row.getInteger("Max"));}PacketHandler.INSTANCE.sendToServer(new MessageRedstoneScreen(tile.getPos(),tile.slot(),title,labels,channels,texture).withSliders(sliders,mins,maxs).withPulses(pulses));}
    protected void actionPerformed(GuiButton button){if(button.id==0||button.id==1){rows.save();materials=button.id==1;initGui();return;}if(button.id==4){if(header){if(!RedstoneScreenContents.validTitle(titleField.getText())||!RedstoneScreenText.fits(fontRenderer,titleField.getText(),RedstoneScreenText.TITLE_WIDTH))return;title=titleField.getText();}if(!rows.save())return;mc.player.closeScreen();return;}if(!materials){for(GuiButton candidate:rows.buttons())if(candidate.id==button.id){rows.action(candidate);break;}}}
    int inlineTransmitterSelector(){return rows.selected()<0?-1:1+tile.slot()*8+rows.selected();}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,7,8,9))return;super.mouseClicked(x,y,button);titleField.mouseClicked(x,y,button);if(materials){if(housing.click(x,y,button))texture=housing.selected();}else rows.mouseClicked(x,y,button);}
    protected void keyTyped(char ch,int key)throws IOException{if(titleField.getVisible()&&titleField.textboxKeyTyped(ch,key)){title=titleField.getText();return;}if(!materials&&rows.keyTyped(ch,key))return;super.keyTyped(ch,key);}
    public void updateScreen(){super.updateScreen();titleField.updateCursorCounter();rows.update();}
    public void handleMouseInput()throws IOException{super.handleMouseInput();if(!materials)rows.wheel(org.lwjgl.input.Mouse.getEventDWheel());}
    public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);DialogHeader.logo(guiLeft,guiTop,xSize);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();if(materials)housing.draw(fontRenderer,x,y);else{if(header){fontRenderer.drawString("Header",guiLeft+12,guiTop+57,0xdae8f0);titleField.drawTextBox();}rows.draw();}net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableLighting();}
    void checkLayout(){for(GuiButton a:buttonList)if(a.visible){if(a.x<0||a.y<0||a.x+a.width>width||a.y+a.height>height)throw new IllegalStateException("Row button outside viewport: "+a.id);for(GuiButton b:buttonList)if(b.visible&&a.id<b.id&&a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y)throw new IllegalStateException("Row buttons overlap: "+a.id+"/"+b.id);}}
    static int moveRow(List<String> labels,List<ChannelList> channels,int selected,int delta){int next=selected+delta;if(labels.size()!=channels.size()||selected<0||selected>=labels.size()||Math.abs(delta)!=1||next<0||next>=labels.size())return selected;Collections.swap(labels,selected,next);Collections.swap(channels,selected,next);return next;}
}
