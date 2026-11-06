package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.client.NativeSegmentedSectionUploads;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
public abstract class SegmentedRebuildTaskMixin implements NativeSegmentedSectionUploads.Task {
 @Shadow(remap=false) @Final private SectionRenderDispatcher.RenderSection this$1;
 @Unique private NativeSegmentedSectionUploads.Publication vandorlabs$publication;
 @Unique private volatile Object vandorlabs$uploaded;
 public void vandorlabs$terrainPublication(NativeSegmentedSectionUploads.Publication publication){vandorlabs$publication=publication;}
 public NativeSegmentedSectionUploads.Publication vandorlabs$terrainPublication(){return vandorlabs$publication;}
 public Object vandorlabs$terrainUploaded(){return vandorlabs$uploaded;}
 public void vandorlabs$terrainUploaded(Object compiled){vandorlabs$uploaded=compiled;}
 @Inject(method="doTask",at=@At("RETURN"))
 private void vandorlabs$completion(SectionBufferBuilderPack buffers,CallbackInfoReturnable<CompletableFuture<?>> callback){
  if(vandorlabs$publication==null)return;
  var section=(NativeSegmentedSectionUploads.Section)this$1;
  if(!NativeSegmentedSectionUploads.current(section,vandorlabs$publication))return;
  callback.getReturnValue().whenComplete((result,failure)->NativeSegmentedSectionUploads.completed(section,this,result,failure));
 }
}
