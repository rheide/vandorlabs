package com.vandorlabs.neoforge.credentials;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Standalone contents only: the mounted terrain model owns the authored frame and pedestal. */
public final class NativeCredentialReaderRenderer implements BlockEntityRenderer<NativeCredentialReaderEntity> {
    private final Matrix4f[] mounts=new Matrix4f[3072];
    private final PoseStack scratch=new PoseStack();
    private boolean rendering;
    public NativeCredentialReaderRenderer(BlockEntityRendererProvider.Context context){}
    private Matrix4f mount(NativeCredentialReaderEntity tile,NativeCredentialReaderEntity.Visual visual){
        var settings=visual.mount();if(settings.height==0&&settings.tilt==0)return null;
        var state=tile.getBlockState();int rotation=NativeMountedControlGeometry.rotation(state,visual.rotation());int pose=settings.height+4*(settings.tilt+4*settings.direction);int key=pose+64*(rotation+4*(state.getValue(NativeMountedSwitchBlock.FACING).get3DDataValue()+6*(tile.chip()?1:0)));
        var cached=mounts[key];if(cached!=null)return cached;
        var transform=NativeMountedControlGeometry.transform(state,settings,rotation);var origin=transform.transform(0,0,0);var x=transform.transform(1,0,0).subtract(origin);var y=transform.transform(0,1,0).subtract(origin);var z=transform.transform(0,0,1).subtract(origin);
        cached=new Matrix4f((float)x.x,(float)x.y,(float)x.z,0,(float)y.x,(float)y.y,(float)y.z,0,(float)z.x,(float)z.y,(float)z.z,0,(float)origin.x,(float)origin.y,(float)origin.z,1);mounts[key]=cached;return cached;
    }
    @Override public void render(NativeCredentialReaderEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();var visual=tile.visual();if(visual.inert()||tile.getLevel()==null)return;var state=tile.getBlockState();if(!(state.getBlock() instanceof NativeCredentialReaderBlock))return;
        var clock=NativeReaderVisualClock.sample(tile.getLevel(),tile.getBlockPos(),0,visual.contents(),visual.motion());var matrix=mount(tile,visual);
        if(rendering)throw new IllegalStateException("Reentrant standalone reader draw");rendering=true;try{scratch.last().pose().set(poses.last().pose());scratch.last().normal().set(poses.last().normal());if(matrix!=null)scratch.mulPose(matrix);NativeReaderRenderer.contents(visual.contents(),visual.motion(),clock.age(),clock.rejected(),state.getValue(NativeMountedSwitchBlock.FACING),visual.rotation(),scratch,buffers,light,overlay);}finally{rendering=false;}
    }
    @Override public AABB getRenderBoundingBox(NativeCredentialReaderEntity tile){var p=tile.getBlockPos();return new AABB(p.getX()-.4,p.getY()-.4,p.getZ()-.4,p.getX()+1.4,p.getY()+1.4,p.getZ()+1.4);}
}
