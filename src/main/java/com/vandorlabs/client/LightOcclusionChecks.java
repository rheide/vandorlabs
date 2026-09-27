package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** A convex light assembly must not rely on depth precision to hide its interior. */
final class LightOcclusionChecks {
    static void run(File output) {
        Minecraft mc=Minecraft.getMinecraft();
        BlockPos origin=new BlockPos(40,240,40);
        Framebuffer target=new Framebuffer(256,256,true);
        List<BlockPos> positions=new ArrayList<>();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();GlStateManager.ortho(-2,2,-2,2,-10,10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try {
            for(EnumFacing facing:EnumFacing.values())for(int size:new int[]{1,2})for(int style:new int[]{0,5}) {
                PanelPlane plane=PanelPlane.of(facing);
                for(int col=0;col<size;col++)for(int row=0;row<size;row++) {
                    BlockPos pos=origin.offset(plane.right,col).offset(plane.up,row);positions.add(pos);
                    mc.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState()
                            .withProperty(BlockAnimatedScreenSelector.FACING,facing),2);
                    ((TileEntityProgrammableLight)mc.world.getTileEntity(pos)).configure(style,15,size==2,0,1+col+2*row);
                }
                double cx=0,cy=0,cz=0;
                for(BlockPos pos:positions){cx+=pos.getX()+.5;cy+=pos.getY()+.5;cz+=pos.getZ()+.5;}
                cx/=positions.size();cy/=positions.size();cz/=positions.size();
                for(int view:new int[]{25,205}) {
                    BufferedImage reference=null;
                    for(boolean depth:new boolean[]{true,false}) {
                        target.bindFramebuffer(true);GlStateManager.depthMask(true);
                        GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                        if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
                        GlStateManager.disableBlend();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);
                        GlStateManager.loadIdentity();GlStateManager.rotate(15,1,0,0);GlStateManager.rotate(view,0,1,0);
                        if(facing==EnumFacing.UP||facing==EnumFacing.DOWN)GlStateManager.rotate(facing==EnumFacing.UP?90:-90,1,0,0);
                        else GlStateManager.rotate(facing==EnumFacing.NORTH?180:facing==EnumFacing.EAST?-90:facing==EnumFacing.WEST?90:0,0,1,0);
                        List<BlockPos> order=new ArrayList<>(positions);if(!depth)Collections.reverse(order);
                        net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(mc.player,false);
                        net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher=
                                net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance;
                        dispatcher.preDrawBatch();
                        for(BlockPos pos:order)dispatcher.render(mc.world.getTileEntity(pos),
                                pos.getX()-cx,pos.getY()-cy,pos.getZ()-cz,0);
                        dispatcher.drawBatch(0);
                        BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,target);
                        if(depth)reference=image;
                        else {
                            int changed=0,occupied=0;
                            for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
                                if((reference.getRGB(x,y)&0xFFFFFF)!=0)occupied++;
                                if(reference.getRGB(x,y)!=image.getRGB(x,y))changed++;
                            }
                            if(occupied<500||changed!=0)throw new IllegalStateException("Light hidden surfaces: "+facing+" size "+size+" style "+style+" view "+view+" changed="+changed+" occupied="+occupied);
                            if(facing==EnumFacing.NORTH&&view==25)javax.imageio.ImageIO.write(image,"png",new File(output,"light-depth-independent-"+size+"-"+style+".png"));
                        }
                    }
                }
                for(BlockPos pos:positions)mc.world.setBlockToAir(pos);positions.clear();
            }
            System.out.println("[vandorlabs][reprolab] light-hidden-faces PASS (Forge batch: six facings, Porthole/Logo, single/joined, front/back, reversed draw order without depth)");
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        finally {
            for(BlockPos pos:positions)mc.world.setBlockToAir(pos);
            GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.cullFace(GlStateManager.CullFace.BACK);
            GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
}
