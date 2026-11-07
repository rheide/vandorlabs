package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.client.NativeGlassBlockEntityRenderer;
import com.vandorlabs.neoforge.fluid.client.NativeFluidModel;
import java.util.List;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual private chunk and BER dispatch, with independent disabled-pass controls and reload. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class GlassWorldClientChecks {
 private static volatile boolean terrain=true,panes=true,captured;private static int stage,ticks,wait,checks;private static long draws;private static String capture;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new IllegalStateException("Glass world stage="+stage+" "+text);}
 private static final class Empty extends BakedModelWrapper<BakedModel>{Empty(BakedModel source){super(source);}@Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState s,Direction side,RandomSource r,ModelData d,RenderType layer){return List.of();}}
 private static final class Gate extends BakedModelWrapper<BakedModel>{Gate(BakedModel source){super(source);}@Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState s,Direction side,RandomSource r,ModelData d,RenderType layer){return terrain?super.getQuads(s,side,r,d,layer):List.of();}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Binding {
  @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){if(GlassWorldFixture.enabled())e.registerBlockEntityRenderer(GlassWorldFixture.TYPE,context->{var delegate=new NativeGlassBlockEntityRenderer(context);return (owner,partial,pose,buffers,light,overlay)->{if(panes){draws++;delegate.render(owner,partial,pose,buffers,light,overlay);}};});}
  @SubscribeEvent(priority=EventPriority.LOWEST)public static void models(ModelEvent.ModifyBakingResult e){if(!GlassWorldFixture.enabled())return;var bake=NativeFlatGlassRenderer.prepare(e.getModelBakery(),e.getModels(),e.getTextureGetter(),null,Minecraft.getInstance().getResourceManager());need(bake!=null,"private authored bake");var missing=e.getModels().get(ModelBakery.MISSING_MODEL_VARIANT);if(missing==null)missing=e.getModels().values().iterator().next();var model=new Gate(NativeFluidModel.wrap(new NativeFlatGlassModel(new Empty(missing),bake),e.getTextureGetter()));for(var state:GlassWorldFixture.BLOCK.getStateDefinition().getPossibleStates())e.getModels().put(BlockModelShaper.stateToModelLocation(state),model);}
 }
 private static void snapshot(String name){capture=name;captured=false;wait=0;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!GlassWorldFixture.enabled()||stage==99)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}
  if(stage==90&&mc.level==null){need(NativeFlatGlassRenderer.currentBake().retainedBytes()==0,"world unload retires draw cache");System.out.println("[vandorlabs][glass-world] client PASS checks="+checks+" actualChunk/BER/joined/selected/opaque/bare/reload/unload; private software only");stage=99;mc.stop();return;}
  if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);if(mc.screen!=null)return;
  if(stage==0){var owner=mc.level.getBlockEntity(GlassWorldFixture.BASE);if(!(owner instanceof NativeGlassEntity host)||host.visual().layout().columns()!=3||host.visual().layout().rows()!=2||NativeFlatGlassRenderer.currentBake()==null)return;need(host.visual().authoredFrame(),"default authored terrain frame");need(host.visual().layout().column()==0&&host.visual().layout().row()==0,"bottom-left Source joined coordinates");mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);if(++wait<200)return;need(draws>0,"real BER dispatched");snapshot("glass-world-positive.png");stage=1;return;}
  if(!captured)return;switch(stage){
   case 1->{panes=false;mc.levelRenderer.allChanged();snapshot("glass-world-no-ber.png");stage=2;}
   case 2->{panes=true;terrain=false;mc.levelRenderer.allChanged();snapshot("glass-world-no-terrain.png");stage=3;}
   case 3->{terrain=true;mc.reloadResourcePacks();capture=null;captured=false;wait=0;stage=4;}
   case 5->{mc.disconnect();stage=90;}
  }
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(!GlassWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(stage==4&&mc.level!=null&&mc.getOverlay()==null&&++wait>60){snapshot("glass-world-reload.png");stage=5;}if(capture==null||++wait<45)return;String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);}
 private GlassWorldClientChecks(){}
}
