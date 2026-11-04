package com.vandorlabs.client;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
/** Exact-JAR rendered comparison: the same cached hull follows independent section poses. */
final class VehicleCouplerRenderChecks {
    static void run(Minecraft mc,File output)throws Exception{
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(VehicleCouplerSectionChecks.train(mc.world,new int[]{3,3,3,3},false,false,false,true,true));craft.setPosition(0,0,0);
        RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());Framebuffer frame=new Framebuffer(512,320,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-17,17,-11,11,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            BufferedImage straight=draw(mc,renderer,craft,frame);
            NBTTagCompound saved=new NBTTagCompound();craft.writeToNBT(saved);NBTTagCompound pose=new NBTTagCompound();pose.setInteger("Version",1);net.minecraft.nbt.NBTTagList joints=new net.minecraft.nbt.NBTTagList();
            for(int i=0;i<3;i++){NBTTagCompound j=new NBTTagCompound();j.setLong("A",new net.minecraft.util.math.BlockPos(1,0,i*6+4).toLong());j.setLong("B",new net.minecraft.util.math.BlockPos(1,0,i*6+5).toLong());j.setDouble("Yaw",i==0?-30:12);j.setDouble("Pitch",i==1?8:0);joints.appendTag(j);}pose.setTag("Joints",joints);saved.setTag("CouplerJointPose",pose);craft.articulation.read(saved);
            BufferedImage bent=draw(mc,renderer,craft,frame);int visible=0,different=0;for(int y=0;y<320;y++)for(int x=0;x<512;x++){if((bent.getRGB(x,y)&0xffffff)!=0x203040)visible++;if(straight.getRGB(x,y)!=bent.getRGB(x,y))different++;}
            if(visible<500||different<500)throw new IllegalStateException("articulated rendered hull missing or unchanged visible="+visible+" different="+different);
            javax.imageio.ImageIO.write(straight,"png",new File(output,"shot_vehicle_coupler_straight.png"));javax.imageio.ImageIO.write(bent,"png",new File(output,"shot_vehicle_coupler_articulated.png"));
            VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.STATE);packet.serverTick=100;packet.jointTopology=craft.articulation.topologyId();packet.jointAngles=new short[]{2000,0,0,0,0,0};craft.authoritative(packet);craft.articulation.clientTick();double middle=craft.articulation.jointYaw(0);if(middle<=-30||middle>=20)throw new IllegalStateException("remote joint must interpolate");
            packet.serverTick=99;packet.jointAngles[0]=-6000;craft.authoritative(packet);packet.serverTick=100;craft.authoritative(packet);craft.articulation.clientTick();craft.articulation.clientTick();if(Math.abs(craft.articulation.jointYaw(0)-20)>1e-8)throw new IllegalStateException("stale/repeated STATE restarted joint interpolation");
            System.out.println("[vandorlabs][reprolab] coupler-remote-motion PASS interpolation stale/repeated-state");
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("articulated render GL error");
            System.out.println("[vandorlabs][reprolab] coupler-render PASS independent-yaw-pitch visible="+visible+" changed-pixels="+different);
        }finally{RenderGroundVehicle.reload();craft.setDead();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static BufferedImage draw(Minecraft mc,RenderGroundVehicle renderer,EntityGroundVehicle craft,Framebuffer frame){
        for(int i=0;i<12;i++){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.rotate(65,1,0,0);GlStateManager.translate(-2,0,-10);RenderGroundVehicle.beginFrame();renderer.doRender(craft,0,0,0,0,1);}GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(512,320,frame);
    }
    private VehicleCouplerRenderChecks(){}
}
