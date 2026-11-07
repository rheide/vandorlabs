package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** The action is Minecraft's actual use key; fixture packets only arrange and acknowledge stages. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ScreenChipPlayerChecks {
 private static int stage,ticks,wait;
 private static java.lang.reflect.Method use;
 private static void need(boolean yes,String message){if(!yes)throw new AssertionError("Screen chip client stage="+stage+": "+message);}
 @SuppressWarnings("unchecked") private static BlockEntityType<NativeScreenEntity> type(){return (BlockEntityType<NativeScreenEntity>)(BlockEntityType<?>)BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("vandorlabs:screen_block_fixture"));}
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD) public static final class Binding {
  @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(ScreenChipPlayerFixture.enabled())event.registerBlockEntityRenderer(type(),NativeScreenBlockEntityRenderer::new);}
  @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws Exception{if(ScreenChipPlayerFixture.enabled()&&!NativeScreenClientBindings.installed())NativeScreenLayers.registerShaders(event);}
 }
 private static boolean actor(Minecraft mc,boolean page){var target=page?ScreenChipPlayerFixture.pageTarget():ScreenChipPlayerFixture.trayTarget();var eye=target.add(page?new Vec3(0,1.2,-2):new Vec3(0,2,0));return mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.player.getInventory().selected==0&&mc.player.getOffhandItem().isEmpty()&&mc.player.getEyePosition().distanceToSqr(eye)<.0025;}
 private static boolean hit(Minecraft mc,BlockPos expected){return mc.hitResult instanceof BlockHitResult result&&result.getBlockPos().equals(expected);}
 private static void click(Minecraft mc)throws ReflectiveOperationException{if(use==null){use=Minecraft.class.getDeclaredMethod("startUseItem");use.setAccessible(true);}use.invoke(mc);wait=ticks;}
 private static void ack(int value){PacketDistributor.sendToServer(new ScreenChipPlayerFixture.Ack(value));wait=ticks;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!ScreenChipPlayerFixture.enabled())return;var mc=Minecraft.getInstance();
  if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
  if(stage==7&&mc.level==null){System.out.println("[vandorlabs][screen-chip-player] client PASS actual vanilla remove/Survival insert/nonowner page click");stage=8;mc.stop();return;}
  if(mc.level==null||mc.player==null||mc.gameMode==null)return;
  var tray=ScreenChipPlayerFixture.owner(mc.level,ScreenChipPlayerFixture.TRAY);
  need(++ticks<1200,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" main="+mc.player.getMainHandItem()+" off="+mc.player.getOffhandItem()+" eye="+mc.player.getEyePosition()+" hit="+mc.hitResult+" tray="+(tray==null?"missing":tray.visual()));
  switch(stage){
   case 0->{if(tray==null||tray.chipView()==null||!tray.chipView().reader.occupied()||!actor(mc,false)||!mc.player.getMainHandItem().isEmpty()||!hit(mc,ScreenChipPlayerFixture.TRAY))return;click(mc);stage=1;}
   case 1->{if(tray.chipView().reader.occupied()||!mc.player.getMainHandItem().is(NativeCredentialItems.DATA_CHIP.get())||mc.player.getMainHandItem().getCount()!=1||ticks<wait+10)return;ack(0);stage=2;}
   case 2->{if(!actor(mc,false)||!mc.player.getMainHandItem().is(NativeCredentialItems.DATA_CHIP.get())||mc.player.getMainHandItem().getCount()!=2||!hit(mc,ScreenChipPlayerFixture.TRAY))return;click(mc);stage=3;}
   case 3->{if(!tray.chipView().reader.occupied()||tray.chipView().journal==null||mc.player.getMainHandItem().getCount()!=1||ticks<wait+10)return;ack(1);stage=4;}
   case 4->{if(!actor(mc,true)||!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)||!hit(mc,ScreenChipPlayerFixture.LEAF)||ticks<wait+10)return;var owner=ScreenChipPlayerFixture.owner(mc.level,ScreenChipPlayerFixture.GROUP);need(owner!=null&&owner.chipPage()==1,"joined owner begins on saved page one");click(mc);stage=5;}
   case 5->{var owner=ScreenChipPlayerFixture.owner(mc.level,ScreenChipPlayerFixture.GROUP);var leaf=ScreenChipPlayerFixture.owner(mc.level,ScreenChipPlayerFixture.LEAF);if(owner==null||leaf==null||owner.chipPage()!=0||ticks<wait+10)return;need(leaf.chipPage()==1,"clicked nonowner's saved page remains unchanged");ack(2);stage=6;}
   case 6->{if(!mc.level.getBlockState(ScreenChipPlayerFixture.DONE).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK))return;mc.level.disconnect();mc.disconnect();stage=7;}
   default->{}
  }
 }
}
