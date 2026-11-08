package com.vandorlabs.client;

import com.vandorlabs.client.segmented.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/** Actual prepared atlas meshes retain the full authored sheet across slit geometry. */
final class SpiralArtworkChecks {
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Spiral artwork: "+why);}
 static void run(Minecraft mc,File output)throws Exception{
  int choice=-1;for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if("segmented_tile_spiral_aluminum_glazing".equals(ScreenHousingTextures.IDS[i]))choice=i;need(choice>=0,"authored identity");
  TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));BufferedImage[] images=new BufferedImage[3];int vertices=0;
  for(int mode=0;mode<3;mode++){
   Framebuffer frame=new Framebuffer(384,384,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.1,3.1,-2.1,1.1,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
   try(TurntableRenderState state=new TurntableRenderState()){
    frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.1875F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    for(int pass=0;pass<2;pass++){
     if(pass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(true);}
     for(int col=0;col<3;col++)for(int row=0;row<3;row++){
      SegmentedDoorTile tile=SegmentedDoorRenderChecks.tile(mc,EnumFacing.NORTH);tile.texture=choice;tile.column=col;tile.columns=3;tile.height=3;tile.settings=new SegmentedDoorSettings(0,false,mode,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass);
      SegmentedDoorPanels.Prepared prepared=SegmentedDoorPanels.prepare(tile.settings,choice,col,row,3,3,SegmentedDoorWindows.layout(tile));double[] pose=new double[6];SegmentedDoorGeometry.panel(row,3,col,3,0,0,false,pose);
      BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);prepared.emit(pass,buffer,col,0,0,pose,0,0xF000F0);
      if(pass==0){int stride=buffer.getVertexFormat().getNextOffset(),uv=buffer.getVertexFormat().getUvOffsetById(0);java.nio.ByteBuffer data=buffer.getByteBuffer();for(int q=0;q<buffer.getVertexCount();q+=4){boolean face=true;double z=data.getFloat(q*stride+8);for(int v=0;v<4;v++)face&=Math.abs(data.getFloat((q+v)*stride+8)-z)<1e-6;face&=Math.abs(Math.abs(z-.5)-SegmentedDoorGeometry.HALF_THICKNESS)<1e-6;if(!face)continue;for(int v=0;v<4;v++){int at=(q+v)*stride;double x=(data.getFloat(at)-col)*16,y=(data.getFloat(at+4)+row)*16,u=(data.getFloat(at+uv)-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU())*16,w=(data.getFloat(at+uv+4)-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV())*16;boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(mode,3,3,1);need(Math.abs(u-(fit?(col*16+x)/3:x))<.003&&Math.abs(w-(fit?(row*16+16-y)/3:16-y))<.003,"actual atlas projection mode="+mode+" cell="+col+","+row);vertices++;}}}
      Tessellator.getInstance().draw();
     }
    }
    GL11.glFinish();need(GL11.glGetError()==GL11.GL_NO_ERROR,"framebuffer");images[mode]=net.minecraft.util.ScreenShotHelper.createScreenshot(384,384,frame);ImageIO.write(images[mode],"png",new File(output,"spiral-full-sheet-layout-"+mode+".png"));
   }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
  }
  int changed=0;for(int y=0;y<384;y++)for(int x=0;x<384;x++){if(images[0].getRGB(x,y)!=images[1].getRGB(x,y))changed++;need(images[com.vandorlabs.render.TextureLayoutMode.STRETCH].getRGB(x,y)==images[com.vandorlabs.render.TextureLayoutMode.FIT].getRGB(x,y),"square Fit matches full-sheet Stretch");}need(changed>10000,"Tile and Stretch must not collapse to per-cell repeats");System.out.println("[vandorlabs][reprolab] spiral-full-sheet PASS actualAtlasVertices="+vertices+" layoutPixelsChanged="+changed+" 3x3 Tile/Stretch/Fit full authored metal over matching slits and selected pane");
 }
 private SpiralArtworkChecks(){}
}
