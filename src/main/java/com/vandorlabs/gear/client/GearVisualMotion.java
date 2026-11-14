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
    private static boolean supporting;
    static final class Pose {long seeds;double progress,authority;boolean initialized;final com.vandorlabs.animation.PlatformMotionSmoothing motion=new com.vandorlabs.animation.PlatformMotionSmoothing(12);}
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
        if(supporting)pose.seeds++;if(Double.isNaN(frameTime))beginFrame();return advance(pose,tile.progress(),frameTime);
    }
    /** Sample every support at the ownership/STATE boundary, even when its tile was culled. */
    static void support(com.vandorlabs.vehicle.EntityGroundVehicle craft,java.util.List<net.minecraft.util.math.BlockPos> roots){
        if(craft==null||craft.world==null||!craft.world.isRemote||craft.view==null)return;
        boolean previous=supporting;supporting=true;try{
            if(roots==null){for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity)sample((GearTileEntity)raw,1);}
            else for(net.minecraft.util.math.BlockPos root:roots){Object raw=craft.view.tiles.get(root);if(raw instanceof GearTileEntity)sample((GearTileEntity)raw,1);}
        }finally{supporting=previous;}
    }
    static String diagnostic(com.vandorlabs.vehicle.EntityGroundVehicle craft,java.util.Collection<net.minecraft.util.math.BlockPos> roots){
        StringBuilder out=new StringBuilder();for(net.minecraft.util.math.BlockPos root:roots){GearTileEntity tile=(GearTileEntity)craft.view.tiles.get(root);World world=tile.getWorld();String key=Long.toString(tile.getPos().toLong());out.append(root).append(" raw=").append(tile.progress()).append(" world=").append(world==null?"null":world.getClass().getSimpleName()).append(" sameView=").append(world==craft.view);
            if(world instanceof VehicleWorld){VehicleWorld view=(VehicleWorld)world;out.append(" viewCraft=").append(view.craft==null?"null":view.craft.getUniqueID());if(view.craft!=null){key=view.craft.getUniqueID()+":"+key;world=view.parent;}}
            LinkedHashMap<String,Pose> cache=WORLDS.get(world);Pose pose=cache==null?null:cache.get(key);out.append(" cache=").append(pose==null?"null":"seeds:"+pose.seeds+",authority:"+pose.authority+",sample:"+pose.progress).append(" clock=").append(frameTime).append(";");}return out.toString();
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
        Pose drawn=new Pose(),culled=new Pose();advance(drawn,1,0);advance(culled,1,0);for(int update=1;update<=24;update++){double now=update*3,authority=1-update/24D;advance(drawn,drawn.authority,now);advance(culled,culled.authority,now);advance(drawn,authority,now);advance(culled,authority,now);for(int frame=1;frame<12;frame++){double sample=now+frame/4D,value=advance(drawn,authority,sample);if(frame%5==0&&Math.abs(value-advance(culled,authority,sample))>1e-12)throw new IllegalStateException("Culled arm must retain the same support history as its drawn peer");}}
        System.out.println("[vandorlabs] arm-visual-clock PASS 20/5TPS one/three-tick packets no prediction beyond support repeated pause reversal blocked settling");
    }
}
