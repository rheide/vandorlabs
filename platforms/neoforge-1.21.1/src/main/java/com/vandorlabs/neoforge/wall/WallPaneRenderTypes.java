package com.vandorlabs.neoforge.wall;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Legacy pane depth masks around the native unshaded BLOCK/lightmap/fog shader path. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WallPaneRenderTypes {
    private static ShaderInstance shader;
    private static boolean stageActive;
    private static int entryDraw,entryRead;
    private static final RenderStateShard.ShaderStateShard SHADER=new RenderStateShard.ShaderStateShard(()->shader);
    private static final RenderStateShard.OutputStateShard TARGET=new RenderStateShard.OutputStateShard("vandorlabs_wall_pane_target",()->{
        if(!stageActive)throw new IllegalStateException("Wall pane target used outside its render stage");
        var mc=Minecraft.getInstance();if(Minecraft.useShaderTransparency())mc.levelRenderer.getTranslucentTarget().bindWrite(false);
    },WallPaneRenderTypes::restoreTarget);
    private static final RenderType TRANSPARENT=create(false),OPAQUE=create(true);
    private WallPaneRenderTypes(){}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("vandorlabs:wall_pane"),DefaultVertexFormat.BLOCK),value->{shader=value;});}
    private static RenderType create(boolean depth){return RenderType.create("vandorlabs_wall_pane_"+(depth?"depth":"color"),DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,1536,false,false,
            RenderType.CompositeState.builder().setShaderState(SHADER).setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,true)).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP).setOutputState(TARGET).setWriteMaskState(writeMask(depth)).createCompositeState(false));}
    private static RenderStateShard.WriteMaskStateShard writeMask(boolean depth){
        // Native COLOR_DEPTH_WRITE assumes an already-enabled mask; weather can leave it disabled.
        return new RenderStateShard.WriteMaskStateShard(true,depth){
            @Override public void setupRenderState(){super.setupRenderState();com.mojang.blaze3d.systems.RenderSystem.depthMask(depth);}
        };
    }
    static RenderType pane(boolean depth){return depth?OPAQUE:TRANSPARENT;}
    /** Capture the actual entry before any target setup, including distinct read/draw bindings. */
    static void beginStage(){
        if(stageActive)throw new IllegalStateException("Nested wall pane stage");
        entryDraw=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        entryRead=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        stageActive=true;
    }
    static int entryDraw(){return entryDraw;}
    static int entryRead(){return entryRead;}
    /** Output-state cleanup restores the original bindings after every individual batch. */
    static void restoreTarget(){
        if(!stageActive)return;
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,entryDraw);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,entryRead);
    }
    static void endStage(){try{restoreTarget();}finally{stageActive=false;}}

    static void resetChunkOffset(){if(shader==null)throw new IllegalStateException("Wall pane shader unavailable");if(shader.CHUNK_OFFSET!=null)shader.CHUNK_OFFSET.set(0F,0F,0F);}
}
