package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.FrameMachineDesign;
import com.vandorlabs.client.TurntableRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/** Shared catalog/machine preview. Core blueprints show the finished structure; other recipes show their item. */
public final class BuilderRecipePreview {
    private static final java.util.LinkedHashMap<String,Preview> CACHE=new java.util.LinkedHashMap<>(16,.75F,true);
    private static final class Preview {final FrameMachineDesign structure;final ItemStack stack;final String label;Preview(String id){FrameMachineDesign found=null;for(FrameMachineDesign spec:FrameMachineDesign.all())if(spec.block.equals(id)){found=spec;break;}structure=found;stack=new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+id)));label=stack.getDisplayName();}}
    private static Preview preview(String id){Preview found=CACHE.get(id);if(found==null){found=new Preview(id);CACHE.put(id,found);if(CACHE.size()>128)CACHE.remove(CACHE.keySet().iterator().next());}return found;}
    public static void clear(){CACHE.clear();}
    public static FrameMachineDesign structure(String blueprint){return preview(blueprint).structure;}
    public static double extent(String blueprint){FrameMachineDesign spec=structure(blueprint);return spec==null?1:Math.sqrt(spec.width*spec.width+spec.height*spec.height+spec.depth*spec.depth);}
    public static String label(String blueprint){return preview(blueprint).label;}
    public static void render(String blueprint,float angle){FrameMachineDesign spec=structure(blueprint);if(spec!=null){SynthesisDesignPreview.render(spec.design,angle);return;}renderItem(preview(blueprint).stack,angle);}
    public static void renderItem(ItemStack stack,float angle){if(stack.isEmpty())return;GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){GlStateManager.rotate(angle,0,1,0);GlStateManager.color(1,1,1,1);Minecraft.getMinecraft().getRenderItem().renderItem(stack,ItemCameraTransforms.TransformType.FIXED);}finally{GlStateManager.popMatrix();}}
    private BuilderRecipePreview(){}
}
