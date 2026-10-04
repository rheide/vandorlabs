package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Opt-in benchmark checks for the GL state returned by the original and prepared paths. */
final class DoorDrawRuntimeChecks {
    private DoorDrawRuntimeChecks() { }
    static void run(Collection<DoorRenderModels.Entry> entries) {
        RenderItem renderer=Minecraft.getMinecraft().getRenderItem();int prepared=0,checks=0;
        for(DoorRenderModels.Entry entry:entries) {
            if(entry.plan(entry.model)!=null)prepared++;
            for(boolean opaque:new boolean[]{false,true})for(int state=0;state<4;state++) {
                reset(state);
                if(opaque)renderer.renderItem(entry.stack,ItemCameraTransforms.TransformType.NONE);else renderer.renderItem(entry.stack,entry.model);
                Snapshot expected=new Snapshot();
                reset(state);
                if(opaque)DoorItemRenderer.opaque(renderer,entry);else DoorItemRenderer.baked(renderer,entry,entry.model);
                Snapshot actual=new Snapshot();
                if(!expected.equals(actual))throw new IllegalStateException("door prepared draw changed GL state for "+entry.model.getClass().getName()+" opaque="+opaque+" state="+state+" expected="+expected+" actual="+actual);
                checks++;
            }
        }
        if(prepared==0)throw new IllegalStateException("door benchmark never exercised a prepared quad plan");
        GlStateManager.depthMask(true);GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        System.out.println("[vandorlabs][reprolab] door-prepared-state PASS checks="+checks+" preparedModels="+prepared+" models="+entries.size());
    }
    private static void reset(int state) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(.25F,.5F,.75F,.8F);GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        if((state&1)==0)GlStateManager.disableLighting();else GlStateManager.enableLighting();
        if((state&2)==0)GlStateManager.disableBlend();else GlStateManager.enableBlend();
        GlStateManager.depthMask((state&1)==0);GlStateManager.cullFace(GlStateManager.CullFace.FRONT);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,48,144);
    }
    private static final class Snapshot {
        final List<Integer> values=new ArrayList<>();
        Snapshot() {
            for(int flag:new int[]{GL11.GL_LIGHTING,GL11.GL_BLEND,GL12.GL_RESCALE_NORMAL,GL11.GL_DEPTH_WRITEMASK,GL11.GL_ALPHA_TEST})values.add(GL11.glGetBoolean(flag)?1:0);
            for(int field:new int[]{GL11.GL_TEXTURE_BINDING_2D,GL11.GL_CULL_FACE_MODE,GL11.GL_ALPHA_TEST_FUNC,
                    GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL13.GL_ACTIVE_TEXTURE})values.add(GL11.glGetInteger(field));
            values.add(Float.floatToIntBits(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)));
            values.add(Float.floatToIntBits(OpenGlHelper.lastBrightnessX));values.add(Float.floatToIntBits(OpenGlHelper.lastBrightnessY));
            for(int field:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER})values.add(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,field));
            FloatBuffer matrix=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);
            for(int i=0;i<16;i++)values.add(Float.floatToIntBits(matrix.get(i)));
            FloatBuffer color=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
            for(int i=0;i<4;i++)values.add(Float.floatToIntBits(color.get(i)));
        }
        @Override public boolean equals(Object other){return other instanceof Snapshot && values.equals(((Snapshot)other).values);}
        @Override public int hashCode(){return values.hashCode();}
        @Override public String toString(){return values.toString();}
    }
}
