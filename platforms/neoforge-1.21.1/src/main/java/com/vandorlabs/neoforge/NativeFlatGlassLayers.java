package com.vandorlabs.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Source flat selected frames blend while retaining depth; bent outlines keep the existing opaque frame layer. */
final class NativeFlatGlassLayers extends RenderType {
 private NativeFlatGlassLayers(){super("flat_glass_layers",com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
 private static ShaderInstance shader;
 private static final TransparencyStateShard SOURCE_BLEND=new TransparencyStateShard("flat_glass_source_blend",()->{RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ZERO);},RenderSystem::disableBlend);
 static final RenderType FRAME=RenderType.create("flat_glass_selected_frame",com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,true,false,CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false)).setTransparencyState(SOURCE_BLEND).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
 private static final Map<ResourceLocation,RenderType> TRANSPARENT_MEDIA=new HashMap<>();
 static synchronized RenderType media(ResourceLocation texture,boolean opaque){return opaque?NativePanelPlaybackRenderer.renderType(texture,true):TRANSPARENT_MEDIA.computeIfAbsent(texture,id->NativePanelPlaybackRenderer.renderType(id,false));}
 static void shaders(RegisterShadersEvent event)throws java.io.IOException {event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("vandorlabs:flat_glass_frame"),com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK),ready->shader=ready);}
 static void buffers(net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent event){var registered=new HashSet<ResourceLocation>();for(var entry:ModernFinishCatalog.ENTRIES){var descriptor=entry.playback();if(descriptor==null)continue;for(String texture:new String[]{descriptor.staticTexture(),descriptor.offTexture(),descriptor.animationTexture()})if(texture!=null){var id=ResourceLocation.parse(texture);if(registered.add(id))event.registerRenderBuffer(media(id,false));}}}
}
