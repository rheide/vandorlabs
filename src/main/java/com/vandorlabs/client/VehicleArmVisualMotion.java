package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.client.GearRenderer;
import com.vandorlabs.physics.RigidTransform;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleArmVisualFrame;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Cosmetic proper orientations. Contacts share the actual rendered arm phases, including lag corrections. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class VehicleArmVisualMotion {
    private static final int MAX_CRAFTS=128,MAX_BODIES=2048,FRAME_SAMPLES=4;
    private static final VisualClock CLOCK=new VisualClock();
    private static double time;
    private static long sampleRequests,graphFits,cacheHits;
    private static final Map<World,Cache> WORLDS=new WeakHashMap<>();
    private static final class Cache {final LinkedHashMap<UUID,Session> crafts=new LinkedHashMap<>(16,.75F,true);int bodies;}
    static final class Pose {
        RigidTransform from,target;
        double start,last=Double.NaN,duration=1;
        RigidTransform sample(RigidTransform authority,double now){
            if(target==null){from=target=copy(authority);start=now;return target;}
            if(different(authority,target)){
                from=RigidTransform.interpolate(from,target,fraction(now));target=copy(authority);
                duration=Double.isNaN(last)||now-last>24?1:Math.max(1,Math.min(12,now-last));start=last=now;
            }
            return RigidTransform.interpolate(from,target,fraction(now));
        }
        private double fraction(double now){return Math.max(0,Math.min(1,(now-start)/duration));}
    }
    private static final class FrameSample {double time;float partial;RigidTransform[] authority,result;double[] phases;}
    static final class Session {
        final long layout;
        final WeakReference<Object> view;
        final List<BlockPos> roots,carriers;
        final Pose[] poses;
        final RigidTransform[] orientations;
        final double[] phases,raw,minimum,maximum;
        final Map<BlockPos,Integer> rootIndexes=new HashMap<>();
        final FrameSample[] samples=new FrameSample[FRAME_SAMPLES];
        int used;
        double frame=Double.NaN;
        Session(long layout,Object view,List<BlockPos> roots,List<BlockPos> carriers,Session previous,double[] references){
            this.layout=layout;this.view=new WeakReference<>(view);this.roots=roots;this.carriers=carriers;
            poses=new Pose[carriers.size()];orientations=new RigidTransform[poses.length];phases=new double[roots.size()];raw=new double[phases.length];minimum=new double[phases.length];maximum=new double[phases.length];for(int i=0;i<phases.length;i++){rootIndexes.put(roots.get(i),i);Integer old=previous!=null&&previous.layout==layout?previous.rootIndexes.get(roots.get(i)):null;minimum[i]=old==null?references[i]:previous.minimum[old];maximum[i]=old==null?references[i]:previous.maximum[old];}for(int i=0;i<poses.length;i++){int old=previous!=null&&previous.layout==layout?previous.carriers.indexOf(carriers.get(i)):-1;poses[i]=old<0?new Pose():previous.poses[old];}for(int i=0;i<samples.length;i++)samples[i]=new FrameSample();
        }
        FrameSample cached(double now,float partial,RigidTransform[] authority){
            if(frame!=now){frame=now;used=0;for(FrameSample sample:samples){sample.authority=null;sample.result=null;sample.phases=null;}}
            for(int i=0;i<used;i++){FrameSample sample=samples[i];if(sample.time==now&&sample.partial==partial&&sample.authority==authority)return sample;}return null;
        }
        void put(double now,float partial,RigidTransform[] authority,VehicleArmVisualFrame result){
            int slot=used<samples.length?used++:samples.length-1;FrameSample sample=samples[slot];sample.time=now;sample.partial=partial;sample.authority=authority;sample.result=result.bodies;sample.phases=result.phases;
        }
    }
    private static boolean different(RigidTransform a,RigidTransform b){for(int i=0;i<9;i++)if(Math.abs(a.r[i]-b.r[i])>1e-12)return true;return false;}
    private static RigidTransform copy(RigidTransform source){return new RigidTransform(source.r,0,0,0);}
    @SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)time=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)WORLDS.remove(event.getWorld());}
    public static double time(){return time;}
    /** Main client thread only; primitive counters, without retaining or traversing worlds/tiles. */
    public static long[] stats(){long bodies=0,crafts=0;for(Cache cache:WORLDS.values()){bodies+=cache.bodies;crafts+=cache.crafts.size();}return new long[]{sampleRequests,graphFits,cacheHits,bodies,crafts};}
    public static RigidTransform[] sample(EntityGroundVehicle craft,long layout,List<BlockPos> roots,List<BlockPos> carriers,RigidTransform[] authority,float partial){
        sampleRequests++;Cache cache=WORLDS.computeIfAbsent(craft.world,key->new Cache());UUID id=craft.getUniqueID();Session session=cache.crafts.get(id);
        if(session==null||session.layout!=layout||session.view.get()!=craft.view||session.roots!=roots||session.carriers!=carriers){
            if(session!=null)cache.bodies-=session.poses.length;session=new Session(layout,craft.view,roots,carriers,session,craft.articulation.visualArmReferences());cache.crafts.put(id,session);cache.bodies+=session.poses.length;
            while(cache.bodies>MAX_BODIES||cache.crafts.size()>MAX_CRAFTS){Iterator<Session> oldest=cache.crafts.values().iterator();Session removed=oldest.next();cache.bodies-=removed.poses.length;oldest.remove();}
        }
        FrameSample saved=session.cached(time,partial,authority);if(saved!=null){cacheHits++;return saved.result;}
        for(int body=0;body<authority.length;body++)session.orientations[body]=session.poses[body].sample(authority[body],time);
        for(int arm=0;arm<roots.size();arm++){Object raw=craft.view.tiles.get(roots.get(arm));if(!(raw instanceof GearTileEntity))return null;GearTileEntity tile=(GearTileEntity)raw;session.raw[arm]=tile.progress();session.phases[arm]=tile.payload()!=null&&tile.payload().materialized()?tile.progress():GearRenderer.rawRenderedProgress(tile,partial);if(Double.isNaN(session.minimum[arm]))session.minimum[arm]=session.maximum[arm]=tile.progress();session.minimum[arm]=Math.min(session.minimum[arm],tile.progress());session.maximum[arm]=Math.max(session.maximum[arm],tile.progress());}
        graphFits++;VehicleArmVisualFrame result=craft.articulation.fitVisualArmFrame(layout,session.phases,session.minimum,session.maximum,session.orientations);if(result==null)result=new VehicleArmVisualFrame(authority,session.raw.clone());session.put(time,partial,authority,result);boolean settled=true;for(int i=0;i<session.phases.length;i++)settled&=Math.abs(session.phases[i]-session.raw[i])<1e-9;for(int i=0;i<authority.length;i++)settled&=!different(session.orientations[i],authority[i]);if(settled)for(int i=0;i<session.raw.length;i++)session.minimum[i]=session.maximum[i]=session.raw[i];return result.bodies;
    }
    /** Every renderer uses the scalar phase from the same cached constraint frame as its body. */
    public static double armPhase(EntityGroundVehicle craft,BlockPos root,float partial){
        if(!craft.articulation.prepareVisualArmFrame(root,partial))return Double.NaN;Cache cache=WORLDS.get(craft.world);Session session=cache==null?null:cache.crafts.get(craft.getUniqueID());if(session==null||session.frame!=time)return Double.NaN;Integer index=session.rootIndexes.get(root);if(index==null)return Double.NaN;for(int n=session.used-1;n>=0;n--){FrameSample frame=session.samples[n];if(frame.time==time&&frame.partial==partial&&frame.phases!=null)return frame.phases[index];}return Double.NaN;
    }
    private VehicleArmVisualMotion(){}
}
