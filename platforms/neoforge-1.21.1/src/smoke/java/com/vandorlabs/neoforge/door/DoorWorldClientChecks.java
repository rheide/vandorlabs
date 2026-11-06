package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.ui.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual player placement/input/dialog/reader/harvest plus world BER culling and a disabled-renderer negative control. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class DoorWorldClientChecks {
 private static int stage,ticks,wait,frames,checks;private static String capture;private static volatile boolean captured;private static java.lang.reflect.Field rendererField;private static Object savedRenderers;
 private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Canonical Door client stage="+stage+": "+why);}
 private static void call(Minecraft mc,String name){try{var method=Minecraft.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(mc);}catch(ReflectiveOperationException error){throw new IllegalStateException("Actual Minecraft input "+name,error);}}
 private static void mode(int operation){PacketDistributor.sendToServer(new DoorWorldClientFixture.Mode(operation));wait=ticks;}
 private static NativeProgrammableDoorEntity tile(Minecraft mc){return mc.level.getBlockEntity(DoorWorldClientFixture.POS) instanceof NativeProgrammableDoorEntity tile?tile:null;}
 private static boolean actor(Minecraft mc,GameType type,Item item){return mc.gameMode.getPlayerMode()==type&&mc.player.getInventory().selected==0&&(item==Items.AIR?mc.player.getMainHandItem().isEmpty():mc.player.getMainHandItem().is(item))&&mc.player.getOffhandItem().isEmpty();}
 private static void click(NativeDoorScreen screen,int id){var button=screen.button(id);need(button!=null&&button.active&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+button.getHeight()/2D,0),"actual dialog button "+id);}
 private static void tab(NativeDoorScreen screen,int page){var pages=SpaceDoorDialog.pages(screen.edit().design());for(int i=0;i<pages.length;i++)if(pages[i]==page){var p=screen.panel();int width=(p.width-22)/pages.length;need(screen.mouseClicked(p.left+12+i*width+width/2D,p.top+37,0)&&screen.page()==page,"actual dialog tab");return;}throw new IllegalStateException("Missing security tab");}
 private static void capture(String name){capture=name;captured=false;frames=0;}
 @SuppressWarnings("unchecked") private static void renderer(Minecraft mc,boolean enable){try{if(rendererField==null){rendererField=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class.getDeclaredField("renderers");rendererField.setAccessible(true);}var dispatcher=mc.getBlockEntityRenderDispatcher();if(!enable){savedRenderers=rendererField.get(dispatcher);var copy=new HashMap<>((Map<Object,Object>)savedRenderers);copy.remove(NativeDoorRegistrations.ENTITY.get());rendererField.set(dispatcher,copy);}else{rendererField.set(dispatcher,savedRenderers);savedRenderers=null;mc.levelRenderer.setSectionDirty(0,DoorWorldClientFixture.POS.getY()>>4,0);}}catch(ReflectiveOperationException error){throw new IllegalStateException("Isolated renderer negative control",error);}}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!DoorWorldClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(stage==20&&mc.level==null){System.out.println("[vandorlabs][door-world-client] client PASS checks="+checks+" actual canonical survival placement/dialog/filter/reader ownership/configured pick/stone mining and upper-only BER bounds; renderer-disabled GPU comparison separate");mc.stop();stage=21;return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;if(!"Programmable Door".equals(NativeDoorRegistrations.ITEM.get().getDescription().getString()))throw new IllegalStateException("Canonical Door locale missing");
  need(++ticks<2400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" pitch="+mc.player.getXRot()+" hit="+mc.hitResult+" screen="+mc.screen);var tile=tile(mc);
  switch(stage){
   case 0->{if(ticks<80||!actor(mc,GameType.SURVIVAL,NativeDoorRegistrations.ITEM.get())||mc.player.getMainHandItem().getCount()!=2||mc.player.getZ()>-2.9)return;if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(DoorWorldClientFixture.POS.below())||hit.getDirection()!=net.minecraft.core.Direction.UP)return;var model=mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0);need(model instanceof NativeDoorItemModel.Selected selected&&selected.modelId().contains("rotating"),"bare canonical icon follows measured rotating item policy");call(mc,"startUseItem");stage=1;wait=ticks;}
   case 1->{if(tile==null||mc.level.getBlockEntity(DoorWorldClientFixture.POS.above())==null||mc.player.getMainHandItem().getCount()!=1||ticks<wait+20)return;need(tile.getType()==NativeDoorRegistrations.ENTITY.get()&&tile.settings().door().sliding&&mc.level.getBlockState(DoorWorldClientFixture.POS).getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL,"actual canonical type/prototype/fluid chunk bridge");need(mc.getBlockEntityRenderDispatcher().getRenderer(tile) instanceof NativeDoorRenderer,"actual production BER binding");mode(0);stage=2;}
   case 2->{if(!actor(mc,GameType.SURVIVAL,VandorLabsBootstrap.CONFIGURIZER.get())||ticks<wait+20)return;if(NativeDoorSelection.update(1)==null)return;call(mc,"startUseItem");stage=3;}
   case 3->{if(!(mc.screen instanceof NativeDoorScreen screen))return;need(screen.getMenu().getType()==NativeDoorRegistrations.MENU.get(),"actual canonical dedicated screen/menu binding");tab(screen,3);click(screen,SpaceDoorDialog.SECURITY);need(screen.edit().securityMode()==2,"source Stay Inserted option");var p=screen.panel();need(screen.mouseClicked(p.controlsX+144,p.top+144,0)&&screen.edit().allowed()==2,"real non-consuming company picker");stage=4;wait=ticks;}
   case 4->{if(tile.settings().securityMode()!=2||tile.settings().securityAllowed()!=2||ticks<wait+20)return;need(mc.player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"UI company sample retains inventory");click((NativeDoorScreen)mc.screen,1);mode(1);stage=5;}
   case 5->{if(mc.screen!=null||!actor(mc,GameType.SURVIVAL,NativeCredentialItems.ACCESS_CARD.get())||mc.player.getMainHandItem().getCount()!=1||ticks<wait+20)return;if(NativeDoorSelection.update(1)==null)return;call(mc,"startUseItem");stage=6;wait=ticks;}
   case 6->{if(!tile.readerProjection().front().visual().occupied()||!tile.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN)||mc.player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())!=0||ticks<wait+20)return;need(tile.readerProjection().front().visual().company()==1&&tile.readerProjection().front().visual().icon(),"sanitized occupied reader visual reaches actual client");mode(2);stage=7;}
   case 7->{if(!actor(mc,GameType.SURVIVAL,Items.AIR)||ticks<wait+20)return;var target=NativeDoorSelection.update(1);if(target==null)return;call(mc,"startUseItem");stage=8;wait=ticks;}
   case 8->{if(tile.readerProjection().front().visual().occupied()||tile.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN)||mc.player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())!=1||ticks<wait+20)return;mode(3);mc.options.fov().set(30);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.hideGui=true;stage=9;}
   case 9->{if(!actor(mc,GameType.SURVIVAL,NativeCredentialItems.ACCESS_CARD.get())||Math.abs(mc.player.getZ()+2)>.03||mc.player.getXRot()>-5||ticks<wait+40)return;var frustum=mc.levelRenderer.getFrustum();if(DoorWorldClientFixture.POS.getY()==95)need(!frustum.isVisible(new AABB(0,80,0,16,96,16)),"boundary view excludes entire lower owner section");need(!frustum.isVisible(new AABB(DoorWorldClientFixture.POS)),"real view excludes unit lower anchor cube");need(frustum.isVisible(new AABB(DoorWorldClientFixture.POS).expandTowards(0,1,0))&&frustum.isVisible(mc.getBlockEntityRenderDispatcher().getRenderer(tile).getRenderBoundingBox(tile)),"actual expanded BER bounds include visible upper body");capture("canonical-upper-positive.png");stage=10;}
   case 10->{if(!captured)return;renderer(mc,false);need(mc.getBlockEntityRenderDispatcher().getRenderer(tile)==null,"renderer-disabled negative control");capture("canonical-upper-negative.png");stage=11;}
   case 11->{if(!captured)return;renderer(mc,true);need(mc.getBlockEntityRenderDispatcher().getRenderer(tile) instanceof NativeDoorRenderer,"renderer restored after isolated negative control");mc.options.hideGui=false;mc.options.fov().set(70);mode(4);stage=12;}
   case 12->{if(!actor(mc,GameType.CREATIVE,Items.AIR)||ticks<wait+30)return;if(NativeDoorSelection.update(1)==null)return;call(mc,"pickBlock");stage=13;wait=ticks;}
   case 13->{if(!actor(mc,GameType.CREATIVE,NativeDoorRegistrations.ITEM.get())||ticks<wait+20)return;var tag=mc.player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();need(tag.getCompound("SpaceDoorSettings").getInt("DoorSecurityMode")==2&&!tag.toString().contains("InsertedToken"),"actual configured pick preserves choices without physical inventory");var model=mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0);need(model instanceof NativeDoorItemModel.Selected selected&&selected.modelId().contains("sliding"),"configured pick icon follows actual world motion");capture("canonical-configured-pick.png");stage=14;}
   case 14->{if(!captured)return;mode(5);stage=15;}
   case 15->{if(!actor(mc,GameType.SURVIVAL,Items.STONE_PICKAXE)||ticks<wait+20)return;var target=NativeDoorSelection.update(1);if(target==null)return;mc.options.keyAttack.setDown(true);call(mc,"startAttack");stage=16;wait=ticks;}
   case 16->{if(!mc.level.getBlockState(DoorWorldClientFixture.POS).isAir()||!mc.level.getBlockState(DoorWorldClientFixture.POS.above()).isAir())return;mc.options.keyAttack.setDown(false);wait=ticks;stage=17;}
   case 17->{if(ticks<wait+25)return;need(mc.player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get())&&mc.player.getInventory().getItem(4).getCount()==1,"returned reader owner survives actual harvest");mode(6);stage=18;}
   case 18->{if(ticks<wait+30)return;mc.level.disconnect();mc.disconnect();stage=20;}
  }
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!DoorWorldClientFixture.enabled()||capture==null||++frames<8)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{captured=true;});}
}
