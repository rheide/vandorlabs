package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import java.awt.image.BufferedImage;
import java.io.File;

/** Shared status renderer must retain modes while omitting unavailable Autopilot controls. */
final class VehicleStatusRenderChecks {
    static void run(Minecraft mc,File output)throws Exception{
        VehicleStructure structure=VehicleStructure.display(java.util.Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,net.minecraft.init.Blocks.STONE.getDefaultState(),null),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),net.minecraft.block.Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),null)));
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.structure=structure;craft.view=new VehicleWorld(mc.world,structure);craft.view.craft=craft;
        for(int state=-1;state<3;state++){
            craft.structure=state<0?VehicleStructure.display(java.util.Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,net.minecraft.init.Blocks.STONE.getDefaultState(),null))):structure;craft.view=new VehicleWorld(mc.world,craft.structure);craft.view.craft=craft;
            craft.view.tiles.clear();
            if(state>0){TileEntityAutopilot tile=state==1?new TileEntityWaypoint():new TileEntityAutopilot();tile.setWorld(craft.view);tile.setPos(BlockPos.ORIGIN);craft.view.tiles.put(BlockPos.ORIGIN,tile);}
            craft.view.tileRevision+=state+2;
            Framebuffer frame=new Framebuffer(512,256,true);
            GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,128,64,0,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();
                RedstoneScreenRenderer.drawVehicleContents(craft.view,BlockPos.ORIGIN,64,0);
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(512,256,frame);
                if(state<0){require(!VehicleStatus.hasController(craft.view,BlockPos.ORIGIN),"missing controller detected");require((image.getRGB(320,84)&0xffffff)==0x362b19,"mode controls omitted without controller");javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-status-controller-missing.png"));continue;}
                require(VehicleStatus.hasController(craft.view,BlockPos.ORIGIN),"dedicated controller detected");
                require((image.getRGB(484,20)&0xffffff)==(state==2?0x495c69:0x153a29),"Autopilot option visibility state="+state);
                require((image.getRGB(184,176)&0xffffff)==(state==2?0x495c69:0x153a29),"departure option visibility state="+state);
                require((image.getRGB(320,84)&0xffffff)==0x37d8ad,"Block/Taxi/Hover mode controls retained");
                javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-status-autopilot-"+state+".png"));
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        }
        System.out.println("[vandorlabs][reprolab] vehicle-status-controller-live PASS missing controller reports warning without mode or Autopilot controls");
        System.out.println("[vandorlabs][reprolab] vehicle-status-autopilot-live PASS absent controller and waypoint hide Autopilot/departure; controller restores controls; vehicle modes retained");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Vehicle status render: "+message);}
}
