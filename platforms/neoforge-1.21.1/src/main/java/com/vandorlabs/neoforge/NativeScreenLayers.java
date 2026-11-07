package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Explicit opt-in shader hook; no client/global registration is installed by this inactive backend. */
public final class NativeScreenLayers {
 private static ShaderInstance content,frame;
 private static final Map<Key,RenderType> TYPES=new LinkedHashMap<>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,RenderType> entry){return size()>4096;}};private record Key(ResourceLocation texture,boolean frame,boolean bias){}
 private NativeScreenLayers(){}
 public static void registerShaders(RegisterShadersEvent event)throws java.io.IOException{
  var resource=ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_content");event.registerShader(new ShaderInstance(event.getResourceProvider(),resource,DefaultVertexFormat.BLOCK),value->{content=value;TYPES.clear();});event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_frame"),DefaultVertexFormat.BLOCK),value->{value.getUniform("AlphaCutoff").set(.01F);frame=value;TYPES.clear();});
 }
 private static final class Types extends RenderType {
  private Types(){super("vandorlabs_screen",DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
  static RenderType make(Key key){float factor=key.frame?-6:-4;var layering=key.bias||key.frame?new LayeringStateShard("vandorlabs_screen_bias",()->{RenderSystem.polygonOffset(factor,factor);RenderSystem.enablePolygonOffset();},()->{RenderSystem.polygonOffset(0,0);RenderSystem.disablePolygonOffset();}):NO_LAYERING;
   return RenderType.create("vandorlabs_screen",DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,1536,true,false,CompositeState.builder().setShaderState(new ShaderStateShard(()->key.frame?frame:content)).setTextureState(new TextureStateShard(key.texture,false,false)).setTransparencyState(key.frame?TRANSLUCENT_TRANSPARENCY:NO_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setLayeringState(layering).setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
  }
 }
 static RenderType type(ResourceLocation texture,boolean frame,boolean bias){if(content==null||NativeScreenLayers.frame==null)throw new IllegalStateException("Screen shaders were not prepared");Key key=new Key(texture,frame,bias);return TYPES.computeIfAbsent(key,Types::make);}
 static RenderType body(){return type(TextureAtlas.LOCATION_BLOCKS,false,false);}
}
