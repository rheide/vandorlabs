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
 private com.vandorlabs.ui.SynthesisDialog.Layout layout=new com.vandorlabs.ui.SynthesisDialog.Layout(true);
 private static final String[] IDS=com.vandorlabs.ui.SynthesisDialog.DESIGNS,NAMES=com.vandorlabs.ui.SynthesisDialog.NAMES;
 private final Map<Integer,Button> buttons=new LinkedHashMap<>();private final VisualClock clock=new VisualClock();private int page,activation;private EditBox channels;private String error="";
 public NativeBuilderScreen(NativeBuilderMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=312;imageHeight=240;activation=menu.tile.activation();}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(NativeBuilderBindings.enabled())e.register(NativeBuilderBindings.MENU,NativeBuilderScreen::new);}
 public Button control(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public int page(){return page;}public int[] previewBounds(){return new int[]{leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom};}
 public boolean controlsFit(){if(leftPos<0||topPos<0||leftPos+imageWidth>width||topPos+imageHeight>height)return false;for(var slot:menu.slots)if(slot.y<0||slot.y+16>imageHeight||slot.x<0||slot.x+16>imageWidth)return false;return true;}
 @Override protected void init(){layout=com.vandorlabs.ui.SynthesisDialog.layout(width,height);imageWidth=layout.width;imageHeight=layout.height;super.init();for(int i=0;i<menu.slots.size();i++){var slot=(com.vandorlabs.neoforge.mixin.NativeSlotPositionAccess)(Object)menu.slots.get(i);slot.vandorlabs$slotX(layout.slotX(i));slot.vandorlabs$slotY(layout.slotY(i));}rebuild();}
 private void button(int id,int x,int y,int w,String text){
  Button button;if(id>=100)button=new com.vandorlabs.neoforge.client.NativeDialogTabButton(leftPos+x,topPos+y,w,20,Component.literal(text),b->action(id,1));
  else button=new Button(leftPos+x,topPos+y,w,id<3?(layout.compact?17:24):20,Component.literal(text),b->action(id,1),java.util.function.Supplier::get){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){if(id<3){if(menu.value(0)==id)g.fill(getX()+2,getY(),getX()+getWidth()-2,getY()+(layout.compact?17:22),0xff345166);g.drawString(font,getMessage(),getX()+8,getY()+(layout.compact?5:9),menu.value(0)==id?0xffffff:0xb6c2cd,false);}else{int edge=isHovered()?0xffa4d4e8:0xff607c8c;g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isHovered()?0xff354958:0xff24323e);g.fill(getX(),getY(),getX()+getWidth(),getY()+1,edge);g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),edge);g.fill(getX(),getY(),getX()+1,getY()+getHeight(),edge);g.fill(getX()+getWidth()-1,getY(),getX()+getWidth(),getY()+getHeight(),edge);g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+6,0xffe0e8ec);}}};buttons.put(id,button);addRenderableWidget(button);
 }

 private String receiving(){return channels==null?menu.tile.channels().toString():channels.getValue();}
 private void rebuild(){String draft=receiving();clearWidgets();buttons.clear();channels=null;button(100,12,32,layout.tabWidth(),"Production");button(101,layout.previewLeft,32,layout.tabWidth(),"Redstone");buttons.get(100+page).active=false;
  if(page==0){for(int i=0;i<3;i++)button(i,12,layout.previewTop+(layout.compact?0:4)+i*(layout.compact?17:24),layout.previewLeft-22,NAMES[i]);}
  else{button(10,12,layout.receiverY()+42,layout.width-24,"Activation: "+activationName());channels=new EditBox(font,leftPos+12,topPos+layout.receiverY()+12,layout.width-24,18,Component.literal("Receive channels (blank = local)"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft);addRenderableWidget(channels);}
 }
 private String activationName(){return com.vandorlabs.ui.RedstoneActivation.label(activation); }
 private boolean sendRedstone(){if(channels==null)return true;var parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null){error="Invalid receiving channels";return false;}error="";PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),1,activation,parsed)));return true;}
 public void action(int id,int direction){if(id==99){onClose();return;}if(id==100||id==101){if(!sendRedstone())return;page=id-100;rebuild();return;}if(id>=0&&id<3){PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),0,id,ChannelList.EMPTY)));return;}if(id==10){activation=Math.floorMod(activation+(direction<0?-1:1),3);sendRedstone();rebuild();}}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&page==0){for(int i=0;i<3;i++)if(buttons.get(i).isMouseOver(x,y)){action(i,1);return true;}}if(button==1&&buttons.containsKey(10)&&buttons.get(10).isMouseOver(x,y)){action(10,-1);return true;}return super.mouseClicked(x,y,button);}
 @Override public void onClose(){if(sendRedstone())super.onClose();}
 @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(g,font,Component.literal("Synthesis Tower"),leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.SynthesisDialog.STYLE);
  if(page==0){int selected=Math.clamp(menu.value(0),0,2);g.fill(leftPos+12,topPos+layout.previewTop,leftPos+layout.previewLeft-10,topPos+layout.previewBottom,0xff0d1118);preview(g,IDS[selected]);if(!layout.compact){g.drawString(font,"Designs",leftPos+12,topPos+60,0xd4dfe9,false);g.drawString(font,"Finished structure",leftPos+layout.previewLeft,topPos+60,0xd4dfe9,false);}}
  else{g.drawString(font,"Receive channels (blank = local)",leftPos+12,topPos+layout.receiverY(),0xdae8f0,false);if(!layout.compact)g.drawString(font,error.isEmpty()?"Physical Redstone or any receiving channel.":error,leftPos+12,topPos+(layout.compact?114:142),error.isEmpty()?0x9baab3:0xff7777,false);if(!layout.compact)g.drawString(font,"A disabled tower keeps its current build.",leftPos+12,topPos+155,0xa7b3c0,false);}
  String[] labels={"Matter","Core","Chip"};int[] labelSlots={0,1,3};for(int i=0;i<labels.length;i++)g.drawString(font,labels[i],leftPos+layout.machineX(labelSlots[i])-3,topPos+layout.machineY-12,0xdae8f0,false);
  for(int i=0;i<menu.slots.size();i++){int x=leftPos+layout.slotX(i),y=topPos+layout.slotY(i);g.fill(x-1,y-1,x+17,y+17,0xff67727e);g.fill(x,y,x+16,y+16,0xff0b1017);}
  int start=layout.progressX(),end=start+layout.progressWidth(),progress=Math.clamp(menu.value(2),0,8000)*(end-start)/8000;g.fill(leftPos+start,topPos+layout.machineY+4,leftPos+end,topPos+layout.machineY+12,0xff303947);g.fill(leftPos+start,topPos+layout.machineY+4,leftPos+start+progress,topPos+layout.machineY+12,0xff55cba4);
  g.drawString(font,Integer.toString(menu.value(3)),leftPos+12,topPos+(layout.compact?210:272),0xdae8f0,false);g.drawString(font,"Inventory",leftPos+layout.inventoryX,topPos+layout.inventoryY-10,0xdae8f0,false);
 }
 private void preview(GuiGraphics g,String id){g.fill(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom,0xff0d1118);if(!NativeBuilderModelCache.ready())return;g.flush();g.enableScissor(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom);var poses=g.pose();poses.pushPose();try{poses.translate(leftPos+(layout.previewLeft+layout.previewRight)/2,topPos+(layout.compact?84:115),150);poses.scale(layout.compact?8:12,layout.compact?-8:-12,layout.compact?8:12);poses.mulPose(Axis.XP.rotationDegrees(17));poses.mulPose(Axis.YP.rotationDegrees((float)(clock.sample(System.nanoTime(),minecraft.isPaused())*1.5)));RenderSystem.enableDepthTest();var buffers=minecraft.renderBuffers().bufferSource();NativeBuilderModelCache.preview(id,poses,buffers,0xf000f0);buffers.endBatch();}finally{poses.popPose();g.disableScissor();RenderSystem.disableDepthTest();}}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);if(x>=leftPos+layout.machineX(3)&&x<leftPos+layout.machineX(3)+16&&y>=topPos+layout.machineY&&y<topPos+layout.machineY+16)g.renderTooltip(font,Component.literal(com.vandorlabs.ui.SynthesisDialog.CATALOG_CHIP_TOOLTIP),x,y);else renderTooltip(g,x,y);}
 @Override public boolean isPauseScreen(){return false;}
}
