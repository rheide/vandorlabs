package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.client.NativeTrapdoorBlockRenderer;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import com.vandorlabs.neoforge.fluid.client.NativeFluidModel;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Real section build and upload, plus an isolated fluid-only disabled negative and resource reload. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TrapdoorFluidWorldClientChecks {
    private static volatile boolean disabled;
    private static int stage,ticks,wait,frames,checks;private static String capture;private static volatile boolean captured;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Wet Trapdoor client stage="+stage+": "+message);}
    private static final class Empty extends BakedModelWrapper<BakedModel> {
        Empty(BakedModel parent){super(parent);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return List.of();}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return List.of();}
    }
    private static final class Gate extends BakedModelWrapper<BakedModel> {
        Gate(BakedModel parent){super(parent);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return disabled?List.of():super.getQuads(state,side,random,data,layer);}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Binding {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(TrapdoorFluidWorldFixture.enabled())event.registerBlockEntityRenderer(TrapdoorFluidWorldFixture.TYPE,NativeTrapdoorBlockRenderer::new);}
        @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){if(!TrapdoorFluidWorldFixture.enabled())return;for(var state:TrapdoorFluidWorldFixture.BLOCK.getStateDefinition().getPossibleStates()){var key=net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(TrapdoorFluidWorldFixture.id(),state);var housing=event.getModels().get(key);if(housing==null)housing=event.getModels().values().iterator().next();event.getModels().put(key,new Gate(NativeFluidModel.wrap(new Empty(housing),event.getTextureGetter())));}}
    }
    private static void capture(String name){capture=name;captured=false;frames=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!TrapdoorFluidWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(stage==8&&mc.level==null){System.out.println("[vandorlabs][trapdoor-fluid-world] client PASS checks="+checks+" actual wet71710-cell host/section fluid model/native BER/disabled-fluid negative/resource reload/no Cartesian faces");mc.stop();stage=9;return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1600,"timeout mode="+mc.gameMode.getPlayerMode()+" position="+mc.player.position()+" screen="+mc.screen);var owner=mc.level.getBlockEntity(TrapdoorFluidWorldFixture.POS) instanceof NativeTrapdoorEntity tile?tile:null;
        switch(stage){
            case 0->{if(ticks<80||owner==null||!owner.open()||owner.fluidRenderSnapshot().faces.isEmpty()||!mc.gameMode.getPlayerMode().isCreative()||!mc.player.getMainHandItem().isEmpty()||!mc.player.getAbilities().flying)return;mc.options.hideGui=true;need(owner.fluidVolumeSnapshot().cells().size()==71710,"actual client retains Source detailed physical profile");need(owner.fluidSurfaceRegions().surfaces().size()==6&&!owner.fluidVolumeSnapshot().facesMaterialized(),"client prepares six analytic surfaces without Cartesian faces");need(mc.getBlockEntityRenderDispatcher().getRenderer(owner) instanceof NativeTrapdoorBlockRenderer,"actual native BER bound to private housing");need(owner.getModelData().get(NativeFluidRenderSnapshot.DATA)==owner.fluidRenderSnapshot(),"worker ModelData uses prepared immutable faces");capture("wet-trapdoor-positive.png");stage=1;}
            case 1->{if(!captured)return;disabled=true;mc.levelRenderer.allChanged();wait=ticks;stage=2;}
            case 2->{if(ticks<wait+80)return;capture("wet-trapdoor-disabled.png");stage=3;}
            case 3->{if(!captured)return;disabled=false;mc.reloadResourcePacks();wait=ticks;stage=4;}
            case 4->{if(mc.getOverlay()!=null||ticks<wait+100||owner.fluidRenderSnapshot().faces.isEmpty())return;need(!owner.fluidVolumeSnapshot().facesMaterialized(),"resource reload retains analytic path");capture("wet-trapdoor-reloaded.png");stage=5;}
            case 5->{if(!captured)return;mc.level.disconnect();mc.disconnect();stage=8;}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!TrapdoorFluidWorldFixture.enabled()||capture==null||++frames<12)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);}
}
