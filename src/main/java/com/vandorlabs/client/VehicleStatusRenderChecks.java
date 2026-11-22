package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.screen.VehicleStatusLayout;
import com.vandorlabs.screen.VehicleInstrument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import java.awt.image.BufferedImage;
import java.io.File;

/** Shared status renderer must retain modes while omitting unavailable Autopilot controls. */
final class VehicleStatusRenderChecks {
    static void instruments(Minecraft mc,File output)throws Exception{
        for(VehicleInstrument gauge:VehicleInstrument.values()){
            if(gauge==VehicleInstrument.STATUS)continue;
            for(int height:new int[]{64,128}){
                BufferedImage first=instrumentFrame(mc,height,()->VehicleInstrumentRenderer.drawFace(gauge,height,0,0,0,200,80,320));
                BufferedImage second=instrumentFrame(mc,height,()->VehicleInstrumentRenderer.drawFace(gauge,height,24,35,115,2350,80,900));
                int delta=0;
                int x0=height==64?280:112,x1=height==64?498:400;
                int y0=height==64?32:88,y1=height==64?225:440;
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                    if(first.getRGB(x,y)!=second.getRGB(x,y))delta++;
                }
                require(delta>30,gauge.label+" geometry responds to telemetry, including airspeed range: "+height+" pixels="+delta);
                javax.imageio.ImageIO.write(second,"png",new File(output,"shot_vehicle-"+gauge.name().toLowerCase(java.util.Locale.ROOT)+"-"+height+".png"));
            }
        }
        System.out.println("[vandorlabs][reprolab] vehicle-instruments PASS attitude, magnetic heading, altitude, dynamic speed range, compact and full panels");
    }

    static void airborneInstruments(Minecraft mc,File output,EntityGroundVehicle craft,BlockPos screen)throws Exception{
        require(craft.flightReadout().projectedSpeed(com.vandorlabs.physics.FlightEnvelope.MAX_ALTITUDE)>5,
                "flight fixture computes a live instrument speed range above 100 m/s");
        for(VehicleInstrument gauge:VehicleInstrument.values()){
            if(gauge==VehicleInstrument.STATUS)continue;
            BufferedImage image=instrumentFrame(mc,128,()->VehicleInstrumentRenderer.draw(gauge,craft.view,screen,128));
            javax.imageio.ImageIO.write(image,"png",new File(output,"shot_airborne-"+gauge.name().toLowerCase(java.util.Locale.ROOT)+".png"));
        }
    }

    private static BufferedImage instrumentFrame(Minecraft mc,int height,Runnable render){
        Framebuffer frame=new Framebuffer(512,height*4,true);
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        GlStateManager.ortho(0,128,height,0,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);
            GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,1);
            GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();
            render.run();
            return net.minecraft.util.ScreenShotHelper.createScreenshot(512,height*4,frame);
        }finally{
            GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);
            frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }

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
                RedstoneScreenRenderer.drawVehicleContents(craft.view,BlockPos.ORIGIN,64);
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(512,256,frame);
                if(state<0){require(!VehicleStatus.hasController(craft.view,BlockPos.ORIGIN),"missing controller detected");buttons(image,64,0,VehicleStatusLayout.PARK);javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-status-controller-missing.png"));continue;}
                require(VehicleStatus.hasController(craft.view,BlockPos.ORIGIN),"dedicated controller detected");
                require((image.getRGB(484,20)&0xffffff)==(state==2?0x495c69:0x153a29),"Autopilot option visibility state="+state);
                require((image.getRGB(184,236)&0xffffff)==(state==2?0x495c69:0x153a29),"departure option visibility state="+state);
                buttons(image,64,VehicleStatus.availableModes(craft.view,BlockPos.ORIGIN,mc.player),VehicleStatusLayout.TAXI);
                javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-status-autopilot-"+state+".png"));
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        }
        System.out.println("[vandorlabs][reprolab] vehicle-status-controller-live PASS missing controller warns and disables all four modes");
        System.out.println("[vandorlabs][reprolab] vehicle-status-autopilot-live PASS absent controller and waypoint hide Autopilot/departure; controller restores controls; vehicle modes retained");
    }
    static void design(Minecraft mc,File output,net.minecraft.nbt.NBTTagCompound data)throws Exception{
        data=data.copy();data.setInteger("x",0);data.setInteger("y",0);data.setInteger("z",0);
        VehicleStructure structure=VehicleStructure.display(java.util.Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,net.minecraft.block.Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),data)));
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.structure=structure;craft.view=new VehicleWorld(mc.world,structure);craft.view.craft=craft;
        int[] greens=new int[2];
        for(int busy=0;busy<2;busy++){
            craft.transferInProgress=busy==1;Framebuffer frame=new Framebuffer(512,512,true);
            GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,128,128,0,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();
                RedstoneScreenRenderer.drawVehicleContents(craft.view,BlockPos.ORIGIN,128);
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(512,512,frame);int model=0,name=0;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++){int rgb=image.getRGB(x,y)&0xffffff,r=rgb>>16,g=rgb>>8&255,b=rgb&255;if(y>=310&&y<440&&x>=360&&b>g&&g>r&&g>100)model++;if(y>=24&&y<68&&r>150&&g>150&&b>150)name++;if(rgb==0x37d8ad)greens[busy]++;}
                require(model==0,"Vehicle Status leaves the saved 3D model out");require(name>100,"vehicle name rendered");
                javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-design-status-"+(busy==0?"ready":"busy")+".png"));
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        }
        require(greens[0]>greens[1]&&greens[1]>0,"indeterminate progress accompanies four disabled modes");
        System.out.println("[vandorlabs][reprolab] vehicle-design-status PASS name model-removed progress-and-disabled-modes rendered-pixels");
    }
    static void fuel(Minecraft mc,File output,EntityGroundVehicle craft,BlockPos pos)throws Exception{
        VehicleFlightReadout reading=craft.flightReadout();
        require(reading.fuel==1000&&reading.capacity==1000,"full-sized 100% fuel label");
        for(int height:new int[]{64,128}){
            Framebuffer frame=new Framebuffer(512,height*4,true);
            GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,128,height,0,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();
                RedstoneScreenRenderer.drawVehicleContents(craft.view,pos,height);
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(512,height*4,frame);
                buttons(image,height,VehicleStatus.availableModes(craft.view,pos,mc.player),VehicleStatusLayout.TAXI);
                int x0=height==64?57:8,y0=height==64?43:99,glyphs=0,overflow=0;
                for(int y=y0*4;y<(y0+9)*4;y++)for(int x=x0*4;x<512;x++){
                    if((image.getRGB(x,y)&0xffffff)!=0xc1e8ff)continue;
                    if(x>=121*4)overflow++;else glyphs++;
                }
                require(glyphs>40&&overflow==0,"100% fuel amount ink fits "+height+"px face");
                javax.imageio.ImageIO.write(image,"png",new File(output,"shot_vehicle_status_full_fuel_"+height+".png"));
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        }
    }
    private static void buttons(BufferedImage image,int height,int available,int active){
        VehicleStatusLayout layout=VehicleStatusLayout.forHeight(height);
        for(int mode=0;mode<VehicleStatusLayout.MODE_COUNT;mode++){
            int actual=image.getRGB((layout.left(mode)+2)*4,(layout.top(mode)+1)*4)&0xffffff;
            require(actual==(layout.buttonColor(available,mode,active)&0xffffff),"button "+VehicleStatusLayout.label(mode)+" rendered in its 2x2 cell");
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Vehicle status render: "+message);}
}
