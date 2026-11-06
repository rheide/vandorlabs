package com.vandorlabs.neoforge.client;
import com.vandorlabs.neoforge.mixin.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class SegmentedUploadMixinClientChecks {
 private static boolean done;
 @SubscribeEvent public static void frame(ScreenEvent.Render.Post event)throws Exception{
  if(done||!"true".equals(System.getenv("VANDOR_SEGMENTED_UPLOAD_MIXIN_CHECK"))||!(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;
  if(!(Minecraft.getInstance().levelRenderer instanceof SegmentedLevelRendererAccessor)||!SegmentedViewAreaInvoker.class.isAssignableFrom(ViewArea.class)||!NativeSegmentedSectionUploads.Section.class.isAssignableFrom(SectionRenderDispatcher.RenderSection.class)||!NativeSegmentedSectionUploads.Task.class.isAssignableFrom(Class.forName("net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")))throw new AssertionError("Client section/task mixins not applied");
  if(NativeSegmentedSectionUploads.tracked()!=0)throw new AssertionError("Title must retain no section/world tracking");done=true;System.out.println("[vandorlabs][segmented-upload-mixins] title PASS LevelRenderer/ViewArea/current-task/upload-completion roles applied; no handoff/world claim");Minecraft.getInstance().stop();
 }
}
