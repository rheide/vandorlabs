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
 @Override public void render(TileEntityTokenSlot tile,double x,double y,double z,float partial,int stage,float alpha){
  if(!(tile.getBlockType() instanceof BlockTokenSlot))return;
  net.minecraft.block.state.IBlockState state=tile.getBlockType().getActualState(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos());
  GlStateManager.pushMatrix();try{GlStateManager.translate(x,y,z);contents(tile,state.getValue(BlockVandorSwitch.FACING),state.getValue(BlockVandorSwitch.ROTATION),partial);}finally{GlStateManager.popMatrix();}
 }
 static void frame(boolean chip,EnumFacing facing){
  net.minecraft.block.state.IBlockState state=net.minecraft.block.Block.getBlockFromName("vandorlabs:data_"+(chip?"chip":"card")+"_slot").getDefaultState().withProperty(BlockVandorSwitch.FACING,facing);
  Minecraft mc=Minecraft.getMinecraft();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
  BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.ITEM);for(EnumFacing side:EnumFacing.values())for(BakedQuad q:model.getQuads(state,side,0))b.addVertexData(q.getVertexData());for(BakedQuad q:model.getQuads(state,null,0))b.addVertexData(q.getVertexData());Tessellator.getInstance().draw();
 }
 static void contents(TileEntityTokenSlot tile,EnumFacing facing,int rotation,float partial){
  GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
   GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
   int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
   if(tile.rejected()){
    GlStateManager.disableTexture2D();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
    for(int pixel=-2;pixel<=2;pixel++)for(int direction:new int[]{-1,1})panel(facing,rotation,(pixel-.35)/16,(pixel+.35)/16,(direction*pixel-.35)/16,(direction*pixel+.35)/16,.026,0xffb340);
    GlStateManager.enableTexture2D();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
   }else if(tile.isEmpty()&&tile.effectiveAllowed()!=0){
    Minecraft mc=Minecraft.getMinecraft();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);TextureAtlasSprite logo=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/access_reader/logos/"+com.vandorlabs.items.ItemDesignToken.DESIGNS[ReaderLogoCycle.design(tile)]);
    double half=2.2/16;BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
    double[][] points={{-half,-half,0,1},{half,-half,1,1},{half,half,1,0},{-half,half,0,0}};
    for(double[] c:points){Vec3d v=TokenSlotGeometry.point(facing,rotation,c[0],c[1],.025);b.pos(v.x,v.y,v.z).tex(c[2]==0?logo.getMaxU():logo.getMinU(),c[3]==0?logo.getMinV():logo.getMaxV()).color(255,255,255,255).endVertex();}Tessellator.getInstance().draw();
   }
   double h=(tile.chip()?3:4.4)/16;GlStateManager.disableTexture2D();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
   panel(facing,rotation,.9/16,2.1/16,-h+.22/16,-h+.47/16,.098,tile.getOutputLevel()>0?0x5cffff:0x1c6876);
   panel(facing,rotation,-2.1/16,-.9/16,-h+.22/16,-h+.47/16,.098,0xc68a37);GlStateManager.enableTexture2D();
   OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
   if(!tile.isEmpty()){
    float age=tile.animationAge(partial);double slide=age<8?age/8:tile.returning()&&age>12?(20-age)/8:1;slide=slide*slide*(3-2*slide);
    double offset=.60*(1-slide);Vec3d center=TokenSlotGeometry.point(facing,rotation,0,offset,.06),u=TokenSlotGeometry.point(facing,rotation,-1,offset,.06).subtract(center),v=TokenSlotGeometry.point(facing,rotation,0,offset+1,.06).subtract(center),n=new Vec3d(facing.getDirectionVec());
    java.nio.FloatBuffer m=org.lwjgl.BufferUtils.createFloatBuffer(16);m.put(new float[]{(float)u.x,(float)u.y,(float)u.z,0,(float)v.x,(float)v.y,(float)v.z,0,(float)n.x,(float)n.y,(float)n.z,0,(float)center.x,(float)center.y,(float)center.z,1}).flip();GlStateManager.multMatrix(m);double scale=tile.chip()?.38:.5;GlStateManager.scale(scale,scale,1);GlStateManager.color(1,1,1,1);Minecraft.getMinecraft().getRenderItem().renderItem(tile.getStackInSlot(0),ItemCameraTransforms.TransformType.FIXED);
   }
  }finally{GlStateManager.enableTexture2D();GlStateManager.popMatrix();}
 }
 private static void panel(EnumFacing face,int rotation,double left,double right,double bottom,double top,double depth,int color){BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(7,DefaultVertexFormats.POSITION_COLOR);for(double[] c:new double[][]{{left,bottom},{right,bottom},{right,top},{left,top}}){Vec3d p=TokenSlotGeometry.point(face,rotation,c[0],c[1],depth);b.pos(p.x,p.y,p.z).color(color>>16&255,color>>8&255,color&255,255).endVertex();}Tessellator.getInstance().draw();}
}
