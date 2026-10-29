package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.opengl.GL11;

/** Compare actual carried sign glyphs with an empty sign at a distant physical world origin. */
final class VehicleSignRenderChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output)throws Exception{if(++ticks<80)return false;run(mc,output);VehiclePaintingRenderChecks.run(mc,output);return true;}
    static void run(Minecraft mc,File output)throws Exception{
        NBTTagCompound data=new NBTTagCompound();data.setString("id","minecraft:sign");data.setInteger("x",0);data.setInteger("y",0);data.setInteger("z",0);for(int i=1;i<=4;i++)data.setString("Text"+i,"{\"text\":\"\"}");
        VehicleStructure structure=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STANDING_SIGN.getDefaultState(),data),new VehicleStructure.Cell(new BlockPos(2,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null)),new BlockPos(2,0,0),EnumFacing.NORTH,1);
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(structure);craft.setPosition(10000,200,10000);craft.lastTickPosX=craft.prevPosX=craft.posX;craft.lastTickPosY=craft.prevPosY=craft.posY;craft.lastTickPosZ=craft.prevPosZ=craft.posZ;
        net.minecraft.entity.Entity previousView=mc.getRenderViewEntity();net.minecraft.entity.item.EntityArmorStand camera=new net.minecraft.entity.item.EntityArmorStand(mc.world);camera.setPosition(craft.posX+.5,craft.posY+.75,craft.posZ+2);mc.setRenderViewEntity(camera);
        Framebuffer frame=new Framebuffer(256,256,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.75,.75,-.75,.75,-16,16);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        double oldX=TileEntityRendererDispatcher.staticPlayerX,oldY=TileEntityRendererDispatcher.staticPlayerY,oldZ=TileEntityRendererDispatcher.staticPlayerZ;
        try(TurntableRenderState saved=new TurntableRenderState()){
            RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());BufferedImage[] images=new BufferedImage[2];TileEntitySign sign=(TileEntitySign)craft.view.getTileEntity(BlockPos.ORIGIN);
            for(int sample=0;sample<2;sample++){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.188F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
                TileEntityRendererDispatcher.staticPlayerX=10000;TileEntityRendererDispatcher.staticPlayerY=200;TileEntityRendererDispatcher.staticPlayerZ=10000;
                if(sample==1){sign.signText[0]=new TextComponentString("VANDOR LABS");sign.signText[1]=new TextComponentString("CARRIED SIGN");}
                RenderGroundVehicle.beginFrame();renderer.doRender(craft,-.5,-.75,-2,0,1);GL11.glFinish();images[sample]=net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
                if(TileEntityRendererDispatcher.staticPlayerX!=10000||TileEntityRendererDispatcher.staticPlayerY!=200||TileEntityRendererDispatcher.staticPlayerZ!=10000)throw new IllegalStateException("Carried tile renderer leaked local camera coordinates");
            }
            int changed=0;for(int y=0;y<256;y++)for(int x=0;x<256;x++)if(images[0].getRGB(x,y)!=images[1].getRGB(x,y))changed++;
            ImageIO.write(images[1],"png",new File(output,"shot_vehicle_carried_sign_text.png"));
            if(changed<40)throw new IllegalStateException("Carried sign glyphs are not visible: changed pixels="+changed);
            System.out.println("[vandorlabs][reprolab] vehicle-sign-render PASS distant-world-origin real-native-glyphs changed-pixels="+changed+" static-camera-restored");
        }finally{mc.setRenderViewEntity(previousView);TileEntityRendererDispatcher.staticPlayerX=oldX;TileEntityRendererDispatcher.staticPlayerY=oldY;TileEntityRendererDispatcher.staticPlayerZ=oldZ;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);RenderGroundVehicle.reload();}
    }
}
