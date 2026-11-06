package com.vandorlabs.neoforge;

import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.client.NativeCredentialFilterPicker;
import com.vandorlabs.neoforge.credentials.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Title-only actual inventory models/padding and renderer-backed credential selector samples. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CredentialItemModelChecks {
 private static int ticks,stage,checks,providerCalls;private static CompletableFuture<Void> reload;private static volatile boolean captured;private static boolean captureRequested;
 private static BakedModel frozen;private static List<BakedQuad> frozenQuads;private static Object frozenParticle;
 private static NativeCredentialFilterPicker cards,chips;
 private static boolean enabled(){return "true".equals(System.getenv("VANDOR_CREDENTIAL_MODEL_CHECKS"));}
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Credential models: "+why);}
 private static List<BakedQuad> quads(BakedModel model){var result=new ArrayList<BakedQuad>();result.addAll(model.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null));for(var side:Direction.values())result.addAll(model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));return result;}
 private static void matrix(Minecraft mc){int count=0;
  for(boolean chip:new boolean[]{false,true})for(int company=0;company<7;company++){
   var stack=chip?NativeCredentialItems.dataChip(company):NativeCredentialItems.accessCard(company);var model=mc.getItemRenderer().getModel(stack,null,null,0);var quads=quads(model);need(quads.size()==18,"three exact authored elements per item");String front="items/"+(chip?"data_chip_":"access_card_")+CredentialCatalog.companyName(company),edge="items/"+(chip?"data_chip_edge":"access_card_edge");boolean sawFront=false;
   for(var q:quads){var sprite=q.getSprite().contents();String path=sprite.name().getPath();need(path.equals(front)||path.equals(edge),"actual company atlas texture "+path+" expected "+front);need(sprite.width()==128&&sprite.height()==128,"canonical128px credential sprite");sawFront|=path.equals(front);}
   need(sawFront&&model.getParticleIcon(ModelData.EMPTY).contents().name().getPath().equals(front),"front/particle identity");var gui=model.getTransforms().gui;need(gui.rotation.x()==10&&gui.rotation.y()==-15&&gui.rotation.z()==0&&gui.scale.x()==.85F&&gui.scale.y()==.85F&&gui.scale.z()==.85F,"canonical GUI pose");
   for(var quad:quads){int[] raw=quad.getVertices();int stride=raw.length/4;for(int v=0;v<4;v++){double x=(Float.intBitsToFloat(raw[v*stride])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[v*stride+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[v*stride+2])-.5)*gui.scale.z();double ry=Math.toRadians(-15),rx=Math.toRadians(10);double xx=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xx;y=y*Math.cos(rx)-z*Math.sin(rx);need(x>-.44&&x<.44&&y>-.44&&y<.44,"rotated GUI padding");}}
   if(frozen==null){frozen=model;frozenQuads=quads;frozenParticle=model.getParticleIcon(ModelData.EMPTY);}count++;
  }
  for(int invalid:new int[]{-1,7,Integer.MAX_VALUE}){var stack=NativeCredentialItems.accessCard(0);stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(invalid));var model=mc.getItemRenderer().getModel(stack,null,null,0);need(model.getParticleIcon(ModelData.EMPTY).contents().name().getPath().equals("items/access_card_unbranded")&&!NativeCredentialItems.authorized(stack,false,127),"fallback icon never authorizes malformed company");}
  var old=quads(frozen);need(old.size()==frozenQuads.size(),"old model remains owned");for(int i=0;i<old.size();i++)need(Arrays.equals(old.get(i).getVertices(),frozenQuads.get(i).getVertices())&&old.get(i).getSprite()==frozenQuads.get(i).getSprite(),"old baked model unchanged after reload");need(frozen.getParticleIcon(ModelData.EMPTY)==frozenParticle&&count==14,"complete model matrix");
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!enabled()||stage==4)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>2400)throw new AssertionError("Credential title timeout stage="+stage);if(ticks<80||mc.getOverlay()!=null||stage<2&&!(mc.screen instanceof TitleScreen))return;need(mc.level==null&&mc.getSingleplayerServer()==null,"title-only, no world/server");
  if(stage==0){matrix(mc);reload=mc.reloadResourcePacks();stage=1;}
  else if(stage==1&&reload.isDone()){reload.join();matrix(mc);cards=new NativeCredentialFilterPicker(company->{providerCalls++;return NativeCredentialItems.accessCard(company);},false);chips=new NativeCredentialFilterPicker(company->{providerCalls++;return NativeCredentialItems.dataChip(company);},true);int[] selection={0};need(cards.click(16+136,144,1,16,144,2,value->selection[0]=value)&&selection[0]==127,"real picker previous callback");stage=2;mc.setScreen(new SamplesScreen());}
  else if(stage==3&&captured){need(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/credential-icons.png")),"actual GUI screenshot retained");need(providerCalls==2,"owned sample provider invoked once per stable filter");System.out.println("[vandorlabs][credential-models] title PASS checks="+checks+" models=14/before-afterreload/128pxsprites/rotatedpadding/realGUIitems/filterSamples; no world/server");stage=4;mc.stop();}
 }
 private static final class SamplesScreen extends net.minecraft.client.gui.screens.Screen {
  SamplesScreen(){super(net.minecraft.network.chat.Component.literal("Credential inventory samples"));}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial){
   var mc=Minecraft.getInstance();g.fill(0,0,width,height,0xFF0A1018);g.fill(8,20,408,190,0xFF101820);g.drawString(mc.font,"Access cards",16,25,0xFFFFFFFF,false);g.drawString(mc.font,"Data chips",16,77,0xFFFFFFFF,false);
   for(int row=0;row<2;row++)for(int company=0;company<7;company++){int x=16+56*company,y=40+52*row;g.fill(x,y,x+18,y+18,0xFF21384B);g.renderItem(row==0?NativeCredentialItems.accessCard(company):NativeCredentialItems.dataChip(company),x+1,y+1);g.drawString(mc.font,CredentialCatalog.companyName(company),x,y+21,0xFFDAE8F0,false);}cards.render(g,mc.font,16,144,2);chips.render(g,mc.font,224,144,64);g.flush();
   if(stage==2){stage=3;captureRequested=true;}
  }
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!enabled()||!captureRequested)return;captureRequested=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"credential-icons.png",mc.getMainRenderTarget(),message->{captured=true;});}
}
