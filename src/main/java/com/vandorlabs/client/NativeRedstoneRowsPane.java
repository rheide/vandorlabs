package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.*;
import java.util.*;

/** Editable Redstone artwork remains in the ordinary screen configuration container. */
final class NativeRedstoneRowsPane {
    private final GuiContainer host;private final TileEntityAnimatedScreenSelector tile;private final int slot;private final RedstoneScreenContents contents;private final RedstoneRowsPane rows;private final boolean header;private GuiTextField title;
    NativeRedstoneRowsPane(GuiContainer host,TileEntityAnimatedScreenSelector tile,int slot){this.host=host;this.tile=tile;this.slot=slot;contents=tile.redstoneScreen(slot);header=!com.vandorlabs.blocks.RedstoneScreenInteractions.half(tile.getBlockType(),slot);NBTTagList values=contents.rowConfiguration();for(int i=0;i<values.tagCount();i++){NBTTagCompound row=values.getCompoundTagAt(i);row.setInteger("Kind",row.getBoolean("Pulse")?2:row.getBoolean("Slider")?1:0);}rows=new RedstoneRowsPane(net.minecraft.client.Minecraft.getMinecraft().fontRenderer,contents.maxRows(),true,values,index->index<contents.rows().size()&&!TransmitterSlots.get(contents.rows().get(index)).isEmpty(),this::apply);}
    void init(List<GuiButton> buttons){int x=host.getGuiLeft()+12,y=host.getGuiTop()+54,w=host.getXSize()-24,h=host.getYSize()-104;String draft=title==null?contents.title():title.getText();rows.setListOffset(header?40:0);rows.layout(x,y,w,h,buttons);title=new GuiTextField(584,net.minecraft.client.Minecraft.getMinecraft().fontRenderer,x,y+12,Math.max(48,w-192),18);title.setMaxStringLength(RedstoneScreenContents.MAX_TITLE);title.setText(draft);title.setVisible(header);buttons.add(new GuiButton(580,x,host.getGuiTop()+host.getYSize()-26,Math.max(48,w-184),20,"Artwork"));}
    int selector(){return rows.selected()<0?-1:1+slot*8+rows.selected();}
    boolean action(GuiButton button){return rows.action(button);}
    boolean click(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(net.minecraft.client.Minecraft.getMinecraft(),rows.buttons(),x,y,button,this::action,604,605,606))return true;title.mouseClicked(x,y,button);rows.mouseClicked(x,y,button);return false;}
    boolean key(char c,int key){if(title.getVisible()&&title.textboxKeyTyped(c,key)){rows.save();return true;}return rows.keyTyped(c,key);}
    void wheel(int delta){rows.wheel(delta);}
    void update(){title.updateCursorCounter();rows.update();}
    void draw(){if(header){net.minecraft.client.Minecraft.getMinecraft().fontRenderer.drawString("Header",title.x,title.y-12,0xdae8f0);title.drawTextBox();}rows.draw();}
    boolean save(){return rows.save();}
    private void apply(NBTTagList values){String text=title==null?contents.title():title.getText();if(!RedstoneScreenContents.validTitle(text)||!RedstoneScreenText.fits(net.minecraft.client.Minecraft.getMinecraft().fontRenderer,text,RedstoneScreenText.TITLE_WIDTH))return;List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();List<Boolean> sliders=new ArrayList<>(),pulses=new ArrayList<>();List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();for(int i=0;i<values.tagCount();i++){NBTTagCompound row=values.getCompoundTagAt(i);labels.add(row.getString("Label"));channels.add(ChannelData.read(row,0));sliders.add(row.getInteger("Kind")==1);pulses.add(row.getInteger("Kind")==2);mins.add(row.getInteger("Min"));maxs.add(row.getInteger("Max"));}PacketHandler.INSTANCE.sendToServer(new MessageRedstoneScreen(tile.getPos(),slot,text,labels,channels,tile.getHousingTexture()).withSliders(sliders,mins,maxs).withPulses(pulses));}
}
