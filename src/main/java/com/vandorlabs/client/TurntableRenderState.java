package com.vandorlabs.client;

import net.minecraft.client.renderer.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Restore both the driver state and Minecraft's cached state after nested renderers. */
final class TurntableRenderState implements AutoCloseable {
    private final boolean lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final float u=OpenGlHelper.lastBrightnessX,v=OpenGlHelper.lastBrightnessY;
    TurntableRenderState(){GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);}
    @Override public void close(){GL11.glPopAttrib();
        if(lighting){GlStateManager.disableLighting();GlStateManager.enableLighting();}else{GlStateManager.enableLighting();GlStateManager.disableLighting();}
        if(cull){GlStateManager.disableCull();GlStateManager.enableCull();}else{GlStateManager.enableCull();GlStateManager.disableCull();}
        if(blend){GlStateManager.disableBlend();GlStateManager.enableBlend();}else{GlStateManager.enableBlend();GlStateManager.disableBlend();}
        if(alpha){GlStateManager.disableAlpha();GlStateManager.enableAlpha();}else{GlStateManager.enableAlpha();GlStateManager.disableAlpha();}
        if(depth){GlStateManager.disableDepth();GlStateManager.enableDepth();}else{GlStateManager.enableDepth();GlStateManager.disableDepth();}
        GlStateManager.depthMask(!mask);GlStateManager.depthMask(mask);GlStateManager.tryBlendFuncSeparate(1,0,1,0);GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);
        GlStateManager.bindTexture(0);GlStateManager.bindTexture(texture);GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,u,v);
    }
}
