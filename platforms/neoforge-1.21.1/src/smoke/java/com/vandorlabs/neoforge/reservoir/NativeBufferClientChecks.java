package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.reservoir.client.NativeBufferScreen;
import com.vandorlabs.neoforge.ports.client.NativePortScreen;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded real dialog/picker/status path and rendered normal/configured item padding. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeBufferClientChecks {
 private static int stage,ticks,checks,choice;private static boolean capture,drawn;private static final List<ItemStack> icons=new ArrayList<>();
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Own storage client stage="+stage+" tick="+ticks+": "+why);}
 private static void mode(int n){PacketDistributor.sendToServer(new NativeBufferClientFixture.Mode(n));}
 private static boolean ack(Minecraft mc,int n){return mc.level.getBlockState(NativeBufferClientFixture.ACK.east(n)).is(Blocks.STONE);}
 private static NativeBufferEntity owner(Minecraft mc,BlockPos pos){return mc.level.getBlockEntity(pos) instanceof NativeBufferEntity cell?cell:null;}
 private static void use(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false));}
 private static void click(NativeBufferScreen gui,String prefix,int mouse){var button=gui.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith(prefix)).map(w->(Button)w).findFirst().orElseThrow();need(gui.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,mouse),"actual option click "+prefix);}
 private static void atlas(Minecraft mc){for(String name:List.of("matter/casing","resource_ports/power_in_off","resource_ports/power_out_off","resource_ports/fluid_in_off","resource_ports/fluid_out_off","matter/tank_empty","imported/power/battery_cell_bank","reservoir/white")){var sprite=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(net.minecraft.resources.ResourceLocation.parse("vandorlabs:blocks/"+name));need(sprite.contents().name().getPath().equals("blocks/"+name)&&Math.max(sprite.contents().width(),sprite.contents().height())<=128,"stitched Source-size own-storage sprite "+name);}}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeBufferClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==30&&mc.level==null){System.out.println("[vandorlabs][buffer-client] client PASS checks="+checks+" actualSurvival/dedicatedAndConfigurator/materialPicker/previousShapeAndLayout/status100k16k/stitchedSprites/renderedPadding");stage=31;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1600,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen+" casing="+(owner(mc,NativeBufferClientFixture.BATTERY)==null?"missing":owner(mc,NativeBufferClientFixture.BATTERY).casing())+" choice="+choice);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var battery=owner(mc,NativeBufferClientFixture.BATTERY);var tank=owner(mc,NativeBufferClientFixture.TANK);var gui=mc.screen instanceof NativeBufferScreen screen?screen:null;var portGui=mc.screen instanceof NativePortScreen screen?screen:null;
  switch(stage){
   case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().isEmpty()||mc.player.position().distanceToSqr(.5,96,-3)>.01||battery==null||battery.amount()!=370)return;atlas(mc);use(mc,NativeBufferClientFixture.BATTERY);stage=1;}
   case 1->{if(portGui==null||battery.capacity()!=100000||battery.amount()!=370)return;mode(0);portGui.action(22,1);var shape=portGui.control(10);need(portGui.mouseClicked(shape.getX()+shape.getWidth()/2D,shape.getY()+8,1),"same port Housing previous-shape control");choice=ModernFinishCatalog.allChoices().stream().filter(c->c.choice()>=0&&c.entry().general()&&c.entry().visible()&&c.choice()!=battery.casing()).findFirst().orElseThrow().choice();portGui.housingPicker().searchField().setValue(ModernFinishCatalog.option(choice).texture());stage=2;}
   case 2->{if(!ack(mc,0)||battery.blockShape())return;var picker=portGui.housingPicker();int index=picker.rows().indexOf(choice);need(index>=0&&index<picker.visible(),"filtered material picker row visible");var search=picker.searchField();need(portGui.mouseClicked(search.getX()+8,picker.top()+index*22+10,0),"actual material list click");stage=3;}
   case 3->{if(battery.casing()!=choice)return;portGui.onClose();mode(1);stage=4;}
   case 4->{if(!ack(mc,1)||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativeBufferClientFixture.BATTERY);stage=5;}
   case 5->{if(portGui==null||battery.amount()!=370||battery.capacity()!=100000)return;portGui.action(22,1);need(portGui.housingPicker().selected()==choice,"Configurator battery live status and choices");mode(2);capture=true;stage=6;}
   case 6->{if(capture||!ack(mc,2))return;portGui.onClose();mode(3);stage=7;}
   case 7->{if(!ack(mc,3)||!mc.player.getMainHandItem().isEmpty()||mc.player.position().distanceToSqr(2.5,96,-3)>.01)return;use(mc,NativeBufferClientFixture.TANK);stage=8;}
   case 8->{if(gui==null||gui.getMenu().amount()!=5000||gui.getMenu().capacity()!=16000)return;mode(4);click(gui,"Shape:",1);click(gui,"Texture:",1);stage=9;}
   case 9->{if(!ack(mc,4)||!tank.blockShape()||tank.settings().layout!=2)return;gui.onClose();mode(5);stage=10;}
   case 10->{if(!ack(mc,5)||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativeBufferClientFixture.TANK);stage=11;}
   case 11->{if(gui==null||gui.getMenu().amount()!=5000||gui.getMenu().capacity()!=16000)return;mode(6);stage=12;}
   case 12->{if(!ack(mc,6))return;mc.level.disconnect();mc.disconnect();stage=30;}
   default->{}
  }
 }
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!NativeBufferClientFixture.enabled()||!capture||!(event.getScreen() instanceof NativeBufferScreen)&&!(event.getScreen() instanceof NativePortScreen))return;var mc=Minecraft.getInstance();var g=event.getGuiGraphics();if(icons.isEmpty())for(var pos:List.of(NativeBufferClientFixture.BATTERY,NativeBufferClientFixture.TANK))for(boolean block:new boolean[]{false,true}){var owner=owner(mc,pos);var stack=new ItemStack(owner.getBlockState().getBlock());var data=owner.getUpdateTag(mc.level.registryAccess());data.putBoolean("BlockShape",block);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));icons.add(stack);}for(int n=0;n<icons.size();n++){int x=240+n*40;g.fill(x,312,x+24,336,0xff202830);g.renderItem(icons.get(n),x+4,316);}g.flush();drawn=true;}
 @SubscribeEvent public static void screenshot(RenderFrameEvent.Post event)throws Exception{if(!NativeBufferClientFixture.enabled()||!capture||!drawn)return;var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int scale=(int)mc.getWindow().getGuiScale();for(int n=0;n<icons.size();n++){int ink=0,missing=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++){int pixel=image.getPixelRGBA((244+n*40)*scale+x,316*scale+y)&0xffffff;if(pixel==0x302820)continue;ink++;if(pixel==0xff00ff)missing++;need(x>=scale&&x<15*scale&&y>=scale&&y<15*scale,"actual padded storage icon "+n+" at "+x+","+y);}need(ink>30&&missing==0,"visible authored configured storage icon "+n+" ink="+ink+" missing="+missing);}var path=mc.gameDirectory.toPath().resolve("screenshots/native-own-storage.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}capture=false;drawn=false;}
 private NativeBufferClientChecks(){}
}
