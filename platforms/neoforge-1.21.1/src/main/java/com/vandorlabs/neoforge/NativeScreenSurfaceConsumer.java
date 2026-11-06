package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4fc;

/** Dynamic prepared content. The cached body never retains controls, journal payloads or hosts. */
@FunctionalInterface
public interface NativeScreenSurfaceConsumer {
 /** content is Source pixel space after frame inset; ownerPoses has no pixel transform applied.
  * pixelToOwner is a read-only bake-owned affine. World access belongs to the outer caller. */
 void emit(NativeScreenRenderPlan.Binding binding,NativeScreenEntity.Visual currentVisual,ScreenFamily.State state,int slot,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int packedLight,int overlay,long controlTime);
 NativeScreenSurfaceConsumer MISSING=(binding,visual,state,slot,content,transform,poses,buffers,light,overlay,time)->{throw new IllegalStateException("Screen "+binding+" emitter is not installed");};
}
