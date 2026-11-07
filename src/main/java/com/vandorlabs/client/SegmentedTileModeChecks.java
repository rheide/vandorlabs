package com.vandorlabs.client;

import com.vandorlabs.client.segmented.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/** Actual nonsquare atlas artwork through released fast-solid and clipped-window emitters. */
final class SegmentedTileModeChecks {
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Segmented Tile: "+why);}
 private static SegmentedDoorTile tile(Minecraft mc,int choice,int column,boolean window){SegmentedDoorTile tile=SegmentedDoorRenderChecks.tile(mc,EnumFacing.NORTH);tile.height=3;tile.columns=4;tile.column=column;tile.texture=choice;tile.settings=new SegmentedDoorSettings(0,false,1,0,window,2,false,SegmentedDoorSettings.DEFAULT.glass);return tile;}
 static void run(Minecraft mc,File output)throws Exception{
  int choice=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.IDS[i].equals("company_door_pinnacle_light")){choice=ScreenHousingTextures.choiceAt(i);break;}need(choice>=0&&SegmentedDoorTile.validTexture(choice),"actual released tall company artwork");TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));double aspect=UnifiedTextureSprites.aspect(sprite);need(Math.abs(aspect-.5)<1e-6,"authored tall artwork aspect="+aspect);
  SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();boolean authored=SegmentedDoorVisuals.authored;int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();SegmentedDoorVisuals.authored=true;
  try{image(mc,renderer,choice,output);net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);double[] density=new double[2];
   for(int window=0;window<2;window++){SegmentedDoorTile tile=tile(mc,choice,1,window!=0);BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderUncached(tile,0,0,0,0,b);b.finishDrawing();int stride=b.getVertexFormat().getNextOffset(),uv=b.getVertexFormat().getUvOffsetById(0),edges=0;double min=Double.POSITIVE_INFINITY,max=0;
    for(int q=0;q<b.getVertexCount();q+=4){boolean body=true;double faceZ=b.getByteBuffer().getFloat(q*stride+8);for(int v=0;v<4;v++){double z=b.getByteBuffer().getFloat((q+v)*stride+8);body&=Math.abs(z-faceZ)<1e-6&&Math.abs(Math.abs(z-.5)-SegmentedDoorGeometry.HALF_THICKNESS)<1e-5;}if(!body)continue;for(int v=0;v<4;v++){int a=(q+v)*stride,c=(q+(v+1)%4)*stride;double dx=b.getByteBuffer().getFloat(a)-b.getByteBuffer().getFloat(c),dy=b.getByteBuffer().getFloat(a+4)-b.getByteBuffer().getFloat(c+4);if(Math.abs(dx)>1e-6||Math.abs(dy)<1e-5)continue;double dv=(b.getByteBuffer().getFloat(a+uv+4)-b.getByteBuffer().getFloat(c+uv+4))/(sprite.getMaxV()-sprite.getMinV()),value=Math.abs(dv/dy);min=Math.min(min,value);max=Math.max(max,value);edges++;}}
    need(edges>3,"actual body front-edge coverage window="+window);System.out.println("[vandorlabs][reprolab] segmented-tile-density window="+window+" aspect="+aspect+" min="+min+" max="+max+" edges="+edges+" bodyPixels="+(SegmentedDoorGeometry.HALF_THICKNESS*32));density[window]=max;
   }
   need(Math.abs(density[0]-density[1])<1e-5,"solid/window Tile texel density differs: solid="+density[0]+" window="+density[1]);need(Math.abs(density[0]-aspect)<1e-5,"Tile preserves authored aspect");need(Math.abs(SegmentedDoorGeometry.HALF_THICKNESS*32-2)<1e-9,"segment body is two pixels");System.out.println("[vandorlabs][reprolab] segmented-tile-consistency PASS actualCompanyAtlas/fastSolid/clippedWindow/continuousUnscaled/body2px");
  }finally{SegmentedDoorVisuals.authored=authored;net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);}
 }
 private static void image(Minecraft mc,SegmentedDoorRenderer renderer,int choice,File output)throws Exception{
  Framebuffer frame=new Framebuffer(512,384,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.1,4.1,-2.1,1.1,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState ignored=new TurntableRenderState()){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.1875F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.disableLighting();mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
   for(int pass=0;pass<2;pass++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);if(pass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(true);}BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);for(int column=0;column<4;column++)renderer.renderUncached(tile(mc,choice,column,(column&1)==0),column,0,0,0,b);Tessellator.getInstance().draw();}
   GL11.glFinish();javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(512,384,frame),"png",new File(output,"shot_segmented_tile_consistency.png"));need(GL11.glGetError()==GL11.GL_NO_ERROR,"actual mixed Tile framebuffer");
  }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
}
