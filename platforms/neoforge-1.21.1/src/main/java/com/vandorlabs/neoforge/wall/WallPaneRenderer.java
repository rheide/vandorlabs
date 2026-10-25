package com.vandorlabs.neoforge.wall;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Visible BERs enqueue frozen geometry; panes draw after water and weather, as in Forge pass1. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class WallPaneRenderer implements BlockEntityRenderer<WallEntity> {
    private record Draw(WallRenderSnapshot snapshot,PoseStack.Pose pose,int light){}
    private static final Map<WallEntity,Draw> DRAWS=new LinkedHashMap<>();
    private static long draws,vertices,stateChecks;
    private static int lastBeforeTarget,lastAfterTarget;
    private static boolean lastFabulous;
    public WallPaneRenderer(BlockEntityRendererProvider.Context context){}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(WallRegistration.ENTITY.get(),WallPaneRenderer::new);}
    }
    @Override public void render(WallEntity entity,float partialTick,PoseStack pose,MultiBufferSource buffers,int packedLight,int overlay){
        var snapshot=entity.wallRenderSnapshot();if(snapshot.glass.isEmpty()||entity.getLevel()==null)return;
        var level=entity.getLevel();var pos=entity.getBlockPos();int own=(level.getBrightness(LightLayer.SKY,pos)<<20)|(level.getBrightness(LightLayer.BLOCK,pos)<<4);
        DRAWS.put(entity,new Draw(snapshot,pose.last().copy(),own));
    }
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())DRAWS.clear();}
    @SubscribeEvent public static void stage(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){DRAWS.clear();return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER||DRAWS.isEmpty())return;
        var mc=Minecraft.getInstance();boolean oldDepth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);boolean inspect=Boolean.getBoolean("vandorlabs.modernClientSmoke");
        WallPaneRenderTypes.beginStage();
        int before=WallPaneRenderTypes.entryDraw(),beforeRead=WallPaneRenderTypes.entryRead();float[] fog=inspect?RenderSystem.getShaderFogColor().clone():null;var modelView=inspect?new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()):null;
        var buffers=mc.renderBuffers().bufferSource();net.minecraft.client.renderer.RenderType active=null;
        try{
            WallPaneGpuChecks.before();
            WallPaneRenderTypes.resetChunkOffset();var atlas=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            for(var draw:DRAWS.values())for(var face:draw.snapshot.glass){var type=WallPaneRenderTypes.pane(face.depthWrite());if(type!=active){if(active!=null)buffers.endBatch(active);active=type;}var buffer=buffers.getBuffer(type);var sprite=atlas.apply(face.texture());emit(face,draw.pose,draw.light,sprite,buffer);vertices+=4;}
            if(active!=null)buffers.endBatch(active);draws+=DRAWS.size();
            WallPaneGpuChecks.after();
        }finally{
            DRAWS.clear();try{WallPaneRenderTypes.endStage();}finally{RenderSystem.depthMask(oldDepth);}
        }
        if(inspect){lastBeforeTarget=before;lastAfterTarget=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);lastFabulous=Minecraft.useShaderTransparency();stateChecks++;if(before!=lastAfterTarget||beforeRead!=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)||GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)!=oldDepth||!java.util.Arrays.equals(fog,RenderSystem.getShaderFogColor())||!modelView.equals(RenderSystem.getModelViewMatrix()))throw new IllegalStateException("Wall pane render stage leaked target/depth/fog/model-view state");}
    }
    static void emit(WallRenderSnapshot.Face face,PoseStack.Pose pose,int light,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,VertexConsumer target){
        int color=face.argb();for(var p:face.vertices())target.addVertex(pose,(float)p.x(),(float)p.y(),(float)p.z()).setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,face.opacity()).setUv(sprite.getU((float)p.u()),sprite.getV((float)p.v())).setUv2(light&65535,light>>>16).setNormal(pose,face.nx(),face.ny(),face.nz());
    }
    public static long draws(){return draws;}
    public static long vertices(){return vertices;}
    public static long stateChecks(){return stateChecks;}
    public static boolean lastFabulous(){return lastFabulous;}
    public static int lastBeforeTarget(){return lastBeforeTarget;}
    public static int lastAfterTarget(){return lastAfterTarget;}
}
