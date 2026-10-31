package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
/** Reported stepped window topology: shared boundaries have no rails and concave corners remain closed. */
final class GlassJoinContourChecks {
 static void run(Minecraft mc,File output)throws Exception{
  NBTTagCompound root;try(InputStream stream=GlassJoinContourChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/glass-stepped-frame-2.7.nbt")){root=CompressedStreamTools.readCompressed(stream);}
  List<VehicleStructure.Cell> cells=new ArrayList<>();NBTTagList tags=root.getTagList("Cells",10);
  for(int i=0;i<tags.tagCount();i++){NBTTagCompound c=tags.getCompoundTagAt(i),t=c.getCompoundTag("Tile");BlockPos p=new BlockPos(t.getInteger("x"),t.getInteger("y"),t.getInteger("z"));Block b=Block.getBlockFromName(c.getString("Block"));cells.add(new VehicleStructure.Cell(p,b.getStateFromMeta(c.getInteger("Meta")),t));}
  VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));int checked=0;
  try{
   for(net.minecraft.tileentity.TileEntity raw:world.tiles.values()){
    TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;BlockPos p=tile.getPos();int mask=GlassSurfaceGroups.frameMask(tile);
    if(world.getTileEntity(p.east()) instanceof TileEntityProgrammableGlass){require((mask&2)==0,"rail on shared right boundary "+p);checked++;}
    if(world.getTileEntity(p.up()) instanceof TileEntityProgrammableGlass){require((mask&8)==0,"rail on shared upper boundary "+p);checked++;}
   }
   int a=GlassSurfaceGroups.frameMask((TileEntityProgrammableGlass)world.getTileEntity(new BlockPos(4,3,0))),b=GlassSurfaceGroups.frameMask((TileEntityProgrammableGlass)world.getTileEntity(new BlockPos(8,3,0)));
   require((a&32)!=0&&(b&16)!=0,"stepped concave corner patches missing");
   java.awt.image.BufferedImage image=capture(mc,world);ImageIO.write(image,"png",new File(output,"glass-stepped-continuous-frame.png"));
   for(net.minecraft.tileentity.TileEntity tile:world.tiles.values()){
    BlockPos p=tile.getPos();if(world.getTileEntity(p.east())!=null)require(background(image,p.getX()+1,p.getY()+.5),"visible doubled vertical border "+p);
    if(world.getTileEntity(p.up())!=null)require(background(image,p.getX()+.5,p.getY()+1),"visible doubled horizontal border "+p);
   }
   double[] widths=ScreenFrameBorders.get(((TileEntityProgrammableGlass)world.tiles.values().iterator().next()).frameAppearance.front);
   require(!background(image,5-widths[1]/2,3+widths[3]/2)&&!background(image,8+widths[0]/2,3+widths[3]/2),"concave corner pinholes");
   System.out.println("[vandorlabs][reprolab] glass-join-contour PASS tiles="+cells.size()+" sharedEdges="+checked+" unequal rectangles stepped contour concave patches actual framebuffer");
  }finally{GlassSurfaceGroups.unload(world);}
 }
 private static boolean background(java.awt.image.BufferedImage image,double x,double y){int c=image.getRGB((int)(x*96),(int)((4-y)*96));return Math.abs((c>>16&255)-32)+Math.abs((c>>8&255)-48)+Math.abs((c&255)-64)<12;}
 private static java.awt.image.BufferedImage capture(Minecraft mc,VehicleWorld world){
  Framebuffer target=new Framebuffer(960,384,true);int old=MinecraftForgeClient.getRenderPass();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,10,0,4,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState saved=new TurntableRenderState()){
   target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();ForgeHooksClient.setRenderPass(0);
   TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);for(net.minecraft.tileentity.TileEntity raw:world.tiles.values()){BlockPos p=raw.getPos();renderer.render((TileEntityProgrammableGlass)raw,p.getX(),p.getY(),p.getZ(),0,-1,1);}
   GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(960,384,target);
  }finally{ForgeHooksClient.setRenderPass(old);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
 private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Glass contour: "+why);}
}
