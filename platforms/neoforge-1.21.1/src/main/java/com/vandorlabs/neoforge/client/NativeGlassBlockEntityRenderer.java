package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.NativeFlatGlassRenderer;
import com.vandorlabs.neoforge.glass.NativeGlassEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

/** One prepared publication per draw; terrain owns the default authored rails. */
public final class NativeGlassBlockEntityRenderer implements BlockEntityRenderer<NativeGlassEntity> {
 public NativeGlassBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
 @Override public void render(NativeGlassEntity owner,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
  var bake=NativeFlatGlassRenderer.currentBake();if(bake==null||owner.getLevel()==null)return;
  var publication=owner.publication();NativeFlatGlassRenderer.renderAt(bake,publication.visual(),poses,buffers,light,overlay);
 }
}
