package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.client.config.GuiSlider;
import java.io.IOException;

public final class GuiAutopilot extends GuiContainer {
    private final TileEntityAutopilot tile;
    private final boolean waypoint;
    private GuiTextField channels,marker,meta,delay;
    private int trigger,stop,depart;
    private boolean auto,direct;
    private GuiSlider height;
    private static final String[] MODES={"Block","Taxi","Hover"},TRIGGERS={"Disabled","On","Off"};
    public GuiAutopilot(TileEntityAutopilot tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;waypoint=tile instanceof TileEntityWaypoint;trigger=tile.trigger;xSize=300;ySize=waypoint?270:205;if(waypoint){TileEntityWaypoint w=(TileEntityWaypoint)tile;stop=w.stopMode;depart=w.departMode;auto=w.autoDepart;direct=w.directSignal;}}
    public void initGui(){super.initGui();buttonList.clear();channels=new GuiTextField(1,fontRenderer,guiLeft+130,guiTop+32,154,18);ChannelFields.configure(channels);channels.setText(tile.getRedstoneChannels().toString());
        if(!waypoint){marker=new GuiTextField(2,fontRenderer,guiLeft+14,guiTop+102,230,18);marker.setMaxStringLength(128);marker.setText(tile.marker);meta=new GuiTextField(3,fontRenderer,guiLeft+250,guiTop+102,34,18);meta.setText(Integer.toString(tile.markerMeta));buttonList.add(new GuiButton(0,guiLeft+14,guiTop+58,270,20,"Redstone: "+TRIGGERS[trigger]));buttonList.add(new GuiButton(2,guiLeft+14,guiTop+128,270,20,"Use held block as route marker"));}
        else{TileEntityWaypoint w=(TileEntityWaypoint)tile;delay=new GuiTextField(4,fontRenderer,guiLeft+180,guiTop+85,104,18);delay.setText(Integer.toString(w.delay));buttonList.add(new GuiButton(3,guiLeft+14,guiTop+58,270,20,"Auto departure: "+auto));buttonList.add(new GuiButton(4,guiLeft+14,guiTop+111,270,20,"Direct signal to vehicle: "+direct));buttonList.add(new GuiButton(5,guiLeft+14,guiTop+137,270,20,"Stopped mode: "+MODES[stop]));buttonList.add(new GuiButton(6,guiLeft+14,guiTop+163,270,20,"Departure mode: "+MODES[depart]));height=new GuiSlider(7,guiLeft+14,guiTop+189,270,20,"Hover height: "," blocks",0,16,w.hoverHeight,false,true);buttonList.add(height);}
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+ySize-32,270,20,"Done"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    protected void actionPerformed(GuiButton b){switch(b.id){case 0:trigger=GuiOptionCycle.next(trigger,3);b.displayString="Redstone: "+TRIGGERS[trigger];break;case 1:submit();break;case 2:ItemStack held=mc.player.getHeldItemMainhand();if(!(held.getItem() instanceof ItemBlock))held=mc.player.getHeldItemOffhand();if(held.getItem() instanceof ItemBlock){Block block=((ItemBlock)held.getItem()).getBlock();marker.setText(block.getRegistryName().toString());meta.setText(Integer.toString(held.getMetadata()&15));}break;case 3:auto=!auto;b.displayString="Auto departure: "+auto;break;case 4:direct=!direct;b.displayString="Direct signal to vehicle: "+direct;break;case 5:stop=GuiOptionCycle.next(stop,3);b.displayString="Stopped mode: "+MODES[stop];break;case 6:depart=GuiOptionCycle.next(depart,1,2);b.displayString="Departure mode: "+MODES[depart];break;default:break;}}
    private void submit(){ChannelList list=ChannelFields.parse(channels);if(list==null)return;NBTTagCompound n=new NBTTagCompound();com.vandorlabs.redstone.ChannelData.write(n,list);
        try{if(waypoint){int ticks=Integer.parseInt(delay.getText());if(ticks<0||ticks>72000)return;n.setInteger("WaypointDelay",ticks);n.setBoolean("WaypointAuto",auto);n.setBoolean("WaypointDirect",direct);n.setInteger("WaypointStop",stop);n.setInteger("WaypointDepart",depart);n.setInteger("WaypointHeight",height.getValueInt());}
            else{int value=Integer.parseInt(meta.getText());if(value<0||value>15||Block.getBlockFromName(marker.getText())==null)return;n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",marker.getText());n.setInteger("RouteMarkerMeta",value);}
        }catch(IllegalArgumentException invalid){return;}
        PacketHandler.INSTANCE.sendToServer(new MessageAutopilot(tile.getPos(),n));mc.player.closeScreen();
    }
    protected void keyTyped(char c,int key)throws IOException{if(key==org.lwjgl.input.Keyboard.KEY_RETURN){submit();return;}boolean used=channels.textboxKeyTyped(c,key);if(waypoint)used|=delay.textboxKeyTyped(c,key);else{used|=marker.textboxKeyTyped(c,key);used|=meta.textboxKeyTyped(c,key);}if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5,6))return;super.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);if(waypoint)delay.mouseClicked(x,y,button);else{marker.mouseClicked(x,y,button);meta.mouseClicked(x,y,button);}}
    public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(waypoint?"Waypoint":"Autopilot",14,12,0xFFFFFF);fontRenderer.drawString("Channels (0 = none)",14,37,0xDDDDDD);fontRenderer.drawString(waypoint?"Delay (ticks; 20 = 1s)":"Route block ID / metadata",14,waypoint?90:88,0xDDDDDD);if(!waypoint)fontRenderer.drawString("A pulse runs until the next waypoint.",14,156,0xAAAAAA);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channels.drawTextBox();if(waypoint)delay.drawTextBox();else{marker.drawTextBox();meta.drawTextBox();}}
}
