package com.vandorlabs.neoforge.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.ui.RedstoneScreenLayout;
import com.vandorlabs.ui.RedstoneScreenOverlay;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Native buffer/font submissions over the exact shared authored overlay plan. */
final class RedstoneOverlayRenderer {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/gui/white.png");
    private static final RenderType SOLID=RenderType.entityCutout(WHITE);
    private static final java.util.Map<ModernRedstoneScreenContents,RedstoneScreenOverlay.Cache> PLANS=new java.util.WeakHashMap<>();
    private static final RedstoneScreenOverlay.RowAccess<ModernRedstoneScreenContents.Row> ROWS=new RedstoneScreenOverlay.RowAccess<>(){
        public String label(ModernRedstoneScreenContents.Row row){return row.label;}public boolean slider(ModernRedstoneScreenContents.Row row){return row.slider;}public boolean pulse(ModernRedstoneScreenContents.Row row){return row.pulse;}public boolean active(ModernRedstoneScreenContents.Row row){return row.active();}public int segments(ModernRedstoneScreenContents.Row row){return row.segments();}public int selected(ModernRedstoneScreenContents.Row row){return row.selectedSegment();}
    };
    private static final Matrix4f AFFINE=affine();
    private RedstoneOverlayRenderer(){}
    private static Matrix4f affine(){
        RedstoneScreenLayout layout=RedstoneScreenLayout.of(false);ScreenSurface.Quad q=ScreenSurface.quad(ScreenSurface.Kind.FLAT,false);
        return new Matrix4f().zero().m00((float)(q.topLeft.x-q.topRight.x)/128/16).m11((float)(q.bottomRight.y-q.topRight.y)/layout.height/16).m12((float)(q.bottomRight.z-q.topRight.z)/layout.height/16).m21(q.ny/16).m22(q.nz/16).m30((float)q.topRight.x/16).m31(((float)q.topRight.y+q.ny*.02f)/16).m32(((float)q.topRight.z+q.nz*.02f)/16).m33(1);
    }
    static void draw(PoseStack stack,MultiBufferSource buffers,ModernRedstoneScreenContents screen){
        var cache=PLANS.computeIfAbsent(screen,ignored->new RedstoneScreenOverlay.Cache());
        RedstoneScreenOverlay plan=cache.plan(RedstoneScreenLayout.of(false),screen.title(),screen.rows(),ROWS);
        stack.pushPose();stack.mulPose(AFFINE);var pose=stack.last();var out=buffers.getBuffer(SOLID);
        for(int i=0;i<plan.solids.size();i++){var rect=plan.solids.get(i);float z=(float)rect.layer;
            vertex(out,pose,rect.left,rect.bottom,z,rect.color);vertex(out,pose,rect.right,rect.bottom,z,rect.color);vertex(out,pose,rect.right,rect.top,z,rect.color);vertex(out,pose,rect.left,rect.top,z,rect.color);
        }
        // All solids use one layer/buffer before text, matching the batched legacy pass.
        if(buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source)source.endBatch(SOLID);
        stack.translate(0,0,.04);Font font=Minecraft.getInstance().font;
        for(int i=0;i<plan.text.size();i++){var text=plan.text.get(i);font.drawInBatch(text.maxWidth>0?font.plainSubstrByWidth(text.value,text.maxWidth):text.value,text.x,text.y,text.color,false,stack.last().pose(),buffers,Font.DisplayMode.NORMAL,0,15728880);}
        stack.popPose();
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,int color){out.addVertex(pose,x,y,z).setColor(color>>16&255,color>>8&255,color&255,color>>>24).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose,0,0,1);}

}
