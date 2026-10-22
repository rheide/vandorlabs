package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.*;
import org.lwjgl.opengl.GL11;
final class FluidWaterlineChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        BlockPos pos=new BlockPos(350,48,146);net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(128,128,true);
        net.minecraft.entity.Entity oldView=mc.getRenderViewEntity();
        net.minecraft.client.entity.EntityOtherPlayerMP probe=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("902d3aee-c01d-4ae4-b74d-54925cd02690"),"WaterlineProbe"));
        java.lang.reflect.Field camera=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.renderer.ActiveRenderInfo.class,"position","field_178811_e");Object oldCamera=camera.get(null);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GL11.glFrustum(-.006,.006,-.006,.006,.01,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{
            mc.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),2);net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(pos);WaterloggedParts.data(tile).set(0,new FluidStack(FluidRegistry.WATER,1000));
            double top=ContainedFluidHeights.height(mc.world,pos,FluidRegistry.WATER,.5,.5);
            frame.bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.depthMask(true);GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.clear(16640);
            java.awt.image.BufferedImage blank=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);
            GlStateManager.rotate(-90,1,0,0);GlStateManager.translate(-.5,-top+.02,-.5);WaterloggedRenderer.render(tile,0,0,0);
            java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);int changed=0;
            for(int x=48;x<80;x++)for(int y=48;y<80;y++)if(image.getRGB(x,y)!=blank.getRGB(x,y))changed++;
            if(changed<900)throw new IllegalStateException("Free water surface disappears from immediately below: "+changed);
            javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"contained-waterline-underside.png"));
            mc.world.setBlockState(pos,net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState().withProperty(BlockGlassWall.DEPTH,0),2);
            tile=mc.world.getTileEntity(pos);WaterloggedParts.data(tile).set(0,new FluidStack(FluidRegistry.WATER,1000));
            top=ContainedFluidHeights.height(mc.world,pos,FluidRegistry.WATER,.5,.25);
            boolean inside=false;for(WaterloggedParts.Cell cell:WaterloggedParts.volume(mc.world,pos).cells)if(cell.side==0&&cell.box.contains(new net.minecraft.util.math.Vec3d(.5,top-.04,.25)))inside=true;
            if(!inside)throw new IllegalStateException("Forward-view fixture camera must be inside stored water");
            probe.setPosition(pos.getX()+.5,pos.getY()+top-.04-probe.getEyeHeight(),pos.getZ()+.25);probe.prevPosX=probe.posX;probe.prevPosY=probe.posY;probe.prevPosZ=probe.posZ;
            camera.set(null,new net.minecraft.util.math.Vec3d(0,probe.getEyeHeight(),0));mc.setRenderViewEntity(probe);
            GlStateManager.loadIdentity();GlStateManager.rotate(180,0,1,0);GlStateManager.translate(-.5,-top+.04,-.25);
            GlStateManager.depthMask(true);GlStateManager.clear(16640);WaterloggedRenderer.render(tile,0,0,0);
            image=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);changed=0;
            for(int x=48;x<80;x++)for(int y=48;y<80;y++)if(image.getRGB(x,y)!=blank.getRGB(x,y))changed++;
            javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"contained-waterline-forward.png"));
            if(changed<900)throw new IllegalStateException("Water disappears looking forward through glass from inside: "+changed);
        }finally{mc.setRenderViewEntity(oldView);camera.set(null,oldCamera);mc.world.setBlockToAir(pos);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
        System.out.println("[vandorlabs][reprolab] fluid-waterline PASS actual perspective pixels from inside water below free surface and looking forward through glass");
    }
}
