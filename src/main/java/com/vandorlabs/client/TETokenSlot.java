package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;

/** A top-loading reader, using the same geometry and contents for door security. */
public final class TETokenSlot extends TileEntitySpecialRenderer<TileEntityTokenSlot> {
 private static final EnumFacing[] FACES=EnumFacing.values();
 // Immutable face bases and direct matrices are shared by all readers on the render thread.
 private static final FaceBasis[][] BASES=new FaceBasis[6][4];
 static {for(EnumFacing face:FACES)for(int rotation=0;rotation<4;rotation++)BASES[face.ordinal()][rotation]=new FaceBasis(face,rotation);}
 private static final class FaceBasis {
  final Vec3d origin,u,v,n;
  final java.nio.FloatBuffer itemMatrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
  FaceBasis(EnumFacing face,int rotation){
   origin=TokenSlotGeometry.point(face,rotation,0,0,0);u=TokenSlotGeometry.point(face,rotation,1,0,0).subtract(origin);v=TokenSlotGeometry.point(face,rotation,0,1,0).subtract(origin);n=new Vec3d(face.getDirectionVec());
   itemMatrix.put(new float[]{(float)-u.x,(float)-u.y,(float)-u.z,0,(float)v.x,(float)v.y,(float)v.z,0,(float)n.x,(float)n.y,(float)n.z,0,(float)(origin.x+n.x*.06),(float)(origin.y+n.y*.06),(float)(origin.z+n.z*.06),1}).flip();
  }
  void vertex(BufferBuilder b,double x,double y,double depth){b.pos(origin.x+u.x*x+v.x*y+n.x*depth,origin.y+u.y*x+v.y*y+n.y*depth,origin.z+u.z*x+v.z*y+n.z*depth);}
 }
 @Override public void render(TileEntityTokenSlot tile,double x,double y,double z,float partial,int stage,float alpha){
  if(!(tile.getBlockType() instanceof BlockTokenSlot))return;
  net.minecraft.block.state.IBlockState state=tile.getBlockType().getActualState(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos());
  GlStateManager.pushMatrix();try{GlStateManager.translate(x,y,z);contents(tile,state.getValue(BlockVandorSwitch.FACING),state.getValue(BlockVandorSwitch.ROTATION),partial);}finally{GlStateManager.popMatrix();}
 }
 static void frame(boolean chip,EnumFacing facing){
  net.minecraft.block.state.IBlockState state=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+(chip?"data_chip":"access_card")+"_slot").getDefaultState().withProperty(BlockVandorSwitch.FACING,facing);
  Minecraft mc=Minecraft.getMinecraft();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
  BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.ITEM);for(EnumFacing side:FACES)for(BakedQuad q:model.getQuads(state,side,0))b.addVertexData(q.getVertexData());for(BakedQuad q:model.getQuads(state,null,0))b.addVertexData(q.getVertexData());Tessellator.getInstance().draw();
 }
 static void contents(TileEntityTokenSlot tile,EnumFacing facing,int rotation,float partial){
  FaceBasis basis=BASES[facing.ordinal()][rotation&3];
  GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
   GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
   int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
   boolean rejected=tile.rejected();
   if(!rejected&&tile.isEmpty()&&tile.effectiveAllowed()!=0){
    Minecraft mc=Minecraft.getMinecraft();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);TextureAtlasSprite logo=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/access_reader/logos/"+com.vandorlabs.items.ItemDesignToken.DESIGNS[ReaderLogoCycle.design(tile)]);
    double half=2.2/16;BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
    logoVertex(b,basis,-half,-half,logo.getMaxU(),logo.getMaxV());logoVertex(b,basis,half,-half,logo.getMinU(),logo.getMaxV());
    logoVertex(b,basis,half,half,logo.getMinU(),logo.getMinV());logoVertex(b,basis,-half,half,logo.getMaxU(),logo.getMinV());Tessellator.getInstance().draw();
   }
   double h=(tile.chip()?3:4.4)/16;GlStateManager.disableTexture2D();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
   BufferBuilder overlays=Tessellator.getInstance().getBuffer();overlays.begin(7,DefaultVertexFormats.POSITION_COLOR);
   if(rejected)for(int pixel=-2;pixel<=2;pixel++){
    panel(overlays,basis,(pixel-.35)/16,(pixel+.35)/16,(pixel-.35)/16,(pixel+.35)/16,.026,0xffb340);
    if(pixel!=0)panel(overlays,basis,(pixel-.35)/16,(pixel+.35)/16,(-pixel-.35)/16,(-pixel+.35)/16,.026,0xffb340);
   }
   panel(overlays,basis,.9/16,2.1/16,-h+.22/16,-h+.47/16,.098,tile.getOutputLevel()>0?0x5cffff:0x1c6876);
   panel(overlays,basis,-2.1/16,-.9/16,-h+.22/16,-h+.47/16,.098,0xc68a37);Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
   OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
   if(!tile.isEmpty()){
    float age=tile.animationAge(partial);double slide=age<8?age/8:tile.returning()&&age>12?(20-age)/8:1;slide=slide*slide*(3-2*slide);
    double offset=.60*(1-slide);GlStateManager.multMatrix(basis.itemMatrix);GlStateManager.translate(0,offset,0);double scale=tile.chip()?.38:.5;GlStateManager.scale(scale,scale,1);GlStateManager.color(1,1,1,1);Minecraft.getMinecraft().getRenderItem().renderItem(tile.getStackInSlot(0),ItemCameraTransforms.TransformType.FIXED);
   }
  }finally{GlStateManager.enableTexture2D();GlStateManager.popMatrix();}
 }
 private static void logoVertex(BufferBuilder b,FaceBasis basis,double x,double y,float u,float v){basis.vertex(b,x,y,.025);b.tex(u,v).color(255,255,255,255).endVertex();}
 private static void panel(BufferBuilder b,FaceBasis basis,double left,double right,double bottom,double top,double depth,int color){
  coloredVertex(b,basis,left,bottom,depth,color);coloredVertex(b,basis,right,bottom,depth,color);coloredVertex(b,basis,right,top,depth,color);coloredVertex(b,basis,left,top,depth,color);
 }
 private static void coloredVertex(BufferBuilder b,FaceBasis basis,double x,double y,double depth,int color){basis.vertex(b,x,y,depth);b.color(color>>16&255,color>>8&255,color&255,255).endVertex();}
}
