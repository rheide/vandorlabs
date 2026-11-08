package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.client.segmented.*;
import java.io.File;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Documentation-only capture of the actual installed gate fixture, separate from release assertions. */
final class SegmentedGateDocumentation {
 static void capture(Minecraft mc,BlockPos base,File output)throws Exception{
  Map<SegmentedDoorTile,NBTTagCompound> saved=new LinkedHashMap<>();
  for(int style=0;style<3;style++)for(int column=0;column<3;column++)for(int row=0;row<3;row++){SegmentedDoorTile tile=SegmentedDoorTile.at(mc.world,base.add(style*5+column,row,0));saved.put(tile,tile.writeToNBT(new NBTTagCompound()));}
  boolean authored=SegmentedDoorVisuals.authored;int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();UUID motion=UUID.randomUUID();SegmentedDoorVisuals.authored=true;
  try{
   for(boolean straight:new boolean[]{true,false})for(int step=0;step<=24;step++){
    for(SegmentedDoorTile tile:saved.keySet()){
     SegmentedDoorSettings old=tile.settings;tile.settings=new SegmentedDoorSettings(2,straight,old.layout,old.activation,old.window,old.shape,old.join,old.glass,old.position,straight?16:4);tile.from=step/24D;tile.targetOpen=step>0;tile.started=mc.world.getTotalWorldTime()+100;tile.duration=1000000;tile.motionIdentity=motion;SegmentedGateContexts.changed(tile);
    }
    for(int style=0;style<3;style++)image(mc,base,output,style,straight,step);
   }
  }finally{for(Map.Entry<SegmentedDoorTile,NBTTagCompound> entry:saved.entrySet()){entry.getKey().readFromNBT(entry.getValue());SegmentedGateContexts.changed(entry.getKey());}SegmentedDoorVisuals.authored=authored;net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);}
  System.out.println("[vandorlabs][reprolab] segmented-gate-documentation PASS styles=3 motions=2 frames=150 installedJoinedRows");
 }
 private static void image(Minecraft mc,BlockPos base,File output,int style,boolean straight,int step)throws Exception{
  Framebuffer frame=new Framebuffer(480,300,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3.6,3.6,-2.8,1.7,-20,20);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState ignored=new TurntableRenderState()){
   frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GlStateManager.color(1,1,1,1);GlStateManager.rotate(12,1,0,0);GlStateManager.rotate(18,0,1,0);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);
   BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();for(int column=0;column<3;column++)renderer.renderUncached(SegmentedDoorTile.at(mc.world,base.add(style*5+column,2,0)),column,0,0,0,buffer);Tessellator.getInstance().draw();GL11.glFinish();if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Gate documentation GL state");javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(480,300,frame),"png",new File(output,String.format("shot_gate_%s_%s_%02d.png",new String[]{"picket","industrial","lattice"}[style],straight?"slide":"bend",step)));
  }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
 private SegmentedGateDocumentation(){}
}
