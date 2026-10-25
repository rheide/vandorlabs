package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockWheel;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.util.EnumFacing;
import java.util.List;

/** Render wheel roots outside the rigid hull mesh, keeping collision and snapshots unchanged. */
final class VehicleWheelRenderer {
    static int draws;
    static void render(EntityGroundVehicle craft,List<VehicleStructure.Cell> wheels,float partial){
        if(wheels.isEmpty())return;
        Minecraft mc=Minecraft.getMinecraft();double distance=VehicleWheelMotion.sample(craft,partial);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        GlStateManager.disableLighting();GlStateManager.disableCull();
        for(VehicleStructure.Cell cell:wheels){
            BlockWheel block=(BlockWheel)cell.state.getBlock();EnumFacing facing=cell.state.getValue(BlockWheel.FACING);
            int light=craft.view.getCombinedLight(cell.pos,0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
            double center=block.wheelSize()==2?1:.5,radius=block.wheelSize()==2?.875:block.wheelSize()==1?.5:.375;
            GlStateManager.pushMatrix();GlStateManager.translate(cell.pos.getX()+.5,cell.pos.getY(),cell.pos.getZ()+.5);
            GlStateManager.rotate(180-facing.getHorizontalAngle(),0,1,0);GlStateManager.translate(-.5,0,-.5);
            GlStateManager.translate(center,center,.75);GlStateManager.rotate(VehicleWheelMotion.steer(craft,cell.pos),0,1,0);
            GlStateManager.rotate(VehicleWheelMotion.roll(craft.structure.isActive(cell.pos)?distance:0,radius,VehicleWheelMotion.alignment(craft.structure.facing,facing.rotateY())),0,0,1);
            GlStateManager.translate(-center,-center,-.75);
            mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(
                    mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(block.getDefaultState().withProperty(BlockWheel.FACING,EnumFacing.NORTH)),1,1,1,1);
            GlStateManager.popMatrix();draws++;
        }
        GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);
    }
    private VehicleWheelRenderer(){}
}
