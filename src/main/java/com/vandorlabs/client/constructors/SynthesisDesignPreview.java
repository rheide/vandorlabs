package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.FrameMachineDesign;
import com.vandorlabs.client.TurntableRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** The same full-detail, resource-reload-aware atlas mesh serves GUI and world previews. */
public final class SynthesisDesignPreview {
    public static void render(String design,float angle){
        if(!UniversalConstructorModelCache.isReady())return;
        FrameMachineDesign spec=FrameMachineDesign.get(design);
        String path=spec.state_models==null?"complete.obj":spec.state_models[0];
        GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.enableAlpha();GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);
            GlStateManager.rotate(angle,0,1,0);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,DefaultVertexFormats.BLOCK);
            UniversalConstructorModelCache.get(design,path).emit(buffer,-spec.width/2D,-spec.height/2D,-spec.depth/2D,EnumFacing.NORTH,spec.width,spec.depth,0,0,0,15728880);
            Tessellator.getInstance().draw();
        }finally{Tessellator.getInstance().getBuffer().setTranslation(0,0,0);GlStateManager.popMatrix();}
    }
    private SynthesisDesignPreview(){}
}
