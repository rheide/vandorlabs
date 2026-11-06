package com.vandorlabs.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vandorlabs.tiles.GlassTextures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Title-only shader link/state binding and canonical atlas upload; no world or server is started. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativePanelShaderChecks {
    private static int ticks;private static boolean done;
    private static void need(boolean value,String message){if(!value)throw new AssertionError("Pane shader: "+message);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!"true".equals(System.getenv("VANDOR_PANEL_SHADER_CHECK"))||done)return;var mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(++ticks>2400)throw new AssertionError("Pane shader title timeout screen="+mc.screen+" overlay="+mc.getOverlay());
        if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen))return;
        need(mc.level==null&&mc.getSingleplayerServer()==null,"isolated title without a world/server");
        var field=NativePanelPlaybackRenderer.class.getDeclaredField("shader");field.setAccessible(true);var shader=(ShaderInstance)field.get(null);need(shader!=null&&shader.getId()>0&&GL20.glGetProgrami(shader.getId(),GL20.GL_LINK_STATUS)==GL11.GL_TRUE,"real core shader compiled and linked");
        var offset=shader.getUniform("ChunkOffset");need(offset!=null,"owned block vertex transform");for(int i=0;i<3;i++)need(offset.getFloatBuffer().get(i)==0,"no inherited chunk translation");
        var previous=RenderSystem.getShader();try{
            for(boolean opaque:new boolean[]{false,true}){var type=NativePanelPlaybackRenderer.renderType(TextureAtlas.LOCATION_BLOCKS,opaque);type.setupRenderState();try{need(RenderSystem.getShader()==shader,"custom block/lightmap shader selected");shader.apply();need(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==shader.getId(),"actual GL program bound");need(GL11.glIsEnabled(GL11.GL_BLEND)!=opaque,"exact blending policy");need(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)==opaque,"exact depth write policy");need(!GL11.glIsEnabled(GL11.GL_CULL_FACE),"each physical pane face is double sided");}finally{shader.clear();type.clearRenderState();}}
        }finally{RenderSystem.setShader(()->previous);}
        var frameField=NativePanelPlaybackRenderer.class.getDeclaredField("frameShader");frameField.setAccessible(true);var frame=(ShaderInstance)frameField.get(null);
        need(frame!=null&&frame.getId()>0&&GL20.glGetProgrami(frame.getId(),GL20.GL_LINK_STATUS)==GL11.GL_TRUE,"real frame shader linked");
        need(shader.getUniform("AlphaCutoff")!=null&&shader.getUniform("AlphaCutoff").getFloatBuffer().get(0)==.003F,"pane cutoff retained");
        need(frame.getUniform("AlphaCutoff")!=null&&frame.getUniform("AlphaCutoff").getFloatBuffer().get(0)==.1F,"frame cutoff retained");
        var frameType=NativePanelPlaybackRenderer.frameRenderType();try{frameType.setupRenderState();need(RenderSystem.getShader()==frame,"frame shader selected before pane");frame.apply();need(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==frame.getId(),"frame program bound");need(!GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"opaque frame pass");}finally{frame.clear();frameType.clearRenderState();RenderSystem.setShader(()->previous);}
        var railId=ResourceLocation.fromNamespaceAndPath("vandorlabs","frame-default/double_frame_metal");var rail=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(railId).contents();need(rail.name().equals(railId)&&rail.width()==128&&rail.height()==128,"standard original rail atlas");
        var railHash=MessageDigest.getInstance("SHA-256");var railImage=rail.getOriginalImage();for(int y=0;y<128;y++)for(int x=0;x<128;x++){int pixel=railImage.getPixelRGBA(x,y);for(int shift=0;shift<32;shift+=8)railHash.update((byte)(pixel>>>shift));}
        need(java.util.HexFormat.of().formatHex(railHash.digest()).equals("9dc1c3b9900f666df15bda93d6fd9aa6db2abd0779eb47fe133457a606a5cc45"),"exact original Forge rail pixels");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");HashSet<String> seen=new HashSet<>();int count=0;
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++){
            String name=GlassTextures.texture(shade,1,tint).substring("vandorlabs:blocks/programmable_glass/low/".length());if(!seen.add(name))continue;var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","glass-palette/"+name);var contents=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id).contents();need(contents.name().equals(id)&&contents.width()==128&&contents.height()==128,"exact palette atlas asset "+id);hash.update(name.getBytes(StandardCharsets.UTF_8));var image=contents.getOriginalImage();for(int y=0;y<128;y++)for(int x=0;x<128;x++){int pixel=image.getPixelRGBA(x,y);hash.update((byte)pixel);hash.update((byte)(pixel>>>8));hash.update((byte)(pixel>>>16));hash.update((byte)(pixel>>>24));}count++;
        }
        String pixels=java.util.HexFormat.of().formatHex(hash.digest());need(count==75&&pixels.equals("3cc7bf6bc699d3af743cbd3c881c586e9e36939950f29155ad29e39e2e8c3dbe"),"actual palette upload RGBA "+count+" "+pixels);
        System.out.println("[vandorlabs][panel-shader] title PASS linked/bound block shader; alpha/depth/cull state and75canonical128pxRGBA; no world/server");done=true;mc.stop();
    }
}
