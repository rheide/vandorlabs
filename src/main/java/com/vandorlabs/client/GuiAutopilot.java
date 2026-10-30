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
    private String markerId;private int markerMeta;
    private int trigger,stop,depart;
    private boolean auto,direct;
    private GuiSlider height;private InventorySamplePicker routePicker;private int page;private boolean closeAfterSubmit=true;
    private static final String[] MODES={"Park","Taxi","Hover"};
    public GuiAutopilot(TileEntityAutopilot tile){super(new ContainerRedstoneChannel(tile));this.tile=tile;markerId=tile.marker;markerMeta=tile.markerMeta;waypoint=tile instanceof TileEntityWaypoint;trigger=tile.trigger;xSize=300;ySize=waypoint?282:238;if(waypoint){TileEntityWaypoint w=(TileEntityWaypoint)tile;stop=w.stopMode;depart=w.departMode;auto=w.autoDepart;direct=w.directSignal;}}
    public void initGui(){String delayDraft=delay==null?null:delay.getText();ySize=Math.min(waypoint?282:238,super.height-12);super.initGui();buttonList.clear();routePicker=null;String channelDraft=channels==null?ChannelFields.text(tile.getRedstoneChannels()):channels.getText();channels=new GuiTextField(1,fontRenderer,guiLeft+14,guiTop+68,xSize-28,18);ChannelFields.configure(channels);channels.setText(channelDraft);channels.setVisible(page==1&&!waypoint);channels.setFocused(false);
        int tabWidth=(xSize-32)/2;
        buttonList.add(new DialogTabButton(100,guiLeft+14,guiTop+32,tabWidth,20,"General"));
        buttonList.add(new DialogTabButton(101,guiLeft+18+tabWidth,guiTop+32,tabWidth,20,"Redstone"));
        for(GuiButton button:buttonList)button.enabled=button.id!=100+page;
        if(page==1&&waypoint)buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(90,guiLeft+14,guiTop+(waypoint?70:130),xSize-28,20,"Transmitter..."));
        if(!waypoint)buttonList.add(new RedstoneActivationButton(0,guiLeft+14,guiTop+96,xSize-28,trigger));
        else{TileEntityWaypoint w=(TileEntityWaypoint)tile;delay=new GuiTextField(4,fontRenderer,guiLeft+180,guiTop+96,104,18);delay.setText(delayDraft==null?Integer.toString(w.delay):delayDraft);delay.setVisible(page==0);delay.setFocused(false);buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(3,guiLeft+14,guiTop+58,xSize-28,20,"Auto departure: "+(auto?"On":"Off")));buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(4,guiLeft+14,guiTop+111,xSize-28,20,"Direct signal to vehicle: "+(direct?"On":"Off")));buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(5,guiLeft+14,guiTop+137,xSize-28,20,"Stopped mode: "+MODES[stop]));buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(6,guiLeft+14,guiTop+163,xSize-28,20,"Departure mode: "+MODES[depart]));height=new GuiSlider(7,guiLeft+14,guiTop+189,xSize-28,20,"Hover height: "," blocks",0,16,w.hoverHeight,false,true);buttonList.add(height);}
        for(GuiButton button:buttonList)if(button.id<100&&button.id!=90){boolean redstone=button.id==0||button.id==4;button.visible=redstone?page==1:page==0;if(button.id==0||button.id==4)button.y=guiTop+96;if(waypoint){if(button.id==4)button.y=guiTop+140;if(button.id==3)button.y=guiTop+64;if(button.id==5)button.y=guiTop+(ySize<260?120:126);if(button.id==6)button.y=guiTop+(ySize<260?144:152);if(button.id==7)button.y=guiTop+(ySize<260?168:178);}}
        if(!waypoint&&page==0)routePicker=InventorySamplePicker.hotbar(guiLeft+(xSize-162)/2,guiTop+112,GuiAutopilot::routeBlock,stack->{Block block=((ItemBlock)stack.getItem()).getBlock();markerId=block.getRegistryName().toString();markerMeta=stack.getMetadata();closeAfterSubmit=false;submit();closeAfterSubmit=true;});
        buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(1,guiLeft+14,guiTop+ySize-32,xSize-28,20,"Done"));org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    protected void actionPerformed(GuiButton b){switch(b.id){case 100:case 101:page=b.id-100;initGui();return;case 90:closeAfterSubmit=false;submit();closeAfterSubmit=true;PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTransmitterSlot.open(tile.getPos(),0));return;case 0:trigger=GuiOptionCycle.next(trigger,3);((RedstoneActivationButton)b).setMode(trigger);break;case 1:submit();break;case 3:auto=!auto;b.displayString="Auto departure: "+(auto?"On":"Off");break;case 4:direct=!direct;b.displayString="Direct signal to vehicle: "+(direct?"On":"Off");break;case 5:stop=GuiOptionCycle.next(stop,3);b.displayString="Stopped mode: "+MODES[stop];break;case 6:depart=GuiOptionCycle.next(depart,1,2);b.displayString="Departure mode: "+MODES[depart];break;default:break;}}
    private static boolean routeBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock&&((ItemBlock)stack.getItem()).getBlock()!=net.minecraft.init.Blocks.AIR&&stack.getMetadata()>=0&&stack.getMetadata()<=15;}
    private ItemStack markerStack(){Block block=Block.getBlockFromName(markerId);if(block==null||block==net.minecraft.init.Blocks.AIR)return ItemStack.EMPTY;return new ItemStack(block,1,markerMeta);}
    private void submit(){ChannelList list=ChannelFields.parse(channels);if(list==null)return;NBTTagCompound n=new NBTTagCompound();com.vandorlabs.redstone.ChannelData.write(n,list);
        try{if(waypoint){int ticks=Integer.parseInt(delay.getText());if(ticks<0||ticks>72000)return;n.setInteger("WaypointDelay",ticks);n.setBoolean("WaypointAuto",auto);n.setBoolean("WaypointDirect",direct);n.setInteger("WaypointStop",stop);n.setInteger("WaypointDepart",depart);n.setInteger("WaypointHeight",height.getValueInt());}
            else{int value=markerMeta;if(value<0||value>15||Block.getBlockFromName(markerId)==null)return;n.setInteger("AutopilotTrigger",trigger);n.setString("RouteMarker",markerId);n.setInteger("RouteMarkerMeta",value);}
        }catch(IllegalArgumentException invalid){return;}
        PacketHandler.INSTANCE.sendToServer(new MessageAutopilot(tile.getPos(),n));if(closeAfterSubmit)mc.player.closeScreen();
    }
    protected void keyTyped(char c,int key)throws IOException{if(key==org.lwjgl.input.Keyboard.KEY_RETURN){submit();return;}boolean used=page==1&&!waypoint&&channels.textboxKeyTyped(c,key);if(waypoint&&page==0)used|=delay.textboxKeyTyped(c,key);if(!used)super.keyTyped(c,key);}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(page==0&&routePicker!=null&&routePicker.click(x,y,button))return;if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,3,4,5,6))return;super.mouseClicked(x,y,button);if(page==1&&!waypoint)channels.mouseClicked(x,y,button);if(waypoint&&page==0)delay.mouseClicked(x,y,button);}
    public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);DialogHeader.logo(guiLeft,guiTop,xSize);}
    protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(waypoint?"Waypoint":"Autopilot",14,12,0xFFFFFF);if(page==1&&!waypoint)fontRenderer.drawString("Receive channels",14,54,0xDDDDDD);if(page==0)fontRenderer.drawString(waypoint?"Delay (ticks; 20 = 1s)":"Route marker block",14,waypoint?101:64,0xDDDDDD);if(page==0&&!waypoint)fontRenderer.drawString("A pulse runs until the next waypoint.",14,156,0xAAAAAA);}
    public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();channels.drawTextBox();if(page==0&&routePicker!=null)routePicker.draw(x,y);if(waypoint)delay.drawTextBox();else if(page==0){ItemStack stack=markerStack();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();itemRender.renderItemAndEffectIntoGUI(stack,guiLeft+14,guiTop+80);net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();DialogOverlay.prepare();fontRenderer.drawString(fontRenderer.trimStringToWidth(stack.isEmpty()?markerId:stack.getDisplayName(),240),guiLeft+38,guiTop+80,0xFFFFFF);fontRenderer.drawString(fontRenderer.trimStringToWidth(markerId+" : "+markerMeta,240),guiLeft+38,guiTop+91,0xAAAAAA);}}
}
