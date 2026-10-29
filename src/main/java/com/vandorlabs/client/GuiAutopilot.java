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
    private GuiSlider height;private InventorySamplePicker routePicker;private int page;private boolean closeAfterSubmit=true;
    private static final String[] MODES={"Park","Taxi","Hover"};
    public GuiAutopilot(TileEntityAutopilot tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;markerId=tile.marker;markerMeta=tile.markerMeta;waypoint=tile instanceof TileEntityWaypoint;trigger=tile.trigger;xSize=300;ySize=waypoint?282:238;if(waypoint){TileEntityWaypoint w=(TileEntityWaypoint)tile;stop=w.stopMode;depart=w.departMode;auto=w.autoDepart;direct=w.directSignal;}}
    public void initGui(){String delayDraft=delay==null?null:delay.getText();super.initGui();buttonList.clear();picking=false;String channelDraft=channels==null?ChannelFields.text(tile.getRedstoneChannels()):channels.getText();channels=new GuiTextField(1,fontRenderer,guiLeft+130,guiTop+72,154,18);ChannelFields.configure(channels);channels.setText(channelDraft);channels.setVisible(page==1&&!waypoint);channels.setFocused(false);
        buttonList.add(new DialogTabButton(100,guiLeft+14,guiTop+32,waypoint?132:86,20,"General"));buttonList.add(new DialogTabButton(101,guiLeft+(waypoint?152:194),guiTop+32,waypoint?132:86,20,"Redstone"));if(!waypoint)buttonList.add(new DialogTabButton(102,guiLeft+104,guiTop+32,90,20,"Route"));for(GuiButton button:buttonList)button.enabled=button.id!=100+page;
        if(page==1&&waypoint)buttonList.add(new GuiButton(90,guiLeft+14,guiTop+(waypoint?70:130),270,20,"Transmitter..."));
        if(!waypoint){buttonList.add(new RedstoneActivationButton(0,guiLeft+14,guiTop+58,270,trigger));buttonList.add(new GuiButton(2,guiLeft+14,guiTop+128,270,20,"Choose route block from inventory..."));}
        else{TileEntityWaypoint w=(TileEntityWaypoint)tile;delay=new GuiTextField(4,fontRenderer,guiLeft+180,guiTop+96,104,18);delay.setText(delayDraft==null?Integer.toString(w.delay):delayDraft);delay.setVisible(page==0);delay.setFocused(false);buttonList.add(new GuiButton(3,guiLeft+14,guiTop+58,270,20,"Auto departure: "+auto));buttonList.add(new GuiButton(4,guiLeft+14,guiTop+111,270,20,"Direct signal to vehicle: "+direct));buttonList.add(new GuiButton(5,guiLeft+14,guiTop+137,270,20,"Stopped mode: "+MODES[stop]));buttonList.add(new GuiButton(6,guiLeft+14,guiTop+163,270,20,"Departure mode: "+MODES[depart]));height=new GuiSlider(7,guiLeft+14,guiTop+189,270,20,"Hover height: "," blocks",0,16,w.hoverHeight,false,true);buttonList.add(height);}
        for(GuiButton button:buttonList)if(button.id<100&&button.id!=90){boolean redstone=button.id==0||button.id==4;button.visible=redstone?page==1:page==0;if(button.id==0||button.id==4)button.y=guiTop+102;if(waypoint){if(button.id==4)button.y=guiTop+126;if(button.id==3)button.y=guiTop+64;if(button.id==5)button.y=guiTop+126;if(button.id==6)button.y=guiTop+152;if(button.id==7)button.y=guiTop+178;}}
        if(page==2)routePicker=new InventorySamplePicker(guiLeft+14,guiTop+64,xSize-28,ySize-108,GuiAutopilot::routeBlock,stack->{Block block=((ItemBlock)stack.getItem()).getBlock();markerId=block.getRegistryName().toString();markerMeta=stack.getMetadata();closeAfterSubmit=false;submit();closeAfterSubmit=true;},null);
        buttonList.add(new GuiButton(1,guiLeft+14,guiTop+ySize-32,270,20,"Done"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    protected void actionPerformed(GuiButton b){switch(b.id){case 100:case 101:case 102:page=b.id-100;initGui();return;case 90:closeAfterSubmit=false;submit();closeAfterSubmit=true;PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;case 0:trigger=GuiOptionCycle.next(trigger,3);((RedstoneActivationButton)b).setMode(trigger);break;case 1:submit();break;case 2:page=2;initGui();break;case 3:auto=!auto;b.displayString="Auto departure: "+auto;break;case 4:direct=!direct;b.displayString="Direct signal to vehicle: "+direct;break;case 5:stop=GuiOptionCycle.next(stop,3);b.displayString="Stopped mode: "+MODES[stop];break;case 6:depart=GuiOptionCycle.next(depart,1,2);b.displayString="Departure mode: "+MODES[depart];break;default:break;}}
    private static boolean routeBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock&&((ItemBlock)stack.getItem()).getBlock()!=net.minecraft.init.Blocks.AIR&&stack.getMetadata()>=0&&stack.getMetadata()<=15;}
    private ItemStack markerStack(){Block block=Block.getBlockFromName(markerId);if(block==null||block==net.minecraft.init.Blocks.AIR)return ItemStack.EMPTY;return new ItemStack(block,1,markerMeta);}
    private void submit(){ChannelList list=ChannelFields.parse(channels);if(list==null)return;NBTTagCompound n=new NBTTagCompound();com.vandorlabs.redstone.ChannelData.write(n,list);
        try{if(waypoint){int ticks=Integer.parseInt(delay.getText());if(ticks<0||ticks>72000)return;n.setInteger("WaypointDelay",ticks);n.setBoolean("WaypointAuto",auto);n.setBoolean("WaypointDirect",direct);n.setInteger("WaypointStop",stop);n.setInteger("WaypointDepart",depart);n.setInteger("WaypointHeight",height.getValueInt());}
            else{int value=markerMeta;if(value<0||value>15||Block.getBlockFromName(markerId)==null)return;n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",markerId);n.setInteger("RouteMarkerMeta",value);}
        }catch(IllegalArgumentException invalid){return;}
        PacketHandler.INSTANCE.sendToServer(new MessageAutopilot(tile.getPos(),n));if(closeAfterSubmit)mc.player.closeScreen();
    }
    protected void keyTyped(char c,int key)throws IOException{if(key==org.lwjgl.input.Keyboard.KEY_RETURN){submit();return;}boolean used=page==1&&!waypoint&&channels.textboxKeyTyped(c,key);if(waypoint&&page==0)used|=delay.textboxKeyTyped(c,key);if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(page==2&&routePicker!=null&&routePicker.click(x,y,button))return;if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5,6))return;super.mouseClicked(x,y,button);if(page==1&&!waypoint)channels.mouseClicked(x,y,button);if(waypoint&&page==0)delay.mouseClicked(x,y,button);}
    public void onGuiClosed(){if(!picking)super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);DialogHeader.logo(guiLeft,guiTop,xSize);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(waypoint?"Waypoint":"Autopilot",14,12,0xFFFFFF);if(page==1&&!waypoint)fontRenderer.drawString("Receive channels",14,77,0xDDDDDD);if(page==0)fontRenderer.drawString(waypoint?"Delay (ticks; 20 = 1s)":"Route marker block",14,waypoint?101:88,0xDDDDDD);if(page==0&&!waypoint)fontRenderer.drawString("A pulse runs until the next waypoint.",14,156,0xAAAAAA);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);net.minecraft.client.renderer.GlStateManager.disableLighting();channels.drawTextBox();if(page==2&&routePicker!=null)routePicker.draw(x,y);if(waypoint)delay.drawTextBox();else if(page==0){ItemStack stack=markerStack();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();itemRender.renderItemAndEffectIntoGUI(stack,guiLeft+14,guiTop+102);net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();fontRenderer.drawString(fontRenderer.trimStringToWidth(stack.isEmpty()?markerId:stack.getDisplayName(),240),guiLeft+38,guiTop+102,0xFFFFFF);fontRenderer.drawString(fontRenderer.trimStringToWidth(markerId+" : "+markerMeta,240),guiLeft+38,guiTop+113,0xAAAAAA);}}
}
