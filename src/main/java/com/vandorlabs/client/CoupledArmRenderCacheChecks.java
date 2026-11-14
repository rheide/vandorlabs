package com.vandorlabs.client;

import com.vandorlabs.physics.RigidTransform;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import java.lang.management.ManagementFactory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Exercise real client body queries in distinct main/shadow render targets. */
@SideOnly(Side.CLIENT)
final class CoupledArmRenderCacheChecks {
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Coupled arm render cache: "+why);}
    static void run(EntityGroundVehicle craft,BlockPos child){
        need(craft.world.isRemote&&craft.view!=null,"actual client craft required");
        int body=craft.articulation.bodyAt(child);need(body>0,"actual articulated child required");
        need(OpenGlHelper.isFramebufferEnabled(),"main/shadow framebuffer fixture unavailable");
        int previous=GL11.glGetInteger(0x8CA6);
        Framebuffer main=null,shadow=null;
        try{
            main=new Framebuffer(16,16,true);shadow=new Framebuffer(16,16,true);
            need(main.framebufferObject!=shadow.framebufferObject,"independent main/shadow targets");
            long[] before=VehicleArmVisualMotion.stats();
            main.bindFramebuffer(false);RigidTransform expected=craft.articulation.renderBody(body,.375F);
            long[] warm=VehicleArmVisualMotion.stats();need(warm[1]-before[1]<=1,"at most one fit prepares this frame and partial");
            com.sun.management.ThreadMXBean bean=ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean?(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean():null;
            if(bean!=null&&bean.isThreadAllocatedMemorySupported()&&!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
            long thread=Thread.currentThread().getId(),bytes=bean!=null&&bean.isThreadAllocatedMemoryEnabled()?bean.getThreadAllocatedBytes(thread):-1;
            long started=System.nanoTime();int requests=0;
            for(int framePass=0;framePass<2;framePass++){
                (framePass==0?main:shadow).bindFramebuffer(false);
                for(int i=0;i<512;i++){need(craft.articulation.renderBody(body,.375F)==expected,"same-clock body result retained across main/shadow requests");requests++;}
            }
            long nanos=System.nanoTime()-started;long allocated=bytes<0?-1:bean.getThreadAllocatedBytes(thread)-bytes;
            long[] repeated=VehicleArmVisualMotion.stats();need(repeated[0]==warm[0]&&repeated[1]==warm[1],"repeated body requests must not resample or refit the graph");
            craft.articulation.renderBody(body,.625F);long[] changed=VehicleArmVisualMotion.stats();need(changed[0]==repeated[0]+1&&changed[1]-repeated[1]<=1,"changed partial prepares at most one visual graph");
            shadow.bindFramebuffer(false);RigidTransform revised=craft.articulation.renderBody(body,.625F);need(revised!=null,"new partial result");long[] held=VehicleArmVisualMotion.stats();need(held[0]==changed[0]&&held[1]==changed[1],"shadow request reuses the changed-partial graph");
            System.out.println("[vandorlabs][reprolab] coupled-arm-render-cache PASS requests="+requests+" repeatedFits=0 partialFits="+(changed[1]-repeated[1])+" bytes="+allocated+" nanos="+nanos+" distinct-main-shadow-framebuffers");
        }finally{
            if(main!=null)main.deleteFramebuffer();if(shadow!=null)shadow.deleteFramebuffer();OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,previous);
        }
    }
    private CoupledArmRenderCacheChecks(){}
}
