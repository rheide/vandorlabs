package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.client.NativeSegmentedSectionUploads;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SegmentedRenderSectionMixin implements NativeSegmentedSectionUploads.Section {
 @Unique private volatile Object vandorlabs$terrainTask;
 @Inject(method="createCompileTask",at=@At("RETURN"))
 private void vandorlabs$created(RenderRegionCache cache,CallbackInfoReturnable<Object> callback){
  Object task=callback.getReturnValue();vandorlabs$terrainTask=task;
  if(task instanceof NativeSegmentedSectionUploads.Task tracked)tracked.vandorlabs$terrainPublication(NativeSegmentedSectionUploads.publication(this));
 }
 @Inject(method="cancelTasks",at=@At("HEAD"))
 private void vandorlabs$cancelled(CallbackInfoReturnable<Boolean> callback){vandorlabs$terrainTask=null;}
 public BlockPos vandorlabs$terrainOrigin(){return ((SectionRenderDispatcher.RenderSection)(Object)this).getOrigin();}
 public Object vandorlabs$terrainCompiled(){return ((SectionRenderDispatcher.RenderSection)(Object)this).getCompiled();}
 public Object vandorlabs$terrainTask(){return vandorlabs$terrainTask;}
 public boolean vandorlabs$terrainDirty(){return ((SectionRenderDispatcher.RenderSection)(Object)this).isDirty();}
}
