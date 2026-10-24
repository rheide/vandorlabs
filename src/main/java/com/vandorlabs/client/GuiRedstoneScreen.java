package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.RedstoneScreenContents;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;

/** A bounded row list, with one label/channel editor and the existing housing picker. */
public final class GuiRedstoneScreen extends GuiContainer {
    private final RedstoneScreenContents tile;
    private GuiCockpit cockpitParent;private com.vandorlabs.tiles.TileEntityCockpit cockpitTile;private int cockpitDisplay;
    private int maxRows(){return tile==null?cockpitTile.displays.get(cockpitDisplay).maxRows():tile.maxRows();}
    private final List<Boolean> sliders=new ArrayList<>();
    private final List<Boolean> pulses=new ArrayList<>();
    private final List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private GuiTextField titleField,labelField,channelField;
    private String title;
    private final boolean header;
    private int rowListTop;
    private com.vandorlabs.ui.RedstoneDialogLayout layout;
    private HousingTextureList housing;
    private int selected=-1,scroll,texture;
    private boolean materials;
    public GuiRedstoneScreen(InventoryPlayer inventory,RedstoneScreenContents tile){
        super(new ContainerAnimatedScreenSelector(inventory,tile.tile(),tile.slot()));this.tile=tile;header=!com.vandorlabs.blocks.RedstoneScreenInteractions.half(tile.getBlockType(),tile.slot());title=tile.title();texture=tile.getHousingTexture();
        for(int i=0;i<Math.min(tile.rows().size(),tile.maxRows());i++){RedstoneScreenContents.Row row=tile.rows().get(i);labels.add(row.label);channels.add(row.channels);sliders.add(row.slider);pulses.add(row.pulse);mins.add(row.min);maxs.add(row.max);}
        if(!labels.isEmpty())selected=0;
    }
    public GuiRedstoneScreen(GuiCockpit parent,com.vandorlabs.container.ContainerCockpit container,int display){
        super(container);tile=null;cockpitParent=parent;cockpitTile=container.tile;cockpitDisplay=display;header=false;title="";texture=cockpitTile.sideTexture();
        com.vandorlabs.tiles.TileEntityCockpit.Display screen=cockpitTile.displays.get(display);
        for(int i=0;i<screen.rows;i++){com.vandorlabs.tiles.TileEntityCockpit.Control row=cockpitTile.control(screen.id+"/"+i);labels.add(row.label);channels.add(row.channels);sliders.add(row.kind==1);pulses.add(row.kind==2);mins.add(row.min);maxs.add(row.max);}
        if(!labels.isEmpty())selected=0;
    }
    public void initGui(){
        if(labelField!=null)store();
        layout=new com.vandorlabs.ui.RedstoneDialogLayout(width,height,header);xSize=layout.width;ySize=layout.height;super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=layout.controlsX;
        rowListTop=layout.rowListTop;
        com.vandorlabs.ui.ProgrammableDialogLayout.Button titleBounds=layout.title(),labelBounds=layout.label(),channelBounds=layout.channels();
        titleField=new GuiTextField(titleBounds.id,fontRenderer,titleBounds.x,titleBounds.y,titleBounds.width,titleBounds.height);titleField.setMaxStringLength(RedstoneScreenContents.MAX_TITLE);titleField.setText(title);
        labelField=new GuiTextField(labelBounds.id,fontRenderer,labelBounds.x,labelBounds.y,labelBounds.width,labelBounds.height);labelField.setMaxStringLength(RedstoneScreenContents.MAX_LABEL);
        channelField=new GuiTextField(channelBounds.id,fontRenderer,channelBounds.x,channelBounds.y,channelBounds.width,channelBounds.height);ChannelFields.configure(channelField);
        housing=new HousingTextureList(guiLeft+12,guiTop+54,xSize-188,texture).visibleRows(Math.max(2,(ySize-88)/HousingTextureList.ROW_HEIGHT)).custom(value->{texture=value;});
        for(com.vandorlabs.ui.RedstoneDialogLayout.Control control:layout.controls(materials,selected,labels.size(),maxRows(),selected<0?0:pulses.get(selected)?2:sliders.get(selected)?1:0,selected<0?5:mins.get(selected),selected<0?15:maxs.get(selected))){
            com.vandorlabs.ui.ProgrammableDialogLayout.Button bounds=control.bounds;
            GuiButton button=new GuiButton(bounds.id,bounds.x,bounds.y,bounds.width,bounds.height,bounds.label);button.visible=control.visible;button.enabled=control.enabled;buttonList.add(button);
        }
        load();refresh();
    }
    private boolean store(){
        boolean headingValid=!header || RedstoneScreenContents.validTitle(titleField.getText()) && RedstoneScreenText.fits(fontRenderer,titleField.getText(),RedstoneScreenText.TITLE_WIDTH);
        titleField.setTextColor(headingValid?0xE0E0E0:0xFF7777);
        if(!headingValid)return false;
        if(header)title=titleField.getText().trim();
        if(selected<0)return true;
        ChannelList list=ChannelFields.parse(channelField);boolean valid=RedstoneScreenContents.validLabel(labelField.getText()) && RedstoneScreenText.fits(fontRenderer,labelField.getText(),(sliders.get(selected)?RedstoneScreenContents.SLIDER_LABEL_WIDTH:RedstoneScreenText.LABEL_WIDTH)) && (!sliders.get(selected) || labelField.getText().length()<=RedstoneScreenContents.SLIDER_LABEL);
        labelField.setTextColor(valid?0xE0E0E0:0xFF7777);
        if(!valid || list==null)return false;
        labels.set(selected,labelField.getText().trim());channels.set(selected,list);return true;
    }
    private void load(){labelField.setText(selected<0?"":labels.get(selected));channelField.setText(selected<0?"0":channels.get(selected).toString());}
    private void refresh(){
        for(com.vandorlabs.ui.RedstoneDialogLayout.Control control:layout.controls(materials,selected,labels.size(),maxRows(),selected<0?0:pulses.get(selected)?2:sliders.get(selected)?1:0,selected<0?5:mins.get(selected),selected<0?15:maxs.get(selected)))for(GuiButton button:buttonList)if(button.id==control.bounds.id){button.visible=control.visible;button.enabled=control.enabled;button.displayString=control.bounds.label;}
        titleField.setVisible(header && !materials);
        labelField.setVisible(!materials && selected>=0);channelField.setVisible(!materials && selected>=0);
    }
    void checkLayout(){
        if(!materials && selected>=0){
            Map<Integer,GuiButton> controls=new HashMap<>();for(GuiButton b:buttonList)controls.put(b.id,b);
            if(!(controls.get(2).y<controls.get(5).y && controls.get(5).y+controls.get(5).height<labelField.y && labelField.y+labelField.height<channelField.y && channelField.y+channelField.height<controls.get(7).y && controls.get(7).y+controls.get(7).height<controls.get(8).y))throw new IllegalStateException("Redstone row editor order differs");
            if(!controls.get(8).displayString.startsWith("Low:") || !controls.get(9).displayString.startsWith("High:"))throw new IllegalStateException("Redstone row limit labels differ");
        }
        for(GuiButton a:buttonList)if(a.visible){
            if(a.x<0 || a.y<0 || a.x+a.width>width || a.y+a.height>height)throw new IllegalStateException("Row button outside viewport: "+a.id);
            for(GuiButton b:buttonList)if(b.visible && a.id<b.id && a.x<b.x+b.width && a.x+a.width>b.x && a.y<b.y+b.height && a.y+a.height>b.y)throw new IllegalStateException("Row buttons overlap: "+a.id+"/"+b.id);
        }
    }
    private void send(){if(!store())return;texture=housing.selected();if(tile==null){
        net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setInteger("RedstoneDisplay",cockpitDisplay);tag.setInteger("SideTexture",texture);net.minecraft.nbt.NBTTagList rows=new net.minecraft.nbt.NBTTagList();
        for(int i=0;i<labels.size();i++){net.minecraft.nbt.NBTTagCompound row=new net.minecraft.nbt.NBTTagCompound();row.setString("Label",labels.get(i));com.vandorlabs.redstone.ChannelData.write(row,channels.get(i));row.setInteger("Kind",pulses.get(i)?2:sliders.get(i)?1:0);row.setInteger("Min",mins.get(i));row.setInteger("Max",maxs.get(i));rows.appendTag(row);}tag.setTag("Rows",rows);cockpitTile.configureRows(cockpitDisplay,rows);cockpitTile.setSideTexture(texture);
        PacketHandler.INSTANCE.sendToServer(new MessageCockpit(inventorySlots.windowId,0,tag));mc.displayGuiScreen(cockpitParent);return;
    }PacketHandler.INSTANCE.sendToServer(new MessageRedstoneScreen(tile.getPos(),tile.slot(),title,labels,channels,texture).withSliders(sliders,mins,maxs).withPulses(pulses));mc.player.closeScreen();}
    protected void actionPerformed(GuiButton b){
        if(b.id==3 && selected>=0){labels.remove(selected);channels.remove(selected);sliders.remove(selected);pulses.remove(selected);mins.remove(selected);maxs.remove(selected);selected=Math.min(selected,labels.size()-1);scroll=Math.min(scroll,Math.max(0,labels.size()-visibleRows()));load();refresh();return;}
        if(b.id==7 && selected>=0){
            if(!store())return;int type=com.vandorlabs.ui.RedstoneDialogLayout.nextKind(pulses.get(selected)?2:sliders.get(selected)?1:0,GuiOptionCycle.direction());sliders.set(selected,type==1);pulses.set(selected,type==2);
            if(sliders.get(selected)){labels.set(selected,com.vandorlabs.ui.RedstoneDialogLayout.sliderLabel(labels.get(selected),fontRenderer::getStringWidth));}
            load();refresh();return;
        }
        if(b.id==8 && selected>=0){mins.set(selected,com.vandorlabs.ui.RedstoneDialogLayout.nextLow(mins.get(selected),maxs.get(selected),GuiOptionCycle.direction()));refresh();return;}
        if(b.id==9 && selected>=0){maxs.set(selected,com.vandorlabs.ui.RedstoneDialogLayout.nextHigh(mins.get(selected),maxs.get(selected),GuiOptionCycle.direction()));refresh();return;}
        if(!store())return;
        if(b.id<2){materials=b.id==1;titleField.setFocused(false);labelField.setFocused(false);channelField.setFocused(false);refresh();}
        else if(b.id==2 && labels.size()<maxRows()){labels.add("Item "+(labels.size()+1));channels.add(ChannelList.EMPTY);sliders.add(false);pulses.add(false);mins.add(5);maxs.add(15);selected=labels.size()-1;scroll=Math.max(0,selected-visibleRows()+1);load();refresh();labelField.setFocused(true);}
        else if(b.id==5 || b.id==6){
            int next=moveRow(labels,channels,selected,b.id==5?-1:1);
            if(next!=selected){Collections.swap(sliders,selected,next);Collections.swap(pulses,selected,next);Collections.swap(mins,selected,next);Collections.swap(maxs,selected,next);}
            if(next!=selected){
                selected=next;scroll=Math.max(Math.min(scroll,selected),selected-visibleRows()+1);
                load();refresh();
            }
        }
        else if(b.id==4)send();
    }
    static int moveRow(List<String> labels,List<ChannelList> channels,int selected,int delta){
        return com.vandorlabs.ui.RedstoneDialogLayout.moveRows(labels,channels,selected,delta);
    }
    private int visibleRows(){return layout.visibleRows();}
    protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,7,8,9))return;
        super.mouseClicked(x,y,button);
        if(materials){if(housing.click(x,y,button))texture=housing.selected();return;}
        titleField.mouseClicked(x,y,button);labelField.mouseClicked(x,y,button);channelField.mouseClicked(x,y,button);
        if(x>=guiLeft+12 && x<guiLeft+xSize-180 && y>=guiTop+rowListTop && y<guiTop+rowListTop+visibleRows()*20){
            int next=scroll+(y-guiTop-rowListTop)/20;if(next<labels.size() && store()){selected=next;load();refresh();}
        }
    }
    protected void mouseReleased(int x,int y,int button){housing.release();super.mouseReleased(x,y,button);}
    protected void mouseClickMove(int x,int y,int button,long elapsed){if(materials && housing.drag(y))return;super.mouseClickMove(x,y,button,elapsed);}
    public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){
        if(materials)housing.wheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,wheel);else scroll=Math.max(0,Math.min(Math.max(0,labels.size()-visibleRows()),scroll+(wheel>0?-1:1)));
    }}
    protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER){send();return;}
        if(!materials && (typeText(titleField,RedstoneScreenText.TITLE_WIDTH,c,key)||typeText(labelField,selected>=0 && sliders.get(selected)?RedstoneScreenContents.SLIDER_LABEL_WIDTH:RedstoneScreenText.LABEL_WIDTH,c,key)||channelField.textboxKeyTyped(c,key))){store();return;}super.keyTyped(c,key);
    }
    private boolean typeText(GuiTextField field,int budget,char c,int key){
        String before=field.getText();int cursor=field.getCursorPosition(),selection=field.getSelectionEnd();
        if(!field.textboxKeyTyped(c,key))return false;
        // Reject an overflowing insertion or paste intact; keep deletion available
        // when editing an oversized label from an older saved screen.
        if(!RedstoneScreenText.fits(fontRenderer,field.getText(),budget) && fontRenderer.getStringWidth(field.getText())>fontRenderer.getStringWidth(before)){
            field.setText(before);field.setCursorPosition(cursor);field.setSelectionPos(selection);
        }
        return true;
    }
    public void updateScreen(){super.updateScreen();titleField.updateCursorCounter();labelField.updateCursorCounter();channelField.updateCursorCounter();}
    public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);}
    public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();super.drawScreen(x,y,partial);
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        fontRenderer.drawString(tile==null?"Cockpit redstone controls":net.minecraft.client.resources.I18n.format(tile.getBlockType().getUnlocalizedName()+".name"),guiLeft+12,guiTop+10,0xFFFFFF);
        if(materials){housing.draw(fontRenderer,x,y);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableLighting();return;}
        for(int i=0;i<visibleRows() && scroll+i<labels.size();i++){
            int row=scroll+i,yy=guiTop+rowListTop+i*20;drawRect(guiLeft+12,yy,guiLeft+xSize-180,yy+18,row==selected?0xFF365C70:0xFF22313E);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(labels.get(row),xSize-204),guiLeft+18,yy+5,0xE0ECF5);
        }
        int cx=guiLeft+xSize-164;
        if(header){fontRenderer.drawString("Header",guiLeft+12,guiTop+55,0xDAE8F0);titleField.drawTextBox();drawRect(guiLeft+12,guiTop+76,guiLeft+xSize-180,guiTop+77,0xFF365366);}
        fontRenderer.drawString(fontRenderer.trimStringToWidth("Text is limited to screen width.",xSize-192),guiLeft+12,guiTop+ySize-14,0xADBECA);
        if(selected>=0){fontRenderer.drawString("Label",cx,guiTop+94,0xDAE8F0);fontRenderer.drawString("Channels (0 = none)",cx,guiTop+127,0xDAE8F0);labelField.drawTextBox();channelField.drawTextBox();}
        else fontRenderer.drawSplitString("Add an item to create a control.",cx,guiTop+105,148,0xADBECA);
        net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableLighting();
    }
}
