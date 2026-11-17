package com.vandorlabs.neoforge.appliances;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.neoforge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Chunk-batched housings with only the reader, small button and retained document drawn dynamically. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeApplianceClient {
 private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath("vandorlabs","block/appliance_top_frame");
 @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(NativeApplianceBindings.ENTITY,Renderer::new);}
 @SubscribeEvent public static void menus(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e){e.register(NativeApplianceBindings.MENU,NativeApplianceScreen::new);}
 @SubscribeEvent public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e){e.register(ModelResourceLocation.standalone(FRAME));}
 private static final class ButtonClock {long sequence=Long.MIN_VALUE;double started=-100;}
 private static final class Renderer implements BlockEntityRenderer<NativeApplianceEntity>{
  private final com.vandorlabs.animation.VisualClock clock=new com.vandorlabs.animation.VisualClock();
  private final java.util.Map<Object,java.util.LinkedHashMap<net.minecraft.core.BlockPos,ButtonClock>> buttons=new java.util.WeakHashMap<>();
  Renderer(BlockEntityRendererProvider.Context ignored){}
  private boolean pressed(NativeApplianceEntity t){double now=clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused());var entries=buttons.computeIfAbsent(t.getLevel(),k->new java.util.LinkedHashMap<>());var motion=entries.computeIfAbsent(t.getBlockPos(),k->new ButtonClock());if(motion.sequence!=t.buttonSequence()){boolean first=motion.sequence==Long.MIN_VALUE;motion.sequence=t.buttonSequence();motion.started=!first||t.pressed()?now:now-8;}if(entries.size()>2048)entries.remove(entries.keySet().iterator().next());return now-motion.started<8;}
  private static void model(net.minecraft.world.level.block.state.BlockState state,PoseStack p,MultiBufferSource b,int light,int overlay){var mc=Minecraft.getInstance();mc.getBlockRenderer().getModelRenderer().renderModel(p.last(),b.getBuffer(RenderType.cutout()),state,mc.getBlockRenderer().getBlockModel(state),1,1,1,light,overlay,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.cutout());}
  @Override public void render(NativeApplianceEntity t,float partial,PoseStack p,MultiBufferSource b,int light,int overlay){if(t.inert())return;var mc=Minecraft.getInstance();if(t.getLevel()!=null)light=LevelRenderer.getLightColor(t.getLevel(),t.getBlockPos().relative(t.getBlockState().getValue(NativeApplianceBlock.FACING)));p.pushPose();try{p.translate(.5,0,.5);p.mulPose(Axis.YP.rotationDegrees(-t.getBlockState().getValue(NativeApplianceBlock.FACING).toYRot()+180));p.translate(-.5,0,-.5);p.pushPose();p.translate(.25,.25,-1.015);var bake=NativeDoorRenderer.currentReaderBake();if(bake!=null){var target=b.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));bake.frame(true,Direction.NORTH,0,p.last(),target,light,overlay,false);bake.frame(true,Direction.NORTH,0,p.last(),target,light,overlay,true);}var visual=t.chipVisual();var motion=t.chipMotion();var sampled=NativeReaderVisualClock.sample(t.getLevel(),t.getBlockPos(),0,visual,motion);NativeReaderRenderer.contents(visual,motion,sampled.age(),sampled.rejected(),Direction.NORTH,0,p,b,light,overlay);p.popPose();p.pushPose();p.translate(.25,.75,-.02);p.scale(.5f,.5f,.5f);p.translate(-.5,-.5,-1);model(VandorLabsBootstrap.PUSH_BUTTON.get().defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.NORTH).setValue(NativeMountedSwitchBlock.ON,pressed(t)),p,b,light,overlay);p.popPose();if(t.scanner()){if(t.getLevel()!=null)light=LevelRenderer.getLightColor(t.getLevel(),t.getBlockPos().above());var frame=mc.getModelManager().getModel(ModelResourceLocation.standalone(FRAME));mc.getBlockRenderer().getModelRenderer().renderModel(p.last(),b.getBuffer(RenderType.cutout()),null,frame,1,1,1,light,overlay,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.cutout());var original=t.frameStack();if(!original.isEmpty()){p.pushPose();var id=original.get(net.minecraft.core.component.DataComponents.MAP_ID);var data=original.is(Items.FILLED_MAP)&&t.getLevel()!=null?MapItem.getSavedData(original,t.getLevel()):null;if(id!=null&&data!=null){p.translate(.125,1.041,.125);p.mulPose(Axis.XP.rotationDegrees(90));p.scale(.75f/128,.75f/128,.75f/128);mc.gameRenderer.getMapRenderer().render(p,b,id,data,true,light);}else{p.translate(.5,1.047,.5);p.mulPose(Axis.XP.rotationDegrees(-90));p.scale(.65f,.65f,.65f);mc.getItemRenderer().renderStatic(original,ItemDisplayContext.FIXED,light,overlay,p,b,t.getLevel(),0);}p.popPose();}}}finally{p.popPose();}}
 }
 private NativeApplianceClient(){}
}
