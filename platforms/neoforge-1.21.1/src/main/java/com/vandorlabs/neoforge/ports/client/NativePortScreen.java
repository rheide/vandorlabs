package com.vandorlabs.neoforge.ports.client;

import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
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
    private ResourcePortSettings.OnTexture artwork;private boolean pull;private int mode,page,housing;private NativeMaterialPicker picker;private boolean blockShape;
    private String throughput,channelText,error="";private EditBox rate,channels;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativePortScreen(NativePortMenu menu,Inventory inventory,Component title){super(menu,inventory,title);var config=menu.tile.configuration();for(int i=0;i<6;i++)faces[i]=config.settings().face(i);artwork=config.settings().onTexture;pull=config.settings().pullInputs;mode=config.redstoneMode();housing=config.settings().housingChoice;blockShape=battery()!=null&&battery().blockShape();throughput=config.settings().throughput==0?"":Long.toString(config.settings().throughput);channelText=config.channels().toString();}
    private com.vandorlabs.neoforge.reservoir.NativeBatteryEntity battery(){return menu.tile.portBlockEntity() instanceof com.vandorlabs.neoforge.reservoir.NativeBatteryEntity value?value:null;}
    public Button control(int id){return buttons.get(id);}
    public EditBox rateField(){return rate;}
    public EditBox channelField(){return channels;}
    public NativeMaterialPicker housingPicker(){return picker;}
    public NativePortData.Configuration editingConfiguration(){capture();return draft();}
    @Override protected void init(){imageWidth=Math.min(360,width-12);imageHeight=228;super.init();rebuild();}
    private void button(int id,int x,int y,int w,String label){var button=Button.builder(Component.literal(label),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void capture(){if(rate!=null)throughput=rate.getValue();if(channels!=null)channelText=channels.getValue();}
    private void rebuild(){capture();clearWidgets();buttons.clear();rate=channels=null;picker=null;int w=(imageWidth-30)/2;int tab=(imageWidth-40)/3;button(20,10,28,tab,"General");button(22,20+tab,28,tab,"Housing");button(21,30+tab*2,28,tab,"Redstone");buttons.get(20+page).active=false;if(page==0){String[] names={"Down","Up","North","South","West","East"};String[] modes={"Disabled","Input","Output"};for(int i=0;i<6;i++)button(i,10+(i%2)*(w+10),52+(i/2)*22,w,names[i]+": "+modes[faces[i].ordinal()]);rate=new EditBox(font,leftPos+10,topPos+130,imageWidth-20,18,Component.literal("Throughput"));rate.setMaxLength(19);rate.setValue(throughput);rate.setResponder(value->{throughput=value;validity();});addRenderableWidget(rate);button(6,10,152,imageWidth-20,"Pull from inputs: "+(pull?"On":"Off"));button(7,10,174,imageWidth-20,"On texture: "+new String[]{"Active redstone","Connected","Transferring"}[artwork.ordinal()]);}else if(page==1){channels=new EditBox(font,leftPos+10,topPos+78,imageWidth-20,18,Component.literal("Redstone channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(channelText);channels.setResponder(value->{channelText=value;validity();});addRenderableWidget(channels);button(8,10,108,imageWidth-20,"Activation: "+new String[]{"Always active","Redstone on","Redstone off"}[mode]);}else{picker=new NativeMaterialPicker(housing,id->ModernFinishCatalog.option(id).general(),choice->{housing=choice;send();}).specialMaterial(-1,"Original artwork","Housing",battery()==null?"vandorlabs:blocks/resource_ports/port_hull":"vandorlabs:blocks/imported/power/battery_cell_bank");picker.layout(leftPos+10,topPos+56,battery()==null?imageWidth-28:imageWidth/2-28,6,false);addRenderableWidget(picker.searchField());if(battery()!=null)button(10,imageWidth/2+10,174,imageWidth/2-20,"Shape: "+(blockShape?"Block":"Hexagon"));}button(9,(imageWidth-150)/2,202,150,"Done");validity();}
    private NativePortData.Configuration draft(){Long amount=ResourcePortSettings.parseThroughput(throughput);var bank=ChannelList.parse(channelText);if(amount==null||bank==null)throw new IllegalArgumentException("Invalid throughput or channels");return new NativePortData.Configuration(new ResourcePortSettings(faces,amount,pull,artwork,housing),mode,bank);}
    private boolean validity(){try{draft();error="";}catch(IllegalArgumentException invalid){error=invalid.getMessage();}if(buttons.containsKey(9))buttons.get(9).active=error.isEmpty();return error.isEmpty();}
    private boolean send(){capture();if(!validity())return false;PacketDistributor.sendToServer(new NativePortSettingsPayload(menu.containerId,menu.tile.getBlockPos(),draft()));return true;}
    public void action(int id,int direction){capture();if(id==9){if(send())super.onClose();return;}if(id==20||id==21||id==22){if(!send())return;page=id-20;rebuild();return;}if(id==10&&battery()!=null){blockShape=!blockShape;PacketDistributor.sendToServer(new com.vandorlabs.neoforge.reservoir.NativeBufferSettingsPayload(menu.containerId,menu.tile.getBlockPos(),housing,1,blockShape));rebuild();return;}if(id>=0&&id<6)faces[id]=ResourcePortSettings.Face.values()[Math.floorMod(faces[id].ordinal()+direction,3)];else if(id==6)pull=!pull;else if(id==7)artwork=ResourcePortSettings.OnTexture.values()[Math.floorMod(artwork.ordinal()+direction,3)];else if(id==8)mode=Math.floorMod(mode+direction,3);else return;send();rebuild();}
    @Override public boolean mouseClicked(double x,double y,int mouse){if(mouse==1)for(var entry:buttons.entrySet())if((entry.getKey()<9||entry.getKey()==10)&&entry.getValue().active&&entry.getValue().isMouseOver(x,y)){action(entry.getKey(),-1);return true;}return picker!=null&&picker.click(x,y,mouse)||super.mouseClicked(x,y,mouse);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker!=null&&picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(picker!=null)picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker!=null&&picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(picker!=null&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE&&picker.inventoryPicker()!=null){picker.closeInventory();return true;}return picker!=null&&picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){send();super.onClose();}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);graphics.drawString(font,title,leftPos+8,topPos+8,0xFFFFFF,false);if(picker!=null)picker.render(graphics,x,y);if(page==2&&battery()!=null){int gx=leftPos+imageWidth/2+12,gy=topPos+64;graphics.fill(gx-2,gy-2,gx+14,gy+74,0xff98a8b8);graphics.fill(gx,gy,gx+12,gy+72,0xff0c1219);int amount=battery().amount(),filled=(int)Math.round(72D*amount/battery().capacity());graphics.fill(gx,gy+72-filled,gx+12,gy+72,0xff62d884);graphics.drawWordWrap(font,Component.literal(amount+" / "+battery().capacity()+" RF"),gx+22,gy+3,imageWidth/2-44,0xffffff);graphics.drawWordWrap(font,Component.literal("Input/output faces are configured on General."),gx+22,gy+45,imageWidth/2-44,0xcdd7e4);}String label=error.isEmpty()?(page==0?"Shared output limit (blank for unlimited)":page==1?"Redstone channels":""):error;graphics.drawString(font,font.plainSubstrByWidth(label,imageWidth-20),leftPos+10,topPos+(page==0?118:64),error.isEmpty()?0xFFFFFF:0xFF8888,false);}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);if(page==0&&rate!=null)graphics.drawString(font,menu.tile.portKind().units+"/tick",rate.getX()+rate.getWidth()-font.width(menu.tile.portKind().units+"/tick")-4,rate.getY()+5,0x8294a4,false);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
