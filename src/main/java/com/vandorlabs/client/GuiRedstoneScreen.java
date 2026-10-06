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
    private final List<Boolean> sliders=new ArrayList<>();
    private final List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private GuiTextField titleField,labelField,channelField;
    private String title;
    private final boolean header;
    private int rowListTop;
    private HousingTextureList housing;
    private int selected=-1,scroll,texture;
    private boolean materials;
    public GuiRedstoneScreen(InventoryPlayer inventory,RedstoneScreenContents tile){
        super(new ContainerAnimatedScreenSelector(inventory,tile.tile(),tile.slot()));this.tile=tile;header=!com.vandorlabs.blocks.RedstoneScreenInteractions.half(tile.getBlockType(),tile.slot());title=tile.title();texture=tile.getHousingTexture();
        for(int i=0;i<Math.min(tile.rows().size(),tile.maxRows());i++){RedstoneScreenContents.Row row=tile.rows().get(i);labels.add(row.label);channels.add(row.channels);sliders.add(row.slider);mins.add(row.min);maxs.add(row.max);}
        if(!labels.isEmpty())selected=0;
    }
    public void initGui(){
        if(labelField!=null)store();
        xSize=Math.min(420,width-12);ySize=Math.min(300,height-12);super.initGui();buttonList.clear();Keyboard.enableRepeatEvents(true);
        int cx=guiLeft+xSize-164;
        rowListTop=header?82:54;
        titleField=new GuiTextField(2,fontRenderer,guiLeft+78,guiTop+50,xSize-258,18);titleField.setMaxStringLength(RedstoneScreenContents.MAX_TITLE);titleField.setText(title);
        labelField=new GuiTextField(0,fontRenderer,cx,guiTop+105,148,18);labelField.setMaxStringLength(RedstoneScreenContents.MAX_LABEL);
        channelField=new GuiTextField(1,fontRenderer,cx,guiTop+138,148,18);ChannelFields.configure(channelField);
        housing=new HousingTextureList(guiLeft+12,guiTop+54,xSize-188,texture).visibleRows(Math.max(2,(ySize-88)/HousingTextureList.ROW_HEIGHT)).custom(value->{texture=value;});
        buttonList.add(new GuiButton(0,guiLeft+12,guiTop+28,76,20,"Rows"));
        buttonList.add(new GuiButton(1,guiLeft+92,guiTop+28,90,20,"Housing"));
        buttonList.add(new GuiButton(2,cx,guiTop+54,70,18,"Add"));
        buttonList.add(new GuiButton(3,cx+76,guiTop+54,72,18,"Remove"));
        buttonList.add(new GuiButton(5,cx,guiTop+74,70,18,"Up"));
        buttonList.add(new GuiButton(6,cx+76,guiTop+74,72,18,"Down"));
        buttonList.add(new GuiButton(7,cx,guiTop+160,148,18,"Toggle"));
        buttonList.add(new GuiButton(8,cx,guiTop+182,70,18,"Low: 5"));buttonList.add(new GuiButton(9,cx+76,guiTop+182,72,18,"High: 15"));
        buttonList.add(new GuiButton(4,cx,guiTop+ySize-22,148,20,"Done"));load();refresh();
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
        for(GuiButton b:buttonList)if(b.id>=7 && b.id<=9){b.visible=!materials && selected>=0;if(selected>=0){b.enabled=b.id==7 || sliders.get(selected);b.displayString=b.id==7?(sliders.get(selected)?"Control: Slider":"Control: Toggle"):b.id==8?"Low: "+mins.get(selected):"High: "+maxs.get(selected);}}

        titleField.setVisible(header && !materials);
        labelField.setVisible(!materials && selected>=0);channelField.setVisible(!materials && selected>=0);
        for(GuiButton b:buttonList){if(b.id<2)b.enabled=(b.id==1)!=materials;if(b.id==2){b.visible=!materials;b.enabled=labels.size()<tile.maxRows();}if(b.id==3){b.visible=!materials;b.enabled=selected>=0;}if(b.id==5 || b.id==6){b.visible=!materials;b.enabled=selected>=0 && (b.id==5?selected>0:selected<labels.size()-1);}}
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
    private void send(){if(!store())return;texture=housing.selected();PacketHandler.INSTANCE.sendToServer(new MessageRedstoneScreen(tile.getPos(),tile.slot(),title,labels,channels,texture).withSliders(sliders,mins,maxs));mc.player.closeScreen();}
    protected void actionPerformed(GuiButton b){
        if(b.id==3 && selected>=0){labels.remove(selected);channels.remove(selected);sliders.remove(selected);mins.remove(selected);maxs.remove(selected);selected=Math.min(selected,labels.size()-1);scroll=Math.min(scroll,Math.max(0,labels.size()-visibleRows()));load();refresh();return;}
        if(b.id==7 && selected>=0){
            if(!store())return;sliders.set(selected,!sliders.get(selected));
            if(sliders.get(selected)){String value=labels.get(selected);while(value.length()>RedstoneScreenContents.SLIDER_LABEL || fontRenderer.getStringWidth(value)>RedstoneScreenContents.SLIDER_LABEL_WIDTH)value=value.substring(0,value.length()-1);labels.set(selected,value);}
            load();refresh();return;
        }
        if(b.id==8 && selected>=0){mins.set(selected,mins.get(selected)>=maxs.get(selected)-2?1:mins.get(selected)+1);refresh();return;}
        if(b.id==9 && selected>=0){maxs.set(selected,maxs.get(selected)==15?mins.get(selected)+2:maxs.get(selected)+1);refresh();return;}
        if(!store())return;
        if(b.id<2){materials=b.id==1;titleField.setFocused(false);labelField.setFocused(false);channelField.setFocused(false);refresh();}
        else if(b.id==2 && labels.size()<tile.maxRows()){labels.add("Item "+(labels.size()+1));channels.add(ChannelList.EMPTY);sliders.add(false);mins.add(5);maxs.add(15);selected=labels.size()-1;scroll=Math.max(0,selected-visibleRows()+1);load();refresh();labelField.setFocused(true);}
        else if(b.id==5 || b.id==6){
            int next=moveRow(labels,channels,selected,b.id==5?-1:1);
            if(next!=selected){Collections.swap(sliders,selected,next);Collections.swap(mins,selected,next);Collections.swap(maxs,selected,next);}
            if(next!=selected){
                selected=next;scroll=Math.max(Math.min(scroll,selected),selected-visibleRows()+1);
                load();refresh();
            }
        }
        else if(b.id==4)send();
    }
    static int moveRow(List<String> labels,List<ChannelList> channels,int selected,int delta){
        int next=selected+delta;
        if(labels.size()!=channels.size() || selected<0 || selected>=labels.size()
                || Math.abs(delta)!=1 || next<0 || next>=labels.size())return selected;
        Collections.swap(labels,selected,next);Collections.swap(channels,selected,next);return next;
    }
    private int visibleRows(){return Math.max(1,(ySize-rowListTop-12)/20);}
    protected void mouseClicked(int x,int y,int button)throws IOException{
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
        fontRenderer.drawString(net.minecraft.client.resources.I18n.format(tile.getBlockType().getUnlocalizedName()+".name"),guiLeft+12,guiTop+10,0xFFFFFF);
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
