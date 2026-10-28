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
    private GuiTextField channels,delay;
    private String markerId;private int markerMeta;private boolean picking;
    private int trigger,stop,depart;
    private boolean auto,direct;
    private GuiSlider height;
    private static final String[] MODES={"Park","Taxi","Hover"},TRIGGERS={"Disabled","On","Off"};
    public GuiAutopilot(TileEntityAutopilot tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;markerId=tile.marker;markerMeta=tile.markerMeta;waypoint=tile instanceof TileEntityWaypoint;trigger=tile.trigger;xSize=300;ySize=waypoint?270:205;if(waypoint){TileEntityWaypoint w=(TileEntityWaypoint)tile;stop=w.stopMode;depart=w.departMode;auto=w.autoDepart;direct=w.directSignal;}}
    public void initGui(){super.initGui();buttonList.clear();picking=false;String channelDraft=channels==null?tile.getRedstoneChannels().toString():channels.getText();channels=new GuiTextField(1,fontRenderer,guiLeft+130,guiTop+32,154,18);ChannelFields.configure(channels);channels.setText(channelDraft);if(waypoint){channels.setVisible(false);buttonList.add(new GuiButton(90,guiLeft+130,guiTop+30,154,20,"Transmitter..."));}else buttonList.add(new GuiButton(90,guiLeft+130,guiTop+7,154,18,"Transmitter..."));
        if(!waypoint){buttonList.add(new GuiButton(0,guiLeft+14,guiTop+58,270,20,"Redstone: "+TRIGGERS[trigger]));buttonList.add(new GuiButton(2,guiLeft+14,guiTop+128,270,20,"Choose route block from inventory..."));}
        else{TileEntityWaypoint w=(TileEntityWaypoint)tile;delay=new GuiTextField(4,fontRenderer,guiLeft+180,guiTop+85,104,18);delay.setText(Integer.toString(w.delay));buttonList.add(new GuiButton(3,guiLeft+14,guiTop+58,270,20,"Auto departure: "+auto));buttonList.add(new GuiButton(4,guiLeft+14,guiTop+111,270,20,"Direct signal to vehicle: "+direct));buttonList.add(new GuiButton(5,guiLeft+14,guiTop+137,270,20,"Stopped mode: "+MODES[stop]));buttonList.add(new GuiButton(6,guiLeft+14,guiTop+163,270,20,"Departure mode: "+MODES[depart]));height=new GuiSlider(7,guiLeft+14,guiTop+189,270,20,"Hover height: "," blocks",0,16,w.hoverHeight,false,true);buttonList.add(height);}
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+ySize-32,270,20,"Done"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    protected void actionPerformed(GuiButton b){switch(b.id){case 90:PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;case 0:trigger=GuiOptionCycle.next(trigger,3);b.displayString="Redstone: "+TRIGGERS[trigger];break;case 1:submit();break;case 2:picking=true;mc.displayGuiScreen(new GuiCustomTexture(this,"Autopilot route block","Use route block",markerStack(),GuiAutopilot::routeBlock,stack->{Block block=((ItemBlock)stack.getItem()).getBlock();markerId=block.getRegistryName().toString();markerMeta=stack.getMetadata();}));break;case 3:auto=!auto;b.displayString="Auto departure: "+auto;break;case 4:direct=!direct;b.displayString="Direct signal to vehicle: "+direct;break;case 5:stop=GuiOptionCycle.next(stop,3);b.displayString="Stopped mode: "+MODES[stop];break;case 6:depart=GuiOptionCycle.next(depart,1,2);b.displayString="Departure mode: "+MODES[depart];break;default:break;}}
    private static boolean routeBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock&&((ItemBlock)stack.getItem()).getBlock()!=net.minecraft.init.Blocks.AIR&&stack.getMetadata()>=0&&stack.getMetadata()<=15;}
    private ItemStack markerStack(){Block block=Block.getBlockFromName(markerId);if(block==null||block==net.minecraft.init.Blocks.AIR)return ItemStack.EMPTY;return new ItemStack(block,1,markerMeta);}
    private void submit(){ChannelList list=ChannelFields.parse(channels);if(list==null)return;NBTTagCompound n=new NBTTagCompound();com.vandorlabs.redstone.ChannelData.write(n,list);
        try{if(waypoint){int ticks=Integer.parseInt(delay.getText());if(ticks<0||ticks>72000)return;n.setInteger("WaypointDelay",ticks);n.setBoolean("WaypointAuto",auto);n.setBoolean("WaypointDirect",direct);n.setInteger("WaypointStop",stop);n.setInteger("WaypointDepart",depart);n.setInteger("WaypointHeight",height.getValueInt());}
            else{int value=markerMeta;if(value<0||value>15||Block.getBlockFromName(markerId)==null)return;n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",markerId);n.setInteger("RouteMarkerMeta",value);}
        }catch(IllegalArgumentException invalid){return;}
        PacketHandler.INSTANCE.sendToServer(new MessageAutopilot(tile.getPos(),n));mc.player.closeScreen();
    }
    protected void keyTyped(char c,int key)throws IOException{if(key==org.lwjgl.input.Keyboard.KEY_RETURN){submit();return;}boolean used=channels.textboxKeyTyped(c,key);if(waypoint)used|=delay.textboxKeyTyped(c,key);if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5,6))return;super.mouseClicked(x,y,button);channels.mouseClicked(x,y,button);if(waypoint)delay.mouseClicked(x,y,button);}
    public void onGuiClosed(){if(!picking)super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(waypoint?"Waypoint":"Autopilot",14,12,0xFFFFFF);if(!waypoint)fontRenderer.drawString("Channels (0 = none)",14,37,0xDDDDDD);fontRenderer.drawString(waypoint?"Delay (ticks; 20 = 1s)":"Route marker block",14,waypoint?90:88,0xDDDDDD);if(!waypoint)fontRenderer.drawString("A pulse runs until the next waypoint.",14,156,0xAAAAAA);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channels.drawTextBox();if(waypoint)delay.drawTextBox();else{ItemStack stack=markerStack();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();itemRender.renderItemAndEffectIntoGUI(stack,guiLeft+14,guiTop+102);net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();fontRenderer.drawString(fontRenderer.trimStringToWidth(stack.isEmpty()?markerId:stack.getDisplayName(),240),guiLeft+38,guiTop+102,0xFFFFFF);fontRenderer.drawString(fontRenderer.trimStringToWidth(markerId+" : "+markerMeta,240),guiLeft+38,guiTop+113,0xAAAAAA);}}
}
