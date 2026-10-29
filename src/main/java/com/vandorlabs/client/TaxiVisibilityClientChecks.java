package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.gear.GearTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Render the supplied saved Taxi repeatedly while its pose and snapshots advance. */
final class TaxiVisibilityClientChecks {
    private static int ticks,observed,rayHits,drawnFrames,supportedHits;
    private static EntityGroundVehicle live;
    private static int serverId,waitTicks,boardTicks;
    private static boolean setup,clickSent;
    private static VehicleInteraction.Target clickedTarget;
    private static int clientUses,serverUses;
    public static final class ClickAudit {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true)
        public void interaction(net.minecraftforge.event.entity.player.PlayerInteractEvent event){
            if(event.getWorld().isRemote)clientUses++;else serverUses++;
            System.out.println("[vandorlabs][reprolab] saved-taxi interact-event="+event.getClass().getSimpleName()+" remote="+event.getWorld().isRemote+" canceled="+event.isCanceled()+" hand="+event.getHand()+" pos="+event.getEntityPlayer().getPositionVector());
        }
    }
    private static String actorDiagnostic(net.minecraft.entity.player.EntityPlayerMP actor,EntityGroundVehicle craft){
        VehicleInteraction.Target target=VehicleInteraction.pointed(actor);
        return "pos="+actor.getPositionVector()+" yaw="+actor.rotationYaw+" pitch="+actor.rotationPitch+" mode="+actor.interactionManager.getGameType()+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand()+" target="+(target==null?"null":target.craft.getEntityId()+"/"+target.hit.action+"/"+target.hit.cell.pos)+" craft="+craft.getEntityId()+" blocked="+craft.ownershipBlocked+" transfer="+craft.transferInProgress+" seat="+craft.structure.seat+" passengerSeats="+craft.structure.passengerSeats()+" passengers="+craft.getPassengers();
    }
    private static int capturedFrames,samplingAngle;
    private static final int[] angleFrames=new int[3];
    private static boolean longCapture(){return Boolean.getBoolean("vandorlabs.taxiVisibilityLong");}
    private static File frameDirectory;
    private static java.io.PrintWriter frameIndex;
    static void captureFrame(Minecraft mc)throws Exception{
        if(live==null||ticks<100||(!longCapture()&&ticks>=260)||capturedFrames>=240)return;
        if(longCapture()&&(capturedFrames>=120||angleFrames[samplingAngle]>=40))return;
        if(frameDirectory==null){
            frameDirectory=java.nio.file.Files.createTempDirectory("vandor-taxi-mainframes-").toFile();
            frameIndex=new java.io.PrintWriter(new File(frameDirectory,"frames.csv"));
            frameIndex.println("frame,tick,tiles,fallback,shadow,boundFramebuffer,mainFramebuffer,angle");
            System.out.println("[vandorlabs][reprolab] saved-taxi natural-main-frame-crops="+frameDirectory+" maximum=240 crop<=320x320");
        }
        boolean shadow=false;
        try{shadow=Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass").getBoolean(null);}catch(ClassNotFoundException absent){}
        // Read the actual completed main framebuffer after the normal world/shader render.
        BufferedImage full=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        int width=Math.min(320,full.getWidth()),height=Math.min(320,full.getHeight());
        BufferedImage crop=full.getSubimage((full.getWidth()-width)/2,(full.getHeight()-height)/2,width,height);
        ImageIO.write(crop,"png",new File(frameDirectory,String.format(java.util.Locale.ROOT,"frame-%04d.png",capturedFrames)));
        frameIndex.println(capturedFrames+","+ticks+","+RenderGroundVehicle.tileDraws()+","+RenderGroundVehicle.fallbackDraws()+","+shadow+","+GL11.glGetInteger(36006)+","+mc.getFramebuffer().framebufferObject+","+samplingAngle);frameIndex.flush();capturedFrames++;angleFrames[samplingAngle]++;
    }
    static boolean tick(Minecraft mc)throws Exception{
        ticks++;
        if(ticks==1){net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClickAudit());mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.hideGUI=true;}
        if(ticks<80)return false;
        if(!setup){
            NBTTagCompound fixture;
            try(InputStream in=TaxiVisibilityClientChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/followups4-stuck-taxi.nbt")){fixture=CompressedStreamTools.readCompressed(in);}
            mc.getIntegratedServer().callFromMainThread(()->{
                net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
                net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                actor.dismountRidingEntity();actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.inventory.currentItem=0;actor.inventory.clear();actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
                if(Boolean.getBoolean("vandorlabs.taxiVisibilityExisting")){
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)world.getChunkFromBlockCoords(new net.minecraft.util.math.BlockPos(-165+dx*16,83,191+dz*16));
                    EntityGroundVehicle found=null;double nearest=Double.POSITIVE_INFINITY;
                    for(net.minecraft.entity.Entity raw:world.loadedEntityList)if(raw instanceof EntityGroundVehicle){double distance=raw.getDistanceSq(-165,83,191);if(distance<nearest&&distance<256){nearest=distance;found=(EntityGroundVehicle)raw;}}
                    if(found==null||found.structure==null)throw new IllegalStateException("Existing saved Taxi unavailable near -165,83,191");
                    serverId=found.getEntityId();actor.connection.setPlayerLocation(found.posX,found.posY+3,found.posZ-8,0,0);
                    System.out.println("[vandorlabs][reprolab] selected existing saved Taxi id="+serverId+" uuid="+found.getUniqueID()+" pos="+found.getPositionVector()+" nearby-loaded-entities="+world.loadedEntityList.size());return null;
                }
                NBTTagList cells=fixture.getTagList("Cells",10);
                for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));world.getChunkFromBlockCoords(pos);world.setBlockState(pos,net.minecraft.block.Block.getBlockFromName(cell.getString("Name")).getStateFromMeta(cell.getInteger("Meta")),2);}
                EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.readFromNBT(fixture.getCompoundTag("Entity").copy());
                if(craft.structure==null||!world.spawnEntity(craft))throw new IllegalStateException("saved tracked Taxi unavailable");
                serverId=craft.getEntityId();actor.connection.setPlayerLocation(craft.posX,craft.posY+3,craft.posZ-8,0,0);return null;
            }).get();
            mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);mc.player.inventory.currentItem=0;mc.player.inventory.clear();mc.player.capabilities.isFlying=true;
            setup=true;return false;
        }
        if(live==null){
            net.minecraft.entity.Entity raw=mc.world.getEntityByID(serverId);
            if(!(raw instanceof EntityGroundVehicle)||((EntityGroundVehicle)raw).structure==null){if(++waitTicks>300)throw new IllegalStateException("saved Taxi tracked snapshot timeout id="+serverId);return false;}
            live=(EntityGroundVehicle)raw;ticks=81;
            try{System.out.println("[vandorlabs][reprolab] saved-taxi-world shaders-enabled="+Class.forName("Config").getMethod("isShaders").invoke(null));}catch(ClassNotFoundException absent){}
        }
        if(mc.world.getEntityByID(serverId)!=live||live.isDead||live.structure==null)throw new IllegalStateException("Saved Taxi lost tracked client entity/snapshot tick="+ticks);
        if(longCapture()?capturedFrames>=120:ticks>=260)return boarding(mc);
        if(longCapture()&&ticks>6000)throw new IllegalStateException("Taxi long capture timeout actual-main-frames="+capturedFrames+" per-angle="+java.util.Arrays.toString(angleFrames));
        // Keep the saved aircraft in the real world renderer, not a private framebuffer.
        samplingAngle=longCapture()?Math.min(2,capturedFrames/40):(ticks-81)/60%3;
        double angle=samplingAngle*Math.PI/4;
        double radius=Math.max(live.structure.bounds.maxX,live.structure.bounds.maxZ)*.65+5;
        net.minecraft.util.math.Vec3d center=live.toWorld(new net.minecraft.util.math.Vec3d(live.structure.bounds.maxX/2,live.structure.bounds.maxY/2,live.structure.bounds.maxZ/2));
        double x=center.x+Math.sin(angle)*radius,z=center.z-Math.cos(angle)*radius,y=center.y+2;
        mc.player.setPosition(x,y-mc.player.getEyeHeight(),z);
        mc.player.lastTickPosX=mc.player.prevPosX=x;mc.player.lastTickPosY=mc.player.prevPosY=mc.player.posY;mc.player.lastTickPosZ=mc.player.prevPosZ=z;
        double dx=center.x-x,dy=center.y-y,dz=center.z-z;
        mc.player.rotationYaw=mc.player.prevRotationYaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        mc.player.rotationPitch=mc.player.prevRotationPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        if(ticks>100){
            observed++;if(RenderGroundVehicle.tileDraws()>0)drawnFrames++;
            // Inspect real lookup/selection from reachable exterior positions as well as rendering.
            float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
            for(int turn=-90;turn<=90;turn+=15){mc.player.rotationYaw=yaw+turn;VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);if(target!=null&&target.craft==live){rayHits++;if(VehicleInteraction.supported(mc.player,target))supportedHits++;}}
            // A close exterior ring at seat height exercises the same lookup used by right-click.
            net.minecraft.util.math.Vec3d seat=live.toWorld(new net.minecraft.util.math.Vec3d(live.structure.seat).addVector(.5,.5,.5));
            for(int side=0;side<4;side++){
                double ax=side==0?-1.5:side==1?live.structure.bounds.maxX+1.5:live.structure.seat.getX()+.5;
                double az=side==2?-1.5:side==3?live.structure.bounds.maxZ+1.5:live.structure.seat.getZ()+.5;
                net.minecraft.util.math.Vec3d eye=live.toWorld(new net.minecraft.util.math.Vec3d(ax,live.structure.seat.getY()+.8,az));
                mc.player.setPosition(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);
                mc.player.rotationYaw=(float)Math.toDegrees(Math.atan2(eye.x-seat.x,seat.z-eye.z));
                mc.player.rotationPitch=(float)-Math.toDegrees(Math.atan2(seat.y-eye.y,Math.hypot(seat.x-eye.x,seat.z-eye.z)));
                VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
                if(target!=null&&target.craft==live){rayHits++;if(VehicleInteraction.supported(mc.player,target))supportedHits++;}
            }
            mc.player.setPosition(x,y-mc.player.getEyeHeight(),z);mc.player.rotationYaw=yaw;mc.player.rotationPitch=pitch;
            if(ticks%60==0){BufferedImage shot=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());ImageIO.write(shot,"png",new File(System.getProperty("vandorlabs.reprolab","."),"shot_saved_taxi_world_"+ticks+".png"));System.out.println("[vandorlabs][reprolab] saved-taxi-world sample tick="+ticks+" tileDraws="+RenderGroundVehicle.tileDraws()+" fallback="+RenderGroundVehicle.fallbackDraws()+" rayHits="+rayHits);}
        }
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,mc.player.rotationYaw,mc.player.rotationPitch,false));
        return false;
    }
    private static boolean boarding(Minecraft mc)throws Exception{
        if(drawnFrames==0)throw new IllegalStateException("Saved Taxi never emitted tiles in actual world frames");
        if(clickSent){
            boolean serverBoarded=mc.getIntegratedServer().callFromMainThread(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).getRidingEntity()==mc.getIntegratedServer().getWorld(0).getEntityByID(serverId)).get();
            if(serverBoarded&&mc.player.getRidingEntity()==live){System.out.println("[vandorlabs][reprolab] saved-taxi-world-visibility PASS tracked-entity-tick-samples="+observed+" tick-samples-with-tiles="+drawnFrames+" exterior-ray-hits="+rayHits+" supported-hits="+supportedHits+" clientUses="+clientUses+" serverUses="+serverUses+" ordinary-click-retry="+(boardTicks>=40)+" natural-main-frame-crops="+capturedFrames+" actual-frames-per-angle="+java.util.Arrays.toString(angleFrames)+" crop-directory="+frameDirectory+" exterior-interaction server-and-client-boarded; screenshots require review");return true;}
            if(++boardTicks==20||boardTicks==70)System.out.println("[vandorlabs][reprolab] saved-taxi board-wait clientUses="+clientUses+" serverUses="+serverUses+" "+mc.getIntegratedServer().callFromMainThread(()->actorDiagnostic(mc.getIntegratedServer().getPlayerList().getPlayers().get(0),(EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityByID(serverId))).get());
            if(boardTicks==40){mc.objectMouseOver=new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.MISS,mc.player.getPositionEyes(1),net.minecraft.util.EnumFacing.UP,net.minecraft.util.math.BlockPos.ORIGIN);net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);System.out.println("[vandorlabs][reprolab] saved-taxi diagnostic retry ordinary rightClickMouse same ray");}
            if(boardTicks>80)throw new IllegalStateException("Saved Taxi real exterior click failed boarding server="+serverBoarded+" client="+mc.player.getRidingEntity()+" GUI="+mc.currentScreen);
            return false;
        }
        // Search rays from outside the saved hull toward its seat, retaining the exact saved walls/canopies.
        net.minecraft.util.math.Vec3d seat=live.toWorld(new net.minecraft.util.math.Vec3d(live.structure.seat).addVector(.5,.8,.5));
        for(int side=0;side<4;side++)for(double height:new double[]{.3,.8,1.3,1.8})for(double offset:new double[]{-.8,0,.8}){
            double ax=side==0?-1.1:side==1?live.structure.bounds.maxX+1.1:live.structure.seat.getX()+.5+offset;
            double az=side==2?-1.1:side==3?live.structure.bounds.maxZ+1.1:live.structure.seat.getZ()+.5+offset;
            net.minecraft.util.math.Vec3d eye=live.toWorld(new net.minecraft.util.math.Vec3d(ax,live.structure.seat.getY()+height,az));
            mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(eye.x-seat.x,seat.z-eye.z)),(float)-Math.toDegrees(Math.atan2(seat.y-eye.y,Math.hypot(seat.x-eye.x,seat.z-eye.z))));
            VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
            if(target==null||target.craft!=live||(target.hit.action!=VehicleSelection.Action.COCKPIT&&target.hit.action!=VehicleSelection.Action.BOARD))continue;
            if(target.hit.action==VehicleSelection.Action.COCKPIT){
                com.vandorlabs.tiles.TileEntityCockpit original=com.vandorlabs.cockpit.CockpitAssembly.root(live.view,target.hit.cell.pos);
                com.vandorlabs.tiles.TileEntityCockpit probe=new com.vandorlabs.tiles.TileEntityCockpit();probe.readFromNBT(original.writeToNBT(new NBTTagCompound()));probe.setWorld(live.view);probe.setPos(original.getPos());
                java.lang.reflect.Field facing=com.vandorlabs.tiles.TileEntityCockpit.class.getDeclaredField("lastFacing");facing.setAccessible(true);facing.set(probe,original.facing());
                net.minecraft.util.math.Vec3d start=live.toLocal(mc.player.getPositionEyes(1)).subtract(new net.minecraft.util.math.Vec3d(original.getPos()));
                net.minecraft.util.math.Vec3d end=live.toLocal(mc.player.getPositionEyes(1).add(mc.player.getLook(1).scale(8))).subtract(new net.minecraft.util.math.Vec3d(original.getPos()));
                // Clicking a screen/lever/mechanism consumes the click instead of boarding.
                // Probe an unattached client tile so this search does not operate the saved craft.
                if(probe.use(start,end,null)){System.out.println("[vandorlabs][reprolab] saved-taxi skipped-control-ray eye="+eye+" side="+side+" height="+height);continue;}
            }
            final double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;final float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
            mc.getIntegratedServer().callFromMainThread(()->{net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);actor.setPositionAndRotation(x,y,z,yaw,pitch);actor.inventory.currentItem=0;actor.inventory.clear();actor.setSneaking(false);return null;}).get();
            System.out.println("[vandorlabs][reprolab] saved-taxi before-click "+mc.getIntegratedServer().callFromMainThread(()->actorDiagnostic(mc.getIntegratedServer().getPlayerList().getPlayers().get(0),(EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityByID(serverId))).get());
            mc.player.setSneaking(false);mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(x,y,z,yaw,pitch,false));
            mc.objectMouseOver=new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.MISS,eye,net.minecraft.util.EnumFacing.UP,net.minecraft.util.math.BlockPos.ORIGIN);
            net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
            clickedTarget=target;clickSent=true;System.out.println("[vandorlabs][reprolab] saved-taxi exterior click action="+target.hit.action+" cell="+target.hit.cell.pos+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand());return false;
        }
        throw new IllegalStateException("Saved Taxi has no reachable BOARD/COCKPIT target from exterior hull rays; entity retained and rendered frames="+drawnFrames+" supported-hits="+supportedHits);
    }
    static void run(Minecraft mc)throws Exception{
        NBTTagCompound fixture;
        try(InputStream in=TaxiVisibilityClientChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/followups4-stuck-taxi.nbt")){
            if(in==null)throw new IllegalStateException("Taxi visibility fixture missing");
            fixture=CompressedStreamTools.readCompressed(in);
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.readFromNBT(fixture.getCompoundTag("Entity").copy());
        if(craft.structure==null)throw new IllegalStateException("Taxi visibility saved snapshot unavailable");
        net.minecraft.entity.Entity camera=mc.getRenderViewEntity();double cx=camera.posX,cy=camera.posY,cz=camera.posZ,lx=camera.lastTickPosX,ly=camera.lastTickPosY,lz=camera.lastTickPosZ;
        // The server matrices can leave the actor far from the saved aircraft's chunk.
        // This private offscreen craft must still enter the client lookup used by fallback.
        craft.forceSpawn=true;
        mc.world.addEntityToWorld(-47102,craft);
        if(!craft.isAddedToWorld()||!VehicleLookup.loaded(mc.world).contains(craft))throw new IllegalStateException("Saved Taxi offscreen fixture failed client-world registration added="+craft.isAddedToWorld()+" originLoaded="+mc.world.isBlockLoaded(craft.getPosition()));
        Framebuffer frame=new Framebuffer(320,240,true);RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        double span=Math.max(craft.structure.bounds.maxX,Math.max(craft.structure.bounds.maxZ,craft.structure.bounds.maxY))+4;
        GlStateManager.ortho(-span,span,-span*.75,span*.75,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        int samples=0,minimum=Integer.MAX_VALUE;
        try(TurntableRenderState saved=new TurntableRenderState()){
            for(int angle:new int[]{0,45,90})for(int sample=-32;sample<32;sample++){
                if(sample>=0){
                    // Same field updates as a moving Taxi root; preserve all saved configuration.
                    for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity){
                        GearTileEntity root=(GearTileEntity)raw;if(root.isController()&&root.isLandingAssembly())root.captureProgress(.4+.1*Math.sin(sample*.2));
                    }
                    java.util.List<VehicleStructure.Cell> cells=new java.util.ArrayList<>();
                    for(VehicleStructure.Cell cell:craft.structure.cells){net.minecraft.tileentity.TileEntity raw=craft.view.getTileEntity(cell.pos);cells.add(raw instanceof GearTileEntity?new VehicleStructure.Cell(cell.pos,cell.state,raw.writeToNBT(new NBTTagCompound())):cell);}
                    VehicleStructure refreshed=new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);craft.install(VehicleStructure.read(refreshed.write()));
                    craft.setPosition(craft.posX+.03,craft.posY+(sample%2==0?.01:-.01),craft.posZ+.02);
                }
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.rotate(20,1,0,0);GlStateManager.rotate(angle,0,1,0);
                GlStateManager.translate(-craft.structure.bounds.maxX/2,-craft.structure.bounds.maxY/2,-craft.structure.bounds.maxZ/2);
                GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                GlStateManager.enableDepth();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableFog();GlStateManager.color(1,1,1,1);
                camera.setPosition(craft.posX,craft.posY,craft.posZ);camera.lastTickPosX=craft.posX;camera.lastTickPosY=craft.posY;camera.lastTickPosZ=craft.posZ;
                craft.lastTickPosX=craft.posX;craft.lastTickPosY=craft.posY;craft.lastTickPosZ=craft.posZ;
                mc.getRenderManager().cacheActiveRenderInfo(mc.world,mc.fontRenderer,camera,null,mc.gameSettings,1);mc.getRenderManager().setRenderPosition(craft.posX,craft.posY,craft.posZ);
                net.minecraft.client.renderer.culling.Frustum frustum=new net.minecraft.client.renderer.culling.Frustum();frustum.setPosition(craft.posX,craft.posY,craft.posZ);
                if(!renderer.shouldRender(craft,frustum,craft.posX,craft.posY,craft.posZ))throw new IllegalStateException("Saved Taxi frustum rejected visible craft angle="+angle+" sample="+sample);
                RenderGroundVehicle.beginFrame();
                if(sample>=0&&sample%2==0){RenderGroundVehicle.renderMissing(1);if(RenderGroundVehicle.fallbackDraws()==0)throw new IllegalStateException("Saved Taxi hidden-origin fallback omitted craft");}
                else mc.getRenderManager().renderEntityStatic(craft,1,false);
                GL11.glFinish();
                if(sample>=0){
                    BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,frame);int painted=0;
                    for(int y=0;y<240;y++)for(int x=0;x<320;x++)if((image.getRGB(x,y)&0xffffff)!=0)painted++;
                    if(painted<100)throw new IllegalStateException("Saved Taxi disappeared: angle="+angle+" sample="+sample+" pixels="+painted+" tiles="+RenderGroundVehicle.tileDraws());
                    minimum=Math.min(minimum,painted);samples++;
                    if(sample==31)ImageIO.write(image,"png",new File(System.getProperty("vandorlabs.reprolab","."),"shot_saved_taxi_visibility_"+angle+".png"));
                }
            }
            System.out.println("[vandorlabs][reprolab] saved-taxi-visibility PASS exact-snapshot repeated-install moving-origin real-frustum normal-and-hidden-origin-fallback exterior-angles=3 rendered-frames="+samples+" minimum-pixels="+minimum+"; user flicker not reproduced by this fixture");
        }finally{
            craft.setDead();mc.world.removeEntityFromWorld(-47102);RenderGroundVehicle.cleanup(mc.world);camera.setPosition(cx,cy,cz);camera.lastTickPosX=lx;camera.lastTickPosY=ly;camera.lastTickPosZ=lz;mc.getRenderManager().setRenderPosition(lx,ly,lz);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
}
