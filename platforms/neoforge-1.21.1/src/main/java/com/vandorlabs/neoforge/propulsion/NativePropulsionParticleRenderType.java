package com.vandorlabs.neoforge.propulsion;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
/** Source depth-writing blended sheet, rendered before translucent blocks with the existing 1/255 cutoff. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePropulsionParticleRenderType implements ParticleRenderType {
 public static final NativePropulsionParticleRenderType INSTANCE=new NativePropulsionParticleRenderType();private static ShaderInstance shader;
 private NativePropulsionParticleRenderType(){}
 @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{if(NativePropulsionResources.active())event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("vandorlabs:propulsion_particle"),DefaultVertexFormat.PARTICLE),value->shader=value);}
 @Override public BufferBuilder begin(Tesselator tessellator,TextureManager textures){RenderSystem.depthMask(true);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);RenderSystem.enableBlend();RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);RenderSystem.setShader(()->shader==null?GameRenderer.getParticleShader():shader);return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);}
 @Override public boolean isTranslucent(){return false;}
 @Override public String toString(){return "VANDOR_PROPULSION_SOURCE";}
}
