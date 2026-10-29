package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

final class VehiclePaintingRenderChecks {
    static void run(Minecraft mc,File output)throws Exception{
        net.minecraft.block.Block controller=net.minecraft.block.Block.getBlockFromName("vandorlabs:vehicle_controller");TileEntity tile=controller.createTileEntity(mc.world,controller.getDefaultState());tile.setPos(new BlockPos(3,0,0));NBTTagCompound nativeData=tile.writeToNBT(new NBTTagCompound());
        EntityPainting painting=new EntityPainting(mc.world,new BlockPos(0,0,1),EnumFacing.SOUTH,"Kebab");NBTTagList paintings=new NBTTagList();paintings.appendTag(painting.writeToNBT(new NBTTagCompound()));NBTTagCompound carried=nativeData.copy(),forge=carried.getCompoundTag("ForgeData");forge.setTag("VandorVehiclePaintings",paintings);carried.setTag("ForgeData",forge);
        Framebuffer frame=new Framebuffer(256,256,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.75,.75,-.75,.75,-16,16);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.setPosition(10000,200,10000);craft.lastTickPosX=craft.prevPosX=craft.posX;craft.lastTickPosY=craft.prevPosY=craft.posY;craft.lastTickPosZ=craft.prevPosZ=craft.posZ;
        try(TurntableRenderState saved=new TurntableRenderState()){
            BufferedImage[] images=new BufferedImage[2];RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());
            for(int sample=0;sample<2;sample++){
                VehicleStructure structure=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(2,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(tile.getPos(),controller.getDefaultState(),sample==0?nativeData:carried)),new BlockPos(2,0,0),EnumFacing.NORTH,1);craft.install(structure);
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.188F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
                RenderGroundVehicle.beginFrame();renderer.doRender(craft,-.5,-.5,-2,0,1);GL11.glFinish();images[sample]=net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
            }
            int changed=0;for(int y=0;y<256;y++)for(int x=0;x<256;x++)if(images[0].getRGB(x,y)!=images[1].getRGB(x,y))changed++;
            ImageIO.write(images[1],"png",new File(output,"shot_vehicle_carried_painting.png"));if(changed<1000)throw new IllegalStateException("Carried painting was not visibly rendered: "+changed);
            for(net.minecraft.entity.Entity entity:mc.world.loadedEntityList)if(entity.getUniqueID().equals(painting.getUniqueID()))throw new IllegalStateException("Painting visualization registered a native world entity");
            System.out.println("[vandorlabs][reprolab] vehicle-painting-render PASS actual-native-model changed-pixels="+changed+" inert-client-visual no-native-entity-duplicate");
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);RenderGroundVehicle.reload();}
    }
}
