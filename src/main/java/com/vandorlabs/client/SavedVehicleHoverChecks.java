package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;

/** Exact saved craft and terrain: ordinary driver input must climb and then level. */
final class SavedVehicleHoverChecks {
    private static int stage,ticks;private static java.util.UUID id;private static double startY;private static double maxPitch,maxY;private static volatile boolean parked;private static volatile Exception parkingFailure;
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("saved Hover stage="+stage+" tick="+ticks+": "+why);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;mc.displayGuiScreen(null);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer world=actor.getServerWorld();
        if(stage==0){if(++ticks<40)return false;mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=null;for(Entity e:world.loadedEntityList)if(e instanceof EntityGroundVehicle&&Math.abs(e.posX+141.226)<2&&Math.abs(e.posZ-31.952)<2)craft=(EntityGroundVehicle)e;
            require(craft!=null&&craft.structure!=null,"exact saved craft present near -138,74,33");id=craft.getUniqueID();startY=craft.posY;maxY=startY;require(craft.structure.cells.size()==97&&craft.articulation.jointCount()==2,"exact 97-block three-section saved craft");
            require(craft.board(actor,craft.structure.seat),"driver boards exact saved seat");require(craft.setHover(true),"saved craft accepts Hover with section lift");System.out.println("[vandorlabs][reprolab] saved-hover START y="+startY+" pitches="+craft.articulation.jointPitch(0)+","+craft.articulation.jointPitch(1));
        }).get();stage=1;ticks=0;return false;}
        if(stage==1){if(mc.player.getRidingEntity()==null)return false;net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),ticks<130);mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=(EntityGroundVehicle)world.getEntityFromUuid(id);require(craft!=null&&craft.hover,"saved craft retains Hover");craft.input(actor,100000+ticks,(byte)0,(byte)0,false);craft.flightInput(actor,(byte)(ticks<130?1:0),false);maxY=Math.max(maxY,craft.posY);maxPitch=Math.max(maxPitch,Math.max(Math.abs(craft.articulation.jointPitch(0)),Math.abs(craft.articulation.jointPitch(1))));
            if(ticks%30==0)System.out.println("[vandorlabs][reprolab] saved-hover tick="+ticks+" y="+craft.posY+" target="+craft.hoverTarget+" pitch="+craft.articulation.jointPitch(0)+","+craft.articulation.jointPitch(1)+" budget="+craft.articulation.budgetStops+" blocked="+craft.articulation.lastObstruction);
        }).get();if(++ticks<190)return false;
        mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(id);require(maxY-startY>8,"climb must exceed former three-block stop: gained="+(maxY-startY));for(int i=0;i<2;i++)require(Math.abs(c.articulation.jointPitch(i))<.05,"Hover eventually levels section "+i+" pitch="+c.articulation.jointPitch(i));require(c.articulation.validPose(),"exact saved craft stays clear of terrain/self while recovering");c.flightInput(actor,(byte)-1,false);}).get();stage=2;ticks=0;return false;}
        if(stage==3){require(parkingFailure==null,"actual block conversion failed: "+parkingFailure);if(!parked){require(++ticks<300,"actual durable parking completes");return false;}System.out.println("[vandorlabs][reprolab] saved-hover-recovery PASS exact97 blocks source terrain climb="+(maxY-startY)+" settled level at altitude and ground; actual durable conversion to native blocks");return true;}
        net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),ticks<150);
        mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(id);c.input(actor,200000+ticks,(byte)0,(byte)0,false);c.flightInput(actor,(byte)(c.posY>startY+.5?-1:0),false);}).get();if(++ticks<180)return false;
        net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(id);require(c.posY<startY+1,"descends near original ground safely");for(int i=0;i<2;i++)require(Math.abs(c.articulation.jointPitch(i))<.05,"ground Hover settles horizontal section "+i+" pitch="+c.articulation.jointPitch(i));require(c.articulation.parkable(),"levelled saved vehicle can pass articulated parking checks");c.stopForParking();VehicleTrainDriveChecks.parkRecovered(world,c,actor,()->parked=true,error->parkingFailure=error);}).get();stage=3;ticks=0;return false;
    }
    private SavedVehicleHoverChecks(){}
}
