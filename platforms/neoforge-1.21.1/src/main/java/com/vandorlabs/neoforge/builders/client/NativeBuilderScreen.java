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
 private static final String[] IDS=NativeBuilderProducts.IDS,NAMES=NativeBuilderProducts.NAMES;
 private final Map<Integer,Button> buttons=new LinkedHashMap<>();private final VisualClock clock=new VisualClock();private int page,activation,displayedMode=-1,displayedChoices=-1;private EditBox channels;private String error="";
 public NativeBuilderScreen(NativeBuilderMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=312;imageHeight=240;activation=menu.tile.activation();}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(NativeBuilderBindings.enabled())e.register(NativeBuilderBindings.MENU,NativeBuilderScreen::new);}
 public Button control(int id){return buttons.get(id);}public EditBox channelField(){return channels;}public int page(){return page;}public int[] previewBounds(){return new int[]{leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom};}
 public boolean controlsFit(){if(leftPos<0||topPos<0||leftPos+imageWidth>width||topPos+imageHeight>height)return false;for(var slot:menu.slots)if(slot.isActive()&&(slot.y<0||slot.y+16>imageHeight||slot.x<0||slot.x+16>imageWidth))return false;return true;}
 @Override protected void init(){layout=com.vandorlabs.ui.SynthesisDialog.layout(width,height);imageWidth=layout.width;imageHeight=layout.height;super.init();rebuild();}
 private void positionSlots(){menu.page(page);for(int i=0;i<menu.slots.size();i++){var slot=(com.vandorlabs.neoforge.mixin.NativeSlotPositionAccess)(Object)menu.slots.get(i);boolean hidden=page!=0||menu.digitizing()&&i==1;slot.vandorlabs$slotX(hidden?-10000:layout.slotX(i,menu.digitizing()));slot.vandorlabs$slotY(hidden?-10000:layout.slotY(i));}}
 @Override protected void containerTick(){super.containerTick();if(displayedMode!=menu.value(9)||press()&&displayedChoices!=menu.value(10))rebuild();}
 private void button(int id,int x,int y,int w,String text){
  Button button;if(id==100||id==101)button=new com.vandorlabs.neoforge.client.NativeDialogTabButton(leftPos+x,topPos+y,w,20,Component.literal(text),b->action(id,1));
  else button=new Button(leftPos+x,topPos+y,w,id>=0&&id<designs()?layout.rowHeight:20,Component.literal(text),b->action(id,1),java.util.function.Supplier::get){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){if(id>=0&&id<designs()){if(menu.value(0)==id)g.fill(getX()+2,getY(),getX()+getWidth()-2,getY()+layout.rowHeight-2,0xff345166);g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()-20),getX()+6,getY()+(layout.rowHeight-8)/2,menu.value(0)==id?0xffffff:0xb6c2cd,false);}else{int edge=isHovered()?0xffa4d4e8:0xff607c8c;g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isHovered()?0xff354958:0xff24323e);g.fill(getX(),getY(),getX()+getWidth(),getY()+1,edge);g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),edge);g.fill(getX(),getY(),getX()+1,getY()+getHeight(),edge);g.fill(getX()+getWidth()-1,getY(),getX()+getWidth(),getY()+getHeight(),edge);g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+6,0xffe0e8ec);}}};buttons.put(id,button);addRenderableWidget(button);
 }

 private boolean press(){return menu.tile.press();}private boolean tower(){return menu.tile.synthesis();}private String machine(){return menu.tile.design().id();}
 private int designs(){return tower()?6:NativeBuilderProducts.parts(machine()).size();}private net.minecraft.world.item.ItemStack blockPreview(){return menu.digitizing()?(menu.tile.visual().processing()?menu.tile.visual().input():menu.contents().getItem(0)):menu.tile.visual().template();}private String name(int i){return !tower()?NativeBuilderProducts.partResult(machine(),i).getHoverName().getString():i==5?"Block → Output":NAMES[i];}
 private String receiving(){return channels==null?menu.tile.channels().toString():channels.getValue();}
 private void rebuild(){positionSlots();displayedMode=menu.value(9);displayedChoices=menu.value(10);String draft=receiving();clearWidgets();buttons.clear();channels=null;button(100,12,32,layout.tabWidth(),"Production");button(101,layout.secondTabX(),32,layout.tabWidth(),"Redstone");buttons.get(100+page).active=false;
  if(page==0){int row=0;for(int order=0;order<designs();order++){
   int i=press()?(order+1)%designs():order;
   if(!press()||(displayedChoices&(1<<i))!=0)button(i,12,layout.listTop+row++*layout.rowHeight,layout.listRight-layout.listLeft,name(i));
  }if(tower())button(102,120,3,112,"Mode: "+(menu.digitizing()?"Digitize":"Synthesize"));}
  else{button(10,layout.listLeft,layout.receiverY()+42,layout.receiverWidth(),"Activation: "+activationName());channels=new EditBox(font,leftPos+layout.listLeft,topPos+layout.receiverY()+12,layout.receiverWidth(),18,Component.literal("Receive channels (blank = local)"));channels.setMaxLength(ChannelList.MAX_TEXT_LENGTH);channels.setValue(draft);addRenderableWidget(channels);}
 }
 private String activationName(){return com.vandorlabs.ui.RedstoneActivation.label(activation); }
 private boolean sendRedstone(){if(channels==null)return true;var parsed=ChannelList.parse(channels.getValue());channels.setTextColor(parsed==null?0xff7777:0xe0e0e0);if(parsed==null){error="Invalid receiving channels";return false;}error="";PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),1,activation,parsed)));return true;}
 public void action(int id,int direction){if(id==99){onClose();return;}if(id==102){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,menu.digitizing()?0:1);return;}if(id==100||id==101){if(!sendRedstone())return;page=id-100;rebuild();return;}if(id>=0&&id<designs()){PacketDistributor.sendToServer(new NativeBuilderPayload(new NativeBuilderEdit(menu.containerId,menu.tile.getBlockPos(),0,id,ChannelList.EMPTY)));return;}if(id==10){activation=Math.floorMod(activation+(direction<0?-1:1),3);sendRedstone();rebuild();}}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&page==0){for(int i=0;i<designs();i++)if(buttons.containsKey(i)&&buttons.get(i).isMouseOver(x,y)){action(i,1);return true;}}if(button==1&&buttons.containsKey(102)&&buttons.get(102).isMouseOver(x,y)){action(102,-1);return true;}if(button==1&&buttons.containsKey(10)&&buttons.get(10).isMouseOver(x,y)){action(10,-1);return true;}return super.mouseClicked(x,y,button);}
 @Override public void onClose(){if(sendRedstone())super.onClose();}
 @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
  com.vandorlabs.neoforge.client.NativeDialogVisuals.panel(g,font,Component.literal(tower()?"Synthesis Tower":press()?"Assembly Press":"Enclosed Gantry"),leftPos,topPos,imageWidth,imageHeight,com.vandorlabs.ui.SynthesisDialog.STYLE);
  if(page==0)g.fill(leftPos+12,topPos+52,leftPos+imageWidth-12,topPos+layout.machineY+20,0xff17212c);
  if(page==0){
   int selected=Math.clamp(menu.value(0),0,designs()-1);
   g.fill(leftPos+layout.listLeft,topPos+layout.listTop,leftPos+layout.listRight,topPos+layout.listBottom,0xff0d1118);
   g.drawString(font,"Designs",leftPos+layout.listLeft,topPos+layout.listHeadingY(),0xd4dfe9,false);
   g.drawString(font,font.plainSubstrByWidth(menu.digitizing()?"Digitize "+(blockPreview().isEmpty()?"block":blockPreview().getHoverName().getString()):tower()&&selected==5&&!blockPreview().isEmpty()?blockPreview().getHoverName().getString():name(selected),layout.previewRight-layout.previewLeft),leftPos+layout.previewLeft,topPos+layout.previewTop-12,0xd4dfe9,false);
   if(menu.digitizing()||tower()&&selected==5)blockPreview(g);else if(!tower()||selected>=3)g.renderItem(!tower()?NativeBuilderProducts.partResult(machine(),selected):NativeBuilderProducts.result(selected),leftPos+(layout.previewLeft+layout.previewRight)/2-8,topPos+(layout.previewTop+layout.previewBottom)/2-8);else preview(g,IDS[selected]);
  }else{
   g.drawString(font,"Receive channels (blank = local)",leftPos+layout.listLeft,topPos+layout.receiverY(),0xdae8f0,false);
   if(!layout.compact){g.drawString(font,error.isEmpty()?"Physical Redstone or any receiving channel.":error,leftPos+layout.listLeft,topPos+176,error.isEmpty()?0x9baab3:0xff7777,false);g.drawString(font,"A disabled builder keeps its current build.",leftPos+layout.listLeft,topPos+189,0xa7b3c0,false);}
  }
  if(page==0){String[] labels={menu.digitizing()?"Input":com.vandorlabs.ui.SynthesisDialog.inputLabel(menu.tile.design().id()),"Output","Chip"};int[] labelSlots={0,1,3};for(int i=0;i<labels.length;i++)if(!(menu.digitizing()&&labelSlots[i]==1))g.drawString(font,labels[i],leftPos+layout.slotX(labelSlots[i],menu.digitizing())-3,topPos+layout.machineY-12,0xdae8f0,false);
  for(int i=0;i<menu.slots.size();i++){if(!menu.slots.get(i).isActive())continue;int x=leftPos+layout.slotX(i,menu.digitizing()),y=topPos+layout.slotY(i);g.fill(x-1,y-1,x+17,y+17,0xff67727e);g.fill(x,y,x+16,y+16,0xff0b1017);}
  int start=layout.progressX(),end=start+layout.progressWidth(),progress=Math.clamp(menu.value(2),0,8000)*(end-start)/8000;
  g.fill(leftPos+start,topPos+layout.machineY+4,leftPos+end,topPos+layout.machineY+12,0xff303947);g.fill(leftPos+start,topPos+layout.machineY+4,leftPos+start+progress,topPos+layout.machineY+12,0xff55cba4);
  int rf=Math.max(0,menu.value(3)),capacity=Math.max(0,menu.value(4)),fill=capacity==0?0:(int)((long)Math.min(rf,capacity)*layout.rfWidth/capacity);
  int rx=leftPos+layout.rfX,ry=topPos+layout.rfY;g.fill(rx-1,ry-1,rx+layout.rfWidth+1,ry+layout.rfHeight+1,0xff67727e);g.fill(rx,ry,rx+layout.rfWidth,ry+layout.rfHeight,0xff0b1017);if(fill>0)g.fill(rx,ry,rx+fill,ry+layout.rfHeight,0xff55cba4);
  g.drawString(font,"RF",rx+4,ry+4,0xffe4edf4,false);
  g.drawString(font,">",leftPos+start+layout.progressWidth()/2-2,topPos+layout.machineY+4,0xd4dfe9,false);
  g.drawString(font,"Inventory",leftPos+layout.inventoryX,topPos+layout.inventoryY-10,0xd4dfe9,false);}
 }
 private void blockPreview(GuiGraphics g){g.fill(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom,0xff0d1118);var stack=blockPreview();if(stack.isEmpty())return;g.flush();g.enableScissor(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom);var poses=g.pose();poses.pushPose();try{poses.translate(leftPos+(layout.previewLeft+layout.previewRight)/2D,topPos+(layout.previewTop+layout.previewBottom)/2D,150);float scale=Math.min(layout.previewRight-layout.previewLeft,layout.previewBottom-layout.previewTop)*.56F;poses.scale(scale,-scale,scale);poses.mulPose(Axis.XP.rotationDegrees(17));poses.mulPose(Axis.YP.rotationDegrees((float)(clock.sample(System.nanoTime(),minecraft.isPaused())*1.5)));poses.translate(-.5,-.5,-.5);RenderSystem.enableDepthTest();var buffers=minecraft.renderBuffers().bufferSource();NativeBlockRecipePreview.render(stack,poses,buffers,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,menu.digitizing()&&menu.value(6)==1?1-Math.clamp(menu.value(2)/8000F,0,1):1);buffers.endBatch();}finally{poses.popPose();g.disableScissor();RenderSystem.disableDepthTest();}}
 private void preview(GuiGraphics g,String id){g.fill(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom,0xff0d1118);if(!NativeBuilderModelCache.ready())return;g.flush();g.enableScissor(leftPos+layout.previewLeft,topPos+layout.previewTop,leftPos+layout.previewRight,topPos+layout.previewBottom);var poses=g.pose();poses.pushPose();try{poses.translate(leftPos+(layout.previewLeft+layout.previewRight)/2,topPos+(layout.previewTop+layout.previewBottom)/2,150);double extent=Math.sqrt(Math.pow(NativeBuilderModelCache.width(id),2)+Math.pow(NativeBuilderModelCache.height(id),2)+9);float scale=(float)(Math.min(layout.previewRight-layout.previewLeft,layout.previewBottom-layout.previewTop)*.8/extent);poses.scale(scale,-scale,scale);poses.mulPose(Axis.XP.rotationDegrees(17));poses.mulPose(Axis.YP.rotationDegrees((float)(clock.sample(System.nanoTime(),minecraft.isPaused())*1.5)));RenderSystem.enableDepthTest();var buffers=minecraft.renderBuffers().bufferSource();NativeBuilderModelCache.preview(id,poses,buffers,0xf000f0);buffers.endBatch();}finally{poses.popPose();g.disableScissor();RenderSystem.disableDepthTest();}}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);
  if(page==0&&x>=leftPos+layout.slotX(0,menu.digitizing())&&x<leftPos+layout.slotX(0,menu.digitizing())+16&&y>=topPos+layout.slotY(0)&&y<topPos+layout.slotY(0)+16)g.renderTooltip(font,Component.literal(menu.digitizing()?"One supported block to digitize":com.vandorlabs.ui.SynthesisDialog.inputTooltip(menu.tile.design().id())),x,y);
  else if(page==0&&x>=leftPos+layout.rfX&&x<leftPos+layout.rfX+layout.rfWidth&&y>=topPos+layout.rfY&&y<topPos+layout.rfY+layout.rfHeight)g.renderTooltip(font,Component.literal(com.vandorlabs.ui.SynthesisDialog.rfTooltip(menu.value(3),menu.value(4))),x,y);
  else if(page==0&&x>=leftPos+layout.slotX(3,menu.digitizing())&&x<leftPos+layout.slotX(3,menu.digitizing())+16&&y>=topPos+layout.slotY(3)&&y<topPos+layout.slotY(3)+16)g.renderTooltip(font,Component.literal(com.vandorlabs.ui.SynthesisDialog.CATALOG_CHIP_TOOLTIP),x,y);
  else renderTooltip(g,x,y);
 }
 @Override public boolean isPauseScreen(){return false;}
}
