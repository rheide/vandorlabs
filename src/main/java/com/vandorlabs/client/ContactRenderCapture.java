package com.vandorlabs.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Close-up native block/TESR captures of a two-cell contact, without camera movement. */
final class ContactRenderCapture {
    static void save(Minecraft mc,File output,String name,BlockPos first,BlockPos second,BlockPos... extra)throws Exception{
        save(mc,output,name,20,first,second,extra);
    }
    static void save(Minecraft mc,File output,String name,float elevation,BlockPos first,BlockPos second,BlockPos... extra)throws Exception{
        java.util.Set<BlockPos> positions=new java.util.LinkedHashSet<>();positions.add(first);positions.add(second);positions.addAll(java.util.Arrays.asList(extra));
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(640,420,true);int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1.5,1.5,-.984375,.984375,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{frame.bindFramebuffer(true);GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.rotate(elevation,1,0,0);GlStateManager.rotate(145,0,1,0);GlStateManager.translate(-.5-(second.getX()-first.getX())/2D,-.5-(second.getY()-first.getY())/2D,-.5-(second.getZ()-first.getZ())/2D);
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);
            for(int renderPass=0;renderPass<2;renderPass++){
                GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);
                GlStateManager.depthMask(renderPass==0);if(renderPass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);}
                BufferBuilder buffer=new BufferBuilder(8192);buffer.begin(7,DefaultVertexFormats.BLOCK);buffer.setTranslation(-first.getX(),-first.getY(),-first.getZ());
                for(BlockPos pos:positions)if((mc.world.getBlockState(pos).getBlock().getBlockLayer()==net.minecraft.util.BlockRenderLayer.TRANSLUCENT)==(renderPass==1))mc.getBlockRendererDispatcher().renderBlock(mc.world.getBlockState(pos),pos,mc.world,buffer);buffer.finishDrawing();new WorldVertexBufferUploader().draw(buffer);
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(renderPass);
                for(BlockPos pos:positions){TileEntity tile=mc.world.getTileEntity(pos);if(tile==null||!tile.shouldRenderInPass(renderPass))continue;net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer renderer=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile);if(renderer!=null)renderer.render(tile,pos.getX()-first.getX(),pos.getY()-first.getY(),pos.getZ()-first.getZ(),0,-1,1);}
            }
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(640,420,frame),"png",new File(output,name+".png"));
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);org.lwjgl.opengl.GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.color(1,1,1,1);}
    }
}
