package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ForgeModContainer;

/** Keep RenderItem's NONE-transform state management while reusing quad grouping. */
final class DoorItemRenderer {
    private static final boolean STANDARD_FORGE=!net.minecraftforge.fml.client.FMLClientHandler.instance().hasOptifine();
    private DoorItemRenderer() { }
    static void opaque(RenderItem renderer,DoorRenderModels.Entry entry) {
        if(entry.stack.isEmpty())return;
        // OptiFine patches this entry point for shaders; renderer subclasses can
        // also own additional state. Keep their complete original dispatch.
        if(!STANDARD_FORGE || renderer.getClass()!=RenderItem.class || !ForgeModContainer.allowEmissiveItems) {
            renderer.renderItem(entry.stack,ItemCameraTransforms.TransformType.NONE);return;
        }
        IBakedModel model=renderer.getItemModelWithOverrides(entry.stack,null,null);
        if(entry.plan(model)==null) {
            renderer.renderItem(entry.stack,ItemCameraTransforms.TransformType.NONE);return;
        }
        TextureManager textures=Minecraft.getMinecraft().getTextureManager();
        textures.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        textures.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false,false);
        GlStateManager.color(1,1,1,1);GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516,.1F);GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        model=ForgeHooksClient.handleCameraTransforms(model,ItemCameraTransforms.TransformType.NONE,false);
        baked(renderer,entry,model);
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();GlStateManager.disableBlend();
        textures.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        textures.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
    }
    static void baked(RenderItem renderer,DoorRenderModels.Entry entry,IBakedModel model) {
        DoorQuadPlan plan=STANDARD_FORGE && renderer.getClass()==RenderItem.class
                && ForgeModContainer.allowEmissiveItems && !entry.stack.hasEffect()?entry.plan(model):null;
        if(plan!=null && plan.itemFormat!=net.minecraft.client.renderer.vertex.DefaultVertexFormats.ITEM)plan=null;
        if(plan==null){renderer.renderItem(entry.stack,model);return;}
        if(entry.stack.isEmpty())return;
        GlStateManager.pushMatrix();GlStateManager.translate(-.5F,-.5F,-.5F);
        plan.draw(renderer,entry.stack);GlStateManager.popMatrix();
    }
}
