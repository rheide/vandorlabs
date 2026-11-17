package com.vandorlabs.client.constructors;
import com.vandorlabs.client.TurntableRenderState;
import net.minecraft.client.renderer.*;
/** Catalog screens reuse the builder's complete design mesh; ordinary blueprints use their actual item model. */
public final class BlueprintPreview {
 public static void draw(String blueprint,float centerX,float centerY,float width,float height,float angle){draw(blueprint,net.minecraft.item.ItemStack.EMPTY,centerX,centerY,width,height,angle);}
 public static void draw(String blueprint,net.minecraft.item.ItemStack chip,float centerX,float centerY,float width,float height,float angle){
  GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
   GlStateManager.translate(centerX,centerY,.5);GlStateManager.scale(1,1,.005);GlStateManager.enableDepth();GlStateManager.depthMask(true);
   double scale=Math.min(width,height)*.85/Math.max(1,BuilderRecipePreview.extent(blueprint,chip));GlStateManager.scale(scale,-scale,scale);GlStateManager.rotate(17,1,0,0);BuilderRecipePreview.render(blueprint,chip,angle);
  }finally{GlStateManager.popMatrix();}
 }
 private BlueprintPreview(){}
}
