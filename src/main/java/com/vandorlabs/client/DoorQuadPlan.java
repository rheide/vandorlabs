package com.vandorlabs.client;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Forge's consecutive diffuse-light groups, prepared once for immutable door models. */
final class DoorQuadPlan {
    private static final EnumFacing[] SIDES=EnumFacing.values();
    final List<Segment> segments;
    final boolean restoreLighting;
    final net.minecraft.client.renderer.vertex.VertexFormat itemFormat=DefaultVertexFormats.ITEM;
    private DoorQuadPlan(List<Segment> segments,boolean restoreLighting) {
        this.segments=Collections.unmodifiableList(segments);this.restoreLighting=restoreLighting;
    }
    static boolean stableQuads(IBakedModel model) {
        // Forge's vanilla JSON bake wraps SimpleBakedModel in this private class.
        // Its getQuads(null, ...) delegates directly to the immutable parent;
        // animation applies only when passed an extended blockstate.
        return model.getClass().getName().equals("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$1")
                || model.getClass()==SimpleBakedModel.class
                || model.getClass()==net.minecraftforge.client.model.obj.OBJModel.OBJBakedModel.class
                || model instanceof SelectedDoorGeometry
                || model instanceof ScaledDoorItemModel && ((ScaledDoorItemModel)model).stableQuads();
    }
    static DoorQuadPlan prepare(IBakedModel model) {
        if(!stableQuads(model) || model.isBuiltInRenderer())return null;
        List<BakedQuad> all=new ArrayList<>();
        for(EnumFacing side:SIDES)all.addAll(model.getQuads(null,side,0));
        all.addAll(model.getQuads(null,null,0));
        for(BakedQuad quad:all) {
            if(quad.getClass()!=BakedQuad.class && quad.getClass()!=net.minecraftforge.client.model.pipeline.UnpackedBakedQuad.class)return null;
            if(quad.getFormat()!=DefaultVertexFormats.ITEM && quad.getFormat().hasUvOffset(1))return null;
        }
        List<Segment> segments=new ArrayList<>();int first=0;boolean previousShade=true;
        while(first<all.size()) {
            boolean shade=all.get(first).shouldApplyDiffuseLighting();int end=first+1;
            while(end<all.size() && all.get(end).shouldApplyDiffuseLighting()==shade)end++;
            segments.add(new Segment(new ArrayList<>(all.subList(first,end)),shade,shade!=previousShade));
            previousShade=shade;first=end;
        }
        return new DoorQuadPlan(segments,!previousShade);
    }
    void draw(RenderItem renderer,ItemStack stack) {
        Tessellator tessellator=Tessellator.getInstance();BufferBuilder buffer=tessellator.getBuffer();
        for(int i=0;i<segments.size();i++) {
            Segment segment=segments.get(i);buffer.begin(7,DefaultVertexFormats.ITEM);
            float block=OpenGlHelper.lastBrightnessX,sky=OpenGlHelper.lastBrightnessY;
            if(segment.changeShade) {
                if(segment.shade)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            }
            renderer.renderQuads(buffer,segment.quads,-1,stack);tessellator.draw();
            OpenGlHelper.lastBrightnessX=block;OpenGlHelper.lastBrightnessY=sky;
        }
        if(restoreLighting) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,OpenGlHelper.lastBrightnessX,OpenGlHelper.lastBrightnessY);
            GlStateManager.enableLighting();
        }
    }
    static final class Segment {
        final List<BakedQuad> quads;
        final boolean shade,changeShade;
        Segment(List<BakedQuad> quads,boolean shade,boolean changeShade) {
            this.quads=Collections.unmodifiableList(quads);this.shade=shade;this.changeShade=changeShade;
        }
    }
}
