package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneScreenPolicy;
import com.vandorlabs.ui.DialogInput;
import com.vandorlabs.ui.RedstoneDialogLayout;
import com.vandorlabs.ui.RedstoneScreenOverlay;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native widgets over the same bounded row editor used by Forge. */
public final class RedstoneConfigurationScreen extends AbstractContainerScreen<ScreenMenu> {
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private final List<Integer> kinds=new ArrayList<>(),mins=new ArrayList<>(),maxs=new ArrayList<>();
    private RedstoneDialogLayout panel;
    private EditBox titleField,labelField,channelField;
    private String heading;
    private NativeMaterialPicker housing;
    private int selected=-1,scroll,texture;
    private boolean materials;
    private List<RedstoneDialogLayout.Control> controls=List.of();
    private final Map<Integer,Button> buttons=new HashMap<>();
    public RedstoneConfigurationScreen(ScreenMenu menu,Inventory inventory,Component ignored){
        super(menu,inventory,Component.literal("Programmable Viewscreen"));heading=menu.tile.redstone().title();texture=menu.tile.data().housingTexture;
        for(var row:menu.tile.redstone().rows()){labels.add(row.label);channels.add(row.channels);kinds.add(row.pulse?2:row.slider?1:0);mins.add(row.min);maxs.add(row.max);}if(!labels.isEmpty())selected=0;
    }
    private EditBox field(com.vandorlabs.ui.ProgrammableDialogLayout.Button bounds,int limit){var field=new EditBox(font,bounds.x,bounds.y,bounds.width,bounds.height,Component.empty());field.setMaxLength(limit);addRenderableWidget(field);return field;}
    @Override protected void init(){
        if(labelField!=null)store();super.init();clearWidgets();buttons.clear();panel=new RedstoneDialogLayout(width,height,true);
        titleField=field(panel.title(),RedstoneScreenPolicy.MAX_TITLE);titleField.setValue(heading);titleField.setFilter(text->font.width(text.trim())<=112||font.width(text)<=font.width(titleField.getValue()));
        labelField=field(panel.label(),RedstoneScreenPolicy.MAX_LABEL);channelField=field(panel.channels(),ChannelList.MAX_TEXT_LENGTH);channelField.setFilter(text->text.matches("[0-9, ]*"));
        NativeMaterialPicker previous=housing;housing=new NativeMaterialPicker(panel.left+12,panel.top+54,panel.width-188,Math.max(2,(panel.height-88)/22),texture,value->texture=value);housing.keepView(previous);
        refreshControls();for(var control:controls){var b=control.bounds;Button button=Button.builder(Component.literal(b.label),ignored->action(b.id,1)).bounds(b.x,b.y,b.width,b.height).build();buttons.put(b.id,button);addRenderableWidget(button);}load();refresh();
    }
    private int kind(){return selected<0?0:kinds.get(selected);}
    private void refreshControls(){controls=panel.controls(materials,selected,labels.size(),RedstoneScreenPolicy.MAX_ROWS,kind(),selected<0?5:mins.get(selected),selected<0?15:maxs.get(selected));}
    private void refresh(){refreshControls();for(var control:controls){Button button=buttons.get(control.bounds.id);button.visible=control.visible;button.active=control.enabled;button.setMessage(Component.literal(control.bounds.label));}titleField.visible=!materials;labelField.visible=!materials&&selected>=0;channelField.visible=!materials&&selected>=0;}
    private boolean store(){
        boolean validTitle=RedstoneScreenPolicy.validTitle(titleField.getValue())&&font.width(titleField.getValue().trim())<=RedstoneScreenOverlay.TITLE_WIDTH;titleField.setTextColor(validTitle?0xE0E0E0:0xFF7777);if(!validTitle)return false;heading=titleField.getValue().trim();if(selected<0)return true;
        ChannelList list=ChannelList.parse(channelField.getValue());channelField.setTextColor(list==null?0xFF7777:0xE0E0E0);
        boolean label=RedstoneScreenPolicy.validLabel(labelField.getValue())&&font.width(labelField.getValue().trim())<=(kind()==1?50:86)&&(kind()!=1||labelField.getValue().length()<=11);labelField.setTextColor(label?0xE0E0E0:0xFF7777);if(!label||list==null)return false;labels.set(selected,labelField.getValue().trim());channels.set(selected,list);return true;
    }
    private void load(){labelField.setFilter(text->true);labelField.setValue(selected<0?"":labels.get(selected));labelField.setFilter(text->font.width(text.trim())<=(kind()==1?50:86)||font.width(text)<=font.width(labelField.getValue()));channelField.setValue(selected<0?"0":channels.get(selected).toString());}
    private void send(){if(!store())return;List<RedstoneConfigurationPayload.Row> rows=new ArrayList<>();for(int i=0;i<labels.size();i++)rows.add(new RedstoneConfigurationPayload.Row(labels.get(i),channels.get(i),kinds.get(i),mins.get(i),maxs.get(i)));PacketDistributor.sendToServer(new RedstoneConfigurationPayload(menu.containerId,menu.pos,heading,List.copyOf(rows),texture));onClose();}
    private void action(int id,int direction){
        if(id==3&&selected>=0){labels.remove(selected);channels.remove(selected);kinds.remove(selected);mins.remove(selected);maxs.remove(selected);selected=Math.min(selected,labels.size()-1);scroll=Math.min(scroll,Math.max(0,labels.size()-panel.visibleRows()));load();refresh();return;}
        if(id==7&&selected>=0){if(!store())return;kinds.set(selected,RedstoneDialogLayout.nextKind(kind(),direction));if(kind()==1)labels.set(selected,RedstoneDialogLayout.sliderLabel(labels.get(selected),font::width));load();refresh();return;}
        if(id==8&&selected>=0){mins.set(selected,RedstoneDialogLayout.nextLow(mins.get(selected),maxs.get(selected),direction));refresh();return;}
        if(id==9&&selected>=0){maxs.set(selected,RedstoneDialogLayout.nextHigh(mins.get(selected),maxs.get(selected),direction));refresh();return;}
        if(!store())return;
        if(id<2){materials=id==1;titleField.setFocused(false);labelField.setFocused(false);channelField.setFocused(false);refresh();}
        else if(id==2&&labels.size()<RedstoneScreenPolicy.MAX_ROWS){labels.add("Item "+(labels.size()+1));channels.add(ChannelList.EMPTY);kinds.add(0);mins.add(5);maxs.add(15);selected=labels.size()-1;scroll=Math.max(0,selected-panel.visibleRows()+1);load();refresh();setFocused(labelField);labelField.setFocused(true);}
        else if(id==5||id==6){int next=RedstoneDialogLayout.moveRows(labels,channels,selected,id==5?-1:1);if(next!=selected){Collections.swap(kinds,selected,next);Collections.swap(mins,selected,next);Collections.swap(maxs,selected,next);selected=next;scroll=Math.max(Math.min(scroll,selected),selected-panel.visibleRows()+1);load();refresh();}}
        else if(id==4)send();
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1)for(var control:controls)if(control.visible&&control.enabled&&control.bounds.id>=7&&control.bounds.id<=9&&control.bounds.contains(x,y)){action(control.bounds.id,-1);return true;}
        if(materials&&housing.click(x,y,button))return true;
        if(!materials&&x>=panel.left+12&&x<panel.left+panel.width-180&&y>=panel.top+panel.rowListTop&&y<panel.top+panel.rowListTop+panel.visibleRows()*20){int next=scroll+(int)(y-panel.top-panel.rowListTop)/20;if(next<labels.size()&&store()){selected=next;load();refresh();return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(materials&&housing.drag(y))return true;return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){housing.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(materials)return housing.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);scroll=DialogInput.wheel(scroll,Math.max(0,labels.size()-panel.visibleRows()),(int)Math.signum(vertical));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){send();return true;}boolean handled=super.keyPressed(key,scan,modifiers);if(handled&&!materials)store();return handled;}
    @Override public boolean charTyped(char character,int modifiers){boolean handled=super.charTyped(character,modifiers);if(handled&&!materials)store();return handled;}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
        graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF19232C);graphics.drawString(font,title,panel.left+12,panel.top+10,0xFFFFFF,false);
        if(materials){housing.draw(graphics,font,x,y);return;}
        for(int i=0;i<panel.visibleRows()&&scroll+i<labels.size();i++){int row=scroll+i,yy=panel.top+panel.rowListTop+i*20;graphics.fill(panel.left+12,yy,panel.left+panel.width-180,yy+18,row==selected?0xFF365C70:0xFF22313E);graphics.drawString(font,font.plainSubstrByWidth(labels.get(row),panel.width-204),panel.left+18,yy+5,0xE0ECF5,false);}
        graphics.drawString(font,"Header",panel.left+12,panel.top+55,0xDAE8F0,false);graphics.fill(panel.left+12,panel.top+76,panel.left+panel.width-180,panel.top+77,0xFF365366);
        graphics.drawString(font,font.plainSubstrByWidth("Text is limited to screen width.",panel.width-192),panel.left+12,panel.top+panel.height-14,0xADBECA,false);
        if(selected>=0){graphics.drawString(font,"Label",panel.controlsX,panel.top+94,0xDAE8F0,false);graphics.drawString(font,"Channels (0 = none)",panel.controlsX,panel.top+127,0xDAE8F0,false);}else graphics.drawWordWrap(font,Component.literal("Add an item to create a control."),panel.controlsX,panel.top+105,148,0xADBECA);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
