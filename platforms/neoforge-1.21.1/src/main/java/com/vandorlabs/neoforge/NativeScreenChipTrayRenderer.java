package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import com.vandorlabs.screen.ScreenChipMount;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.*;
import org.joml.Matrix4f;

/** Source-oriented integrated tray. Inputs are immutable public metadata; physical tokens are never retained. */
public final class NativeScreenChipTrayRenderer {
 private NativeScreenChipTrayRenderer(){}
 private static final Map<ScreenChipMount.Frame,Matrix4f> POSES=new IdentityHashMap<>();
 private static final RenderType FRAME_LAYER=RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
 private static final class Scratch {final PoseStack poses=new PoseStack();boolean busy;}
 private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
 private static final double CHIP_SIZE=6D/16;
 private static final int MAX_POSES=7*6*2*2*3;
 private static Matrix4f pose(ScreenChipMount.Frame frame){var found=POSES.get(frame);if(found!=null)return found;if(POSES.size()>=MAX_POSES)throw new IllegalStateException("Screen chip tray pose budget");float scale=(float)(Math.min(frame.width,frame.height)/CHIP_SIZE);var next=new Matrix4f().translation((float)frame.center.x,(float)frame.center.y,(float)frame.center.z).mul(new Matrix4f((float)frame.u.x,(float)frame.u.y,(float)frame.u.z,0,(float)frame.v.x,(float)frame.v.y,(float)frame.v.z,0,(float)-frame.normal.x,(float)-frame.normal.y,(float)-frame.normal.z,0,0,0,0,1)).scale(scale).translate(-.5F,-.5F,-1);POSES.put(frame,next);return next;}
 /** Copies the cached read-only affine for independent Source comparison; no mutable cache value escapes. */
 public static void writePose(ScreenChipMount.Frame frame,Matrix4f out){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();out.set(pose(Objects.requireNonNull(frame)));}
 public static long retainedBytes(){return 512+POSES.size()*256L;}
 public static void render(NativeReaderRenderer.Bake bake,ScreenChipMount.Frame frame,NativeTokenReader.Visual visual,NativeTokenReader.Motion motion,Object world,BlockPos owner,PoseStack poses,MultiBufferSource buffers,int light,int overlay){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();var clock=NativeReaderVisualClock.sample(world,owner,2,visual,motion);renderAt(bake,frame,visual,motion,clock.age(),clock.rejected(),poses,buffers,light,overlay);}
 /** Explicit clock sample supports deterministic captures and callers already sharing a pause-aware clock. */
 public static void renderAt(NativeReaderRenderer.Bake bake,ScreenChipMount.Frame frame,NativeTokenReader.Visual visual,NativeTokenReader.Motion motion,float age,boolean rejected,PoseStack poses,MultiBufferSource buffers,int light,int overlay){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();if(!visual.chip())throw new IllegalArgumentException("Screen chip tray requires chip metadata");var scratch=SCRATCH.get();if(scratch.busy)throw new IllegalStateException("Reentrant Screen chip tray draw");scratch.busy=true;var local=scratch.poses;try{local.last().pose().set(poses.last().pose());local.last().normal().set(poses.last().normal());local.mulPose(pose(frame));var target=buffers.getBuffer(FRAME_LAYER);bake.frame(true,Direction.NORTH,0,local.last(),target,light,overlay,false);bake.frame(true,Direction.NORTH,0,local.last(),target,light,overlay,true);NativeReaderRenderer.contents(visual,motion,age,rejected,Direction.NORTH,0,local,buffers,light,overlay);}finally{scratch.busy=false;}}
}
