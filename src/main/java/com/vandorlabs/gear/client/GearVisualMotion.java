package com.vandorlabs.gear.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import java.util.*;

/** Cosmetic motion only; authoritative collision and serialized timelines remain on the tile. */
final class GearVisualMotion {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<World,LinkedHashMap<String,Pose>> WORLDS=new WeakHashMap<>();
    static final class Pose {double progress,last;boolean initialized;}
    static double advance(Pose pose,double authoritative,boolean target,boolean running,double now){
        if(!pose.initialized){pose.progress=authoritative;pose.last=now;pose.initialized=true;}
        double step=Math.max(0,now-pose.last)/GearTileEntity.ANIMATION_TICKS;pose.last=now;
        double goal=running?(target?1:0):authoritative;
        pose.progress=goal>pose.progress?Math.min(goal,pose.progress+step):Math.max(goal,pose.progress-step);
        return pose.progress;
    }
    static double sample(GearTileEntity tile,float partial){
        World world=tile.getWorld();String key=Long.toString(tile.getPos().toLong());
        if(world instanceof VehicleWorld){VehicleWorld view=(VehicleWorld)world;if(view.craft!=null)key=view.craft.getUniqueID()+":"+key;world=view.parent;}
        LinkedHashMap<String,Pose> cache=WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>(16,.75F,true));Pose pose=cache.get(key);
        if(pose==null){pose=new Pose();cache.put(key,pose);if(cache.size()>2048)cache.remove(cache.keySet().iterator().next());}
        return advance(pose,tile.renderProgress(partial),tile.target(),tile.isOperational()&&!tile.motionBlocked(),CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused()));
    }
    static void clear(){WORLDS.clear();}
    static void check(){
        Pose pose=new Pose();double previous=1;
        for(int frame=0;frame<=120;frame++){double now=frame/3D,server=Math.max(0,1-(frame/18)*3/24D);double actual=advance(pose,server,false,true,now);if(actual>previous+1e-9)throw new IllegalStateException("Lag correction reversed arm visuals");previous=actual;}
        if(previous!=0)throw new IllegalStateException("Arm visual reaches endpoint under delayed authority");
        double paused=advance(pose,.2,true,true,40);if(paused!=0)throw new IllegalStateException("Repeated paused clock advanced arm");
        double reversed=advance(pose,0,true,true,46);if(Math.abs(reversed-.25)>1e-9)throw new IllegalStateException("Arm reversal must start at current visual pose");
        if(advance(pose,.1,true,false,49)>=reversed)throw new IllegalStateException("Blocked arm must settle toward authoritative position");
        System.out.println("[vandorlabs] arm-visual-clock PASS delayed repeated corrections monotonic progress pause reversal blocked settling");
    }
}
