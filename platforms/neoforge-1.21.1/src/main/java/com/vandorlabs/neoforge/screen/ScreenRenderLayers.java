package com.vandorlabs.neoforge.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The legacy coplanar diagonal artwork bias changes depth, never authored geometry. */
final class ScreenRenderLayers {
    private static final Map<ResourceLocation,RenderType> DIAGONAL=new HashMap<>();
    private static final RenderStateShard.LayeringStateShard BIAS=new RenderStateShard.LayeringStateShard("vandorlabs_diagonal_art",()->{RenderSystem.enablePolygonOffset();RenderSystem.polygonOffset(-4,-4);},()->{RenderSystem.polygonOffset(0,0);RenderSystem.disablePolygonOffset();});
    private ScreenRenderLayers(){}
    static RenderType diagonal(ResourceLocation texture){return DIAGONAL.computeIfAbsent(texture,id->RenderType.create("vandorlabs_diagonal_art",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,true,false,RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER).setTextureState(new RenderStateShard.TextureStateShard(id,false,false)).setTransparencyState(RenderStateShard.NO_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY).setLayeringState(BIAS).createCompositeState(true)));}
    static void clear(){DIAGONAL.clear();}
}
