package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.client.GearRenderer;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Render the actual modular tyre and bracket for forward/reverse travel and both steering directions. */
final class ModularWheelVisualChecks {
    private static final int SIZE=256;
    static void run(Minecraft mc,File directory)throws Exception{
        BufferedImage montage=new BufferedImage(6*SIZE,4*(SIZE+20),BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D canvas=montage.createGraphics();
        try{for(int variant=0;variant<4;variant++){
            final int index=variant;
            VehicleStructure snapshot=mc.getIntegratedServer().callFromMainThread(()->{
                net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);BlockPos source=new BlockPos(420+index*10,32,120);
                GearTileEntity gear=(GearTileEntity)world.getTileEntity(source);BlockPos origin=source.add(-2,-gear.assembly().columnHeight(),-2);List<VehicleStructure.Cell> cells=new ArrayList<>();
                cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));
                for(BlockPos member:gear.members())cells.add(new VehicleStructure.Cell(member.subtract(origin),world.getBlockState(member),VehicleTileData.translated(world.getTileEntity(member).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
                return new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
            }).get();
            EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(snapshot);craft.setPosition(0,0,0);
            GearTileEntity root=null;for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController())root=(GearTileEntity)tile;
            if(root==null)throw new IllegalStateException("Modular wheel render root missing");
            craft.view.origin=new BlockPos(418+variant*10,32-root.assembly().columnHeight(),118);
            BufferedImage[] poses=new BufferedImage[6];poses[0]=draw(mc,craft,root);
            craft.setPosition(0,0,-.31);poses[1]=draw(mc,craft,root);require(difference(poses[0],poses[1])>8,"tyre rolls with Taxi displacement variant="+variant);
            craft.setPosition(0,0,0);poses[2]=draw(mc,craft,root);require(difference(poses[0],poses[2])<20,"reverse travel restores the tyre phase");
            craft.steering=-1;poses[3]=draw(mc,craft,root);craft.steering=1;poses[4]=draw(mc,craft,root);require(difference(poses[3],poses[4])>8,"wheel and bracket steer both ways variant="+variant);
            craft.hover=true;craft.setPosition(0,0,-.4);poses[5]=draw(mc,craft,root);require(difference(poses[0],poses[5])<20,"Hover freezes rolling and steering");
            for(int col=0;col<6;col++){canvas.drawImage(poses[col],col*SIZE,variant*(SIZE+20),null);canvas.setColor(java.awt.Color.WHITE);canvas.drawString(new String[]{"Stopped","Forward","Reverse","Steer left","Steer right","Hover"}[col],col*SIZE+8,variant*(SIZE+20)+SIZE+15);}
            List<VehicleStructure.Cell> missing=new ArrayList<>();for(VehicleStructure.Cell cell:snapshot.cells){boolean wheel=cell.state.getBlock() instanceof GearBlock&&((GearBlock)cell.state.getBlock()).kind()==GearBlock.Kind.WHEEL;missing.add(wheel?new VehicleStructure.Cell(cell.pos,net.minecraft.init.Blocks.AIR.getDefaultState(),null):cell);}
            craft.install(new VehicleStructure(missing,snapshot.seat,snapshot.facing,snapshot.gearCount));
            for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()){GearTileEntity stale=(GearTileEntity)tile;GearGeometry.boxes(stale,stale.progress());draw(mc,craft,stale);}
            craft.setDead();
        }}finally{canvas.dispose();mc.getFramebuffer().bindFramebuffer(true);}
        javax.imageio.ImageIO.write(montage,"png",new File(directory,"shot_modular_wheel_motion.png"));
        System.out.println("[vandorlabs][reprolab] modular-wheel-animation PASS both sizes and arm types actual tyre-roll reverse steering and Hover pixels");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("modular wheel animation: "+message);}
    private static int difference(BufferedImage a,BufferedImage b){int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;return changed;}
    private static BufferedImage draw(Minecraft mc,EntityGroundVehicle craft,GearTileEntity root){
        Framebuffer target=new Framebuffer(SIZE,SIZE,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3.4,3.4,-3.4,3.4,-20,20);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);GlStateManager.loadIdentity();GlStateManager.rotate(18,1,0,0);GlStateManager.rotate(60,0,1,0);
            BlockPos at=root.getPos();GlStateManager.translate(-at.getX()-root.armWidth()*.5,-at.getY()+root.assembly().columnHeight()*.5-.5,-at.getZ()-root.armWidth()*.5);
            GearRenderer renderer=new GearRenderer();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderer.render(root,at.getX(),at.getY(),at.getZ(),1,-1,1);GL11.glFinish();
            return ScreenShotHelper.createScreenshot(SIZE,SIZE,target);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
}
