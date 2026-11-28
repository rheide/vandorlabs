package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * An installed JourneyMap waypoint 60 blocks ahead at low altitude. Its beacon label must be drawn
 * in the world view, with or without a shader pack.
 */
final class JourneyMapLabelChecks {
    private static final BlockPos EYE=new BlockPos(40,100,40),MARK=EYE.north(60);
    private static int stage,ticks,onFoot;
    private static Future<?> pending;

    private JourneyMapLabelChecks(){}

    static boolean tick(Minecraft mc,File output)throws Exception{
        if(mc.player==null||mc.world==null||mc.getIntegratedServer()==null)return false;
        require(++ticks<800,"timeout");
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==0){
            mc.gameSettings.hideGUI=false;mc.gameSettings.fovSetting=70;mc.gameSettings.clouds=0;mc.gameSettings.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer w=p.getServerWorld();
                w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.setWorldTime(6000);w.getWorldInfo().setRaining(false);
                for(BlockPos at:BlockPos.getAllInBox(EYE.add(-20,-30,-90),EYE.add(20,-30,10)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
                p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();
                p.connection.setPlayerLocation(EYE.getX()+.5,EYE.getY()-p.getEyeHeight(),EYE.getZ()+.5,180,0);
            });
            next(1);return false;
        }
        if(stage==1){
            if(ticks<40)return false;
            Class<?> type=Class.forName("journeymap.client.model.Waypoint$Type");
            Class<?> waypoint=Class.forName("journeymap.client.model.Waypoint");
            Object marker=waypoint.getMethod("at",BlockPos.class,type,int.class).invoke(null,MARK,type.getField("Normal").get(null),0);
            waypoint.getMethod("setName",String.class).invoke(marker,"VANDOR");
            Class<?> store=Class.forName("journeymap.client.waypoint.WaypointStore");
            Object instance=store.getField("INSTANCE").get(null);
            store.getMethod("save",waypoint).invoke(instance,marker);
            next(2);return false;
        }
        if(stage==2){
            mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=0;
            if(ticks<60)return false;
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"journeymap_label-low.png"));
            int label=labelPixels(mc);
            System.out.println(String.format(Locale.ROOT,"[vandorlabs] journeymap-labels measured low-altitude label pixels=%d",label));
            require(label>300,"JourneyMap waypoint label is drawn below Y=256 on foot: "+label+" label pixels");
            onFoot=label;
            // Repeat seated in a Taxi craft, where the cockpit camera hooks reposition the beacons.
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer w=p.getServerWorld();
                BlockPos seat=EYE.down(2),deck=seat.down();
                for(BlockPos at:BlockPos.getAllInBox(deck.add(-1,0,-1),deck.add(1,0,1)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
                Block wheel=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof com.vandorlabs.blocks.BlockWheel){wheel=candidate;break;}
                for(BlockPos at:new BlockPos[]{deck.add(-1,-1,-1),deck.add(1,-1,-1),deck.add(1,-1,1)})w.setBlockState(at,wheel.getDefaultState(),2);
                for(BlockPos at:BlockPos.getAllInBox(deck.add(-4,-2,-4),deck.add(4,-2,4)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
                w.setBlockState(seat,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),2);
                com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,seat,w.getBlockState(seat),p,new net.minecraft.item.ItemStack(com.vandorlabs.blocks.ModBlocks.PILOT_SEAT));
                w.setBlockState(seat.east(),com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
                p.connection.setPlayerLocation(seat.getX()+.5,seat.getY()+1,seat.getZ()+2.5,180,0);
                com.vandorlabs.vehicle.VehicleService.screenMode(p,seat.east(),null,1);
            });
            next(3);return false;
        }
        if(stage==3){
            com.vandorlabs.vehicle.EntityGroundVehicle craft=craft(mc);
            if(ticks<40||craft==null||craft.structure==null)return false;
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                for(net.minecraft.entity.Entity e:p.world.loadedEntityList)
                    if(e instanceof com.vandorlabs.vehicle.EntityGroundVehicle&&!e.isDead)require(((com.vandorlabs.vehicle.EntityGroundVehicle)e).board(p),"board the Taxi craft");
            });
            next(4);return false;
        }
        if(stage==4){
            if(mc.player.getRidingEntity()==null||ticks<40)return false;
            mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=0;
            if(ticks<80)return false;
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"journeymap_label-seated.png"));
            int label=labelPixels(mc);
            System.out.println(String.format(Locale.ROOT,"[vandorlabs] journeymap-labels measured seated label pixels=%d (on foot %d)",label,onFoot));
            require(label>300,"JourneyMap waypoint label is drawn below Y=256 while seated in a craft: "+label+" label pixels (on foot "+onFoot+")");
            System.out.println("[vandorlabs] journeymap-labels PASS low-altitude waypoint label visible on foot and seated in a craft");
            return true;
        }
        return false;
    }

    /** Dark label-plate pixels in a box around the screen centre above the horizon, where the aimed waypoint label sits. */
    private static int labelPixels(Minecraft mc){
        // Upper-middle band: the label sits just above the horizon; the ground and craft are below it.
        int w=mc.displayWidth/3,h=mc.displayHeight/6,x=mc.displayWidth/3,y=mc.displayHeight/2;
        ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
        int previous=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if(net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled())mc.getFramebuffer().bindFramebuffer(false);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(x,y,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER,previous);
        int count=0;
        for(int i=0;i<w*h;i++){
            // JourneyMap draws beacons only with the GUI shown; skip the crosshair at the centre.
            if(Math.abs(i%w-w/2)<14&&Math.abs(i/w-h/2)<14)continue;
            int r=pixels.get(i*4)&255,g=pixels.get(i*4+1)&255,b=pixels.get(i*4+2)&255;
            // The label's dark plate against bright sky (shader packs tint the glyphs, not the plate's darkness).
            if(r<120&&g<120&&b<150)count++;
        }
        return count;
    }
    private static com.vandorlabs.vehicle.EntityGroundVehicle craft(Minecraft mc){
        for(net.minecraft.entity.Entity e:mc.world.loadedEntityList)if(e instanceof com.vandorlabs.vehicle.EntityGroundVehicle&&!e.isDead)return (com.vandorlabs.vehicle.EntityGroundVehicle)e;
        return null;
    }
    private static void next(int value){stage=value;ticks=0;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("journeymap-labels stage="+stage+" "+message);}
}
