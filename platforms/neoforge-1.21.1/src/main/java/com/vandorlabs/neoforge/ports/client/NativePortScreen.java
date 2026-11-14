package com.vandorlabs.neoforge.ports.client;

import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact Ports/Redstone tabs use the same left/previous-right interactions as other editors. */
public final class NativePortScreen extends AbstractContainerScreen<NativePortMenu> {
    private final ResourcePortSettings.Face[] faces=new ResourcePortSettings.Face[6];
    private ResourcePortSettings.OnTexture artwork;private boolean pull;private int mode,page;
    private String throughput,channelText,error="";private EditBox rate,channels;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativePortScreen(NativePortMenu menu,Inventory inventory,Component title){super(menu,inventory,title);var config=menu.tile.configuration();for(int i=0;i<6;i++)faces[i]=config.settings().face(i);artwork=config.settings().onTexture;pull=config.settings().pullInputs;mode=config.redstoneMode();throughput=config.settings().throughput==0?"":Long.toString(config.settings().throughput);channelText=config.channels().toString();}
    public Button control(int id){return buttons.get(id);}
    public EditBox rateField(){return rate;}
    public EditBox channelField(){return channels;}
    public NativePortData.Configuration editingConfiguration(){capture();return draft();}
    @Override protected void init(){imageWidth=Math.min(360,width-12);imageHeight=228;super.init();rebuild();}
    private void button(int id,int x,int y,int w,String label){var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void capture(){if(rate!=null)throughput=rate.getValue();if(channels!=null)channelText=channels.getValue();}
    private void rebuild(){capture();clearWidgets();buttons.clear();rate=channels=null;int w=(imageWidth-30)/2;button(20,10,28,w,"General");button(21,20+w,28,w,"Redstone");buttons.get(20+page).active=false;if(page==0){String[] names={"Down","Up","North","South","West","East"};String[] modes={"Disabled","Input","Output"};for(int i=0;i<6;i++)button(i,10+(i%2)*(w+10),52+(i/2)*22,w,names[i]+": "+modes[faces[i].ordinal()]);rate=new EditBox(font,leftPos+10,topPos+130,imageWidth-20,18,Component.literal("Throughput"));rate.setMaxLength(19);rate.setValue(throughput);rate.setResponder(value->{throughput=value;validity();});addRenderableWidget(rate);button(6,10,152,imageWidth-20,"Pull from inputs: "+(pull?"On":"Off"));button(7,10,174,imageWidth-20,"On texture: "+new String[]{"Active redstone","Connected","Transferring"}[artwork.ordinal()]);}else{channels=new EditBox(font,leftPos+10,topPos+78,imageWidth-20,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(channelText);channels.setResponder(value->{channelText=value;validity();});addRenderableWidget(channels);button(8,10,108,imageWidth-20,"Activation: "+new String[]{"Always active","Redstone on","Redstone off"}[mode]);}button(9,(imageWidth-150)/2,202,150,"Done");validity();}
    private NativePortData.Configuration draft(){Long amount=ResourcePortSettings.parseThroughput(throughput);var bank=ChannelList.parse(channelText);if(amount==null||bank==null)throw new IllegalArgumentException("Invalid throughput or channels");return new NativePortData.Configuration(new ResourcePortSettings(faces,amount,pull,artwork),mode,bank);}
    private boolean validity(){try{draft();error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}if(buttons.containsKey(9))buttons.get(9).active=error.isEmpty();return error.isEmpty();}
    private boolean send(){capture();if(!validity())return false;PacketDistributor.sendToServer(new NativePortSettingsPayload(menu.containerId,menu.tile.getBlockPos(),draft()));return true;}
    public void action(int id,int direction){capture();if(id==9){if(send())super.onClose();return;}if(id==20||id==21){if(!send())return;page=id-20;rebuild();return;}if(id>=0&&id<6)faces[id]=ResourcePortSettings.Face.values()[Math.floorMod(faces[id].ordinal()+direction,3)];else if(id==6)pull=!pull;else if(id==7)artwork=ResourcePortSettings.OnTexture.values()[Math.floorMod(artwork.ordinal()+direction,3)];else if(id==8)mode=Math.floorMod(mode+direction,3);else return;send();rebuild();}
    @Override public boolean mouseClicked(double x,double y,int mouse){if(mouse==1)for(var entry:buttons.entrySet())if(entry.getKey()<9&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}return super.mouseClicked(x,y,mouse);}
    @Override public void onClose(){send();super.onClose();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);graphics.drawString(font,title,leftPos+8,topPos+8,0xFFFFFF,false);String label=error.isEmpty()?(page==0?"Throughput ("+menu.tile.kind.units+", blank = infinite)":"Redstone channels"):error;graphics.drawString(font,font.plainSubstrByWidth(label,imageWidth-20),leftPos+10,topPos+(page==0?118:64),error.isEmpty()?0xFFFFFF:0xFF8888,false);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
