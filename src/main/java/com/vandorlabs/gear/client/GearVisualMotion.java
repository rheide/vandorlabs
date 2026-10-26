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
    private static double frameTime=Double.NaN;
    static void beginFrame(){frameTime=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    private static final Map<World,LinkedHashMap<String,Pose>> WORLDS=new WeakHashMap<>();
    static final class Pose {double progress,authority;boolean initialized;final com.vandorlabs.animation.PlatformMotionSmoothing motion=new com.vandorlabs.animation.PlatformMotionSmoothing(12);}
    static double advance(Pose pose,double authoritative,double now){
        if(!pose.initialized){pose.authority=authoritative;pose.initialized=true;}
        if(authoritative!=pose.authority){pose.motion.accept(authoritative-pose.authority,0,0,now);pose.authority=authoritative;}
        pose.progress=Math.max(0,Math.min(1,authoritative+pose.motion.x(now)));
        return pose.progress;
    }
    static double sample(GearTileEntity tile,float partial){
        World world=tile.getWorld();String key=Long.toString(tile.getPos().toLong());
        if(world instanceof VehicleWorld){VehicleWorld view=(VehicleWorld)world;if(view.craft==null)return tile.renderProgress(partial);key=view.craft.getUniqueID()+":"+key;world=view.parent;}
        LinkedHashMap<String,Pose> cache=WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>(16,.75F,true));Pose pose=cache.get(key);
        if(pose==null){pose=new Pose();cache.put(key,pose);if(cache.size()>2048)cache.remove(cache.keySet().iterator().next());}
        if(Double.isNaN(frameTime))beginFrame();return advance(pose,tile.progress(),frameTime);
    }
    static void clear(){WORLDS.clear();}
    static void check(){
        for(double interval:new double[]{1,3,4,12}){
            Pose pose=new Pose();double previous=1;
            for(int frame=0;frame<=25*interval*8;frame++){
                double now=frame/8D,authority=Math.max(0,1-Math.floor(now/interval)/24D),actual=advance(pose,authority,now);
                if(actual>previous+1e-9||actual<authority-1e-9)throw new IllegalStateException("Arm visuals must remain between received poses under lag");previous=actual;
            }
            if(previous!=0)throw new IllegalStateException("Arm visuals settle at received endpoint");
            double now=25*interval;if(advance(pose,0,now)!=0)throw new IllegalStateException("Repeated paused clock advanced arm");
            if(advance(pose,.25,now+1)!=0)throw new IllegalStateException("Correction or reversal must retain visual continuity");
            if(Math.abs(advance(pose,.25,now+20)-.25)>1e-9)throw new IllegalStateException("Blocked or reversed pose settles at authority");
        }
        System.out.println("[vandorlabs] arm-visual-clock PASS 20/5TPS one/three-tick packets no prediction beyond support repeated pause reversal blocked settling");
    }
}
