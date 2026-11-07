package com.vandorlabs.client;

import com.vandorlabs.client.segmented.SegmentedDoorRenderer;
import com.vandorlabs.segmented.*;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Actual installed rows through both Source render passes, with an opaque-image negative. */
final class SegmentedGateBlendChecks {
 private static BufferedImage baseline;private static int generation;
 static void run(Minecraft mc,BlockPos base,File output)throws Exception{
  BufferedImage off=render(mc,base,0),blend=render(mc,base,1),opaque=render(mc,base,2);int fractional=0,changed=0;
  for(int y=0;y<blend.getHeight();y++)for(int x=552;x<732;x++){
   int a=off.getRGB(x,y),b=blend.getRGB(x,y),c=opaque.getRGB(x,y);if(a!=b)changed++;
   if(a==b||b==c)continue;double alpha=-1;boolean consistent=true;
   for(int shift:new int[]{0,8,16}){int background=a>>shift&255,actual=b>>shift&255,source=c>>shift&255;if(Math.abs(source-background)<12)continue;double value=(actual-background)/(double)(source-background);if(alpha<0)alpha=value;else consistent&=Math.abs(value-alpha)<.08;}
   if(consistent&&alpha>.01&&alpha<.99)fractional++;
  }
  if(changed<1000||fractional<1000)throw new AssertionError("Actual Source glazing blend changed="+changed+" fractional="+fractional);
  for(int x=0;x<540;x++)for(int y=0;y<blend.getHeight();y++)if(off.getRGB(x,y)!=blend.getRGB(x,y))throw new AssertionError("Latent gate window leaked into alpha pass");
  if(baseline!=null)for(int y=0;y<blend.getHeight();y++)for(int x=0;x<blend.getWidth();x++)if(baseline.getRGB(x,y)!=blend.getRGB(x,y))throw new AssertionError("Source row/material reload pixel differs at "+x+","+y);baseline=blend;generation++;
  javax.imageio.ImageIO.write(blend,"png",new File(output,"shot_segmented_supplied_gates_blend_"+generation+".png"));javax.imageio.ImageIO.write(off,"png",new File(output,"shot_segmented_supplied_gates_no_image_"+generation+".png"));javax.imageio.ImageIO.write(opaque,"png",new File(output,"shot_segmented_supplied_gates_opaque_negative_"+generation+".png"));
  System.out.println("[vandorlabs][reprolab] segmented-gate-blend PASS generation="+generation+" changed="+changed+" fractional="+fractional+" actualPlacedRows/modelPass0/glazingPass1/opaqueEdges/opaqueImageNegative");
 }
 private static BufferedImage render(Minecraft mc,BlockPos base,int mode){
  Framebuffer frame=new Framebuffer(744,204,true);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.2,12.2,-2.2,1.2,-8,8);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState ignored=new TurntableRenderState()){
   frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.1875F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
   for(int pass=0;pass<2;pass++){if(pass==1&&mode==0)continue;net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);if(pass==1&&mode==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(pass==0);}
    for(int style=0;style<4;style++)for(int column=0;column<3;column++){SegmentedDoorTile root=SegmentedDoorTile.at(mc.world,base.add(style*5+column,2,0));if(root==null||!root.ready())throw new AssertionError("Missing placed framebuffer column");BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderUncached(root,style*3+column,0,0,0,buffer);int vertices=buffer.getVertexCount();if(style<3&&pass==1&&vertices!=0)throw new AssertionError("Gate alpha geometry despite latent-only window");Tessellator.getInstance().draw();}
   }
   GL11.glFinish();if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Source supplied gate framebuffer GL error");return net.minecraft.util.ScreenShotHelper.createScreenshot(744,204,frame);
  }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
}
