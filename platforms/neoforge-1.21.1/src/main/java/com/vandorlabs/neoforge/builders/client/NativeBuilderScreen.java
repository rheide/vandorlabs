package com.vandorlabs.neoforge.builders.client;

import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.animation.VisualClock;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Compact production notebook, physical inventory slots and the shared textured model preview. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBuilderScreen extends AbstractContainerScreen<NativeBuilderMenu>{
 private static final String[] IDS={"synthesis_tower","assembly_press","enclosed_gantry"},NAMES={"Synthesis Tower","Assembly Press","Enclosed Gantry"};
 private final Map<Integer,Button> buttons=new LinkedHashMap<>();private final VisualClock clock=new VisualClock();private int page,activation;private EditBox channels;private String error="";
 public NativeBuilderScreen(NativeBuilderMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=312;imageHeight=240;activation=menu.tile.activation();}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(NativeBuilderBindings.enabled())e.register(NativeBuilderBindings.MENU,NativeBuilderScreen::new);}
 public Button control(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public int page(){return page;}public int[] previewBounds(){return new int[]{leftPos+160,topPos+54,leftPos+300,topPos+113};}
 public boolean controlsFit(){if(leftPos<0||topPos<0||leftPos+imageWidth>width||topPos+imageHeight>height)return false;for(var slot:menu.slots)if(slot.y<0||slot.y+16>imageHeight||slot.x<0||slot.x+16>imageWidth)return false;return true;}
 @Override protected void init(){super.init();rebuild();}
 private void button(int id,int x,int y,int w,String text){var button=Button.builder(Component.literal(text),b->action(id,1)).bounds(leftPos+x,topPos+y,w,18).build();buttons.put(id,button);addRenderableWidget(button);}
 private String receiving(){return channels==null?menu.tile.channels().toString():channels.getValue();}
 private void rebuild(){String draft=receiving();clearWidgets();buttons.clear();channels=null;button(100,12,32,142,"Production");button(101,158,32,142,"Redstone");buttons.get(100+page).active=false;button(99,278,6,22,"×");
  if(page==0){for(int i=0;i<3;i++)button(i,12,56+i*20,142,NAMES[i]);}
  else{button(10,12,58,288,"Activation: "+activationName());channels=new EditBox(font,leftPos+12,topPos+94,288,18,Component.literal("Receive channels"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft);addRenderableWidget(channels);}
 }
 private String activationName(){return switch(activation){case 1->"Redstone On";case 2->"Redstone Off";default->"Always";};}
 private boolean sendRedstone(){if(channels==null)return true;var parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null){error="Invalid receiving channels";return false;}error="";PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),1,activation,parsed)));return true;}
 public void action(int id,int direction){if(id==99){onClose();return;}if(id==100||id==101){if(!sendRedstone())return;page=id-100;rebuild();return;}if(id>=0&&id<3){PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),0,id,ChannelList.EMPTY)));return;}if(id==10){activation=Math.floorMod(activation+(direction<0?-1:1),3);sendRedstone();rebuild();}}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&buttons.containsKey(10)&&buttons.get(10).isMouseOver(x,y)){action(10,-1);return true;}return super.mouseClicked(x,y,button);}
 @Override public void onClose(){if(sendRedstone())super.onClose();}
 @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19232c);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+28,0xff304858);g.drawString(font,title,leftPos+12,topPos+10,0xffffff,false);
  if(page==0){int selected=Math.clamp(menu.value(0),0,2);g.fill(leftPos+9,topPos+56+selected*20,leftPos+11,topPos+74+selected*20,0xff62ed82);preview(g,IDS[selected]);if(menu.contents().getItem(3).isEmpty())g.drawString(font,"More designs can be loaded from a chip.",leftPos+12,topPos+115,0x9baab3,false);}
  else{g.drawString(font,"Receive channels",leftPos+12,topPos+82,0xdae8f0,false);g.drawString(font,error.isEmpty()?"Physical and receiving-channel redstone.":error,leftPos+12,topPos+116,error.isEmpty()?0x9baab3:0xff7777,false);}
  String[] labels={"Matter","Output","Battery","Chip"};int[] xs={18,104,190,276};for(int i=0;i<4;i++){g.drawString(font,labels[i],leftPos+xs[i]-3,topPos+125,0xdae8f0,false);g.fill(leftPos+xs[i]-1,topPos+135,leftPos+xs[i]+17,topPos+153,0xff596773);g.fill(leftPos+xs[i],topPos+136,leftPos+xs[i]+16,topPos+152,0xff111920);}
  int progress=Math.clamp(menu.value(2),0,8000)*56/8000;g.fill(leftPos+43,topPos+141,leftPos+99,topPos+146,0xff36434c);g.fill(leftPos+43,topPos+141,leftPos+43+progress,topPos+146,0xff62ed82);
  g.drawString(font,"RF input",leftPos+12,topPos+162,0x9baab3,false);g.drawString(font,menu.value(5)>0?"Connected":"Offline",leftPos+12,topPos+174,menu.value(5)>0?0x62ed82:0x9baab3,false);g.drawString(font,"Battery RF",leftPos+12,topPos+190,0x9baab3,false);g.drawString(font,Integer.toString(menu.value(3)),leftPos+12,topPos+202,0xdae8f0,false);g.drawString(font,"of "+menu.value(4),leftPos+12,topPos+214,0x9baab3,false);
  for(int row=0;row<4;row++)for(int col=0;col<9;col++){int x=leftPos+76+col*18,y=topPos+(row==3?220:162+row*18);g.fill(x-1,y-1,x+17,y+17,0xff596773);g.fill(x,y,x+16,y+16,0xff111920);}
 }
 private void preview(GuiGraphics g,String id){if(!NativeBuilderModelCache.ready())return;g.flush();g.enableScissor(leftPos+160,topPos+54,leftPos+300,topPos+113);var poses=g.pose();poses.pushPose();try{poses.translate(leftPos+230,topPos+84,150);poses.scale(9,-9,9);poses.mulPose(Axis.XP.rotationDegrees(18));poses.mulPose(Axis.YP.rotationDegrees((float)(clock.sample(System.nanoTime(),minecraft.isPaused())*1.5)));RenderSystem.enableDepthTest();var buffers=minecraft.renderBuffers().bufferSource();NativeBuilderModelCache.preview(id,poses,buffers,0xf000f0);buffers.endBatch();}finally{poses.popPose();g.disableScissor();RenderSystem.disableDepthTest();}}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
 @Override public boolean isPauseScreen(){return false;}
}
