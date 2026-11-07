package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.MessageGearMotion;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Private captured-pair admission queue. The existing durable coupling transfer is the only publisher. */
final class VehicleArmReleaseRuntime {
    private static final Map<EntityGroundVehicle,Request> requests=new IdentityHashMap<>();
    private static final Map<EntityGroundVehicle,Job> jobs=new IdentityHashMap<>();
    private static final Map<EntityGroundVehicle,VisualHold> visuals=new IdentityHashMap<>();
    private static final class Request {final VehicleArmCouplers.Address head;final long tick;Request(EntityGroundVehicle c,VehicleArmCouplers.Address h){head=h;tick=c.world.getTotalWorldTime();}}
    private static final class Job {final Request request;final EntityPlayerMP actor;final VehicleArmCouplerReleasePlan.Prepared prepared;Job(Request r,EntityPlayerMP a,VehicleArmCouplerReleasePlan.Prepared p){request=r;actor=a;prepared=p;}}
    private static final class VisualHold {final long created;long sent=Long.MIN_VALUE;boolean retiring;VisualHold(EntityGroundVehicle c){created=c.world.getTotalWorldTime();}}
    static boolean hasJob(EntityGroundVehicle c){return jobs.containsKey(c);}
    static boolean preparing(EntityGroundVehicle c){return requests.containsKey(c)||jobs.containsKey(c);}
    private static boolean installed(EntityGroundVehicle c){return c!=null&&c.world instanceof WorldServer&&!c.isDead&&!c.ownershipBlocked&&c.structure!=null&&c.view!=null&&c.world.getEntityByID(c.getEntityId())==c;}
    private static boolean captured(VehicleArmCouplerPairs.Pair p){return p.first.nativeCell==null||p.second.nativeCell==null;}
    static boolean requestNative(EntityGroundVehicle c,BlockPos at){
        if(!VehicleArmCouplers.runtime()||!installed(c)||c.transferInProgress)return false;
        try{for(VehicleArmCouplerPairs.Pair p:VehicleArmCouplerPairs.discover(VehicleArmCouplers.observe(c)).pairs)if(captured(p)){for(VehicleArmCouplers.Head h:new VehicleArmCouplers.Head[]{p.first,p.second})if(at.equals(h.nativeCell)){request(c,h.address);return true;}}}
        catch(IllegalArgumentException|LinkageError failure){TileEntity raw=c.view.tiles.get(at);if(raw instanceof TileCoupler)((TileCoupler)raw).status("Section retained: "+message(failure));return true;}
        return false;
    }
    static void request(EntityGroundVehicle c,VehicleArmCouplers.Address head){
        if(!VehicleArmCouplers.runtime()||!installed(c)||c.transferInProgress||VehicleCouplingTransfer.busy(c))return;
        if(!requests.containsKey(c)&&!jobs.containsKey(c)&&requests.size()>=128){status(c,head,"Release queue is busy; original sections retained");return;}
        if(!requests.containsKey(c)&&!jobs.containsKey(c))requests.put(c,new Request(c,head));visuals.computeIfAbsent(c,k->new VisualHold(c));VehicleCouplings.wake(c);
    }
    /** Returns true only when the captured-pair path owns this admission attempt. */
    static boolean start(EntityGroundVehicle c){
        if(!VehicleArmCouplers.runtime()||!installed(c)||c.transferInProgress||VehicleCouplingTransfer.busy(c))return false;if(jobs.containsKey(c))return true;
        Request r=requests.get(c);
        if(r==null)try{for(VehicleArmCouplerPairs.Pair p:VehicleArmCouplerPairs.discover(VehicleArmCouplers.observe(c)).pairs)if(captured(p)&&(!p.first.requested||!p.second.requested)){VehicleArmCouplers.Head h=!p.first.requested?p.first:p.second;request(c,h.address);r=requests.get(c);break;}}
        catch(IllegalArgumentException|LinkageError failure){for(VehicleStructure.Cell cell:c.structure.couplers){TileEntity raw=c.view.tiles.get(cell.pos);if(raw instanceof TileCoupler)((TileCoupler)raw).status("Section retained: "+message(failure));}return true;}
        if(r==null)return false;if(jobs.size()>=4)return true;
        try{EntityPlayerMP actor=VehicleCouplings.authorizer(c);status(c,r.head,"Checking separated vehicles; section remains attached");c.signals.persist();VehicleArmCouplerReleasePlan.Prepared plan=VehicleArmCouplerReleasePlan.prepare(c,actor,r.head);if(plan.status()!=VehicleArmCouplerReleasePlan.Status.PENDING)throw new IllegalArgumentException(plan.error());jobs.put(c,new Job(r,actor,plan));requests.remove(c);}
        catch(Exception|LinkageError failure){cancel(c,message(failure));}return true;
    }
    static void pollInputs(EntityGroundVehicle c){
        if(!installed(c))return;c.signals.tick();for(TileEntity raw:new ArrayList<>(c.view.tiles.values()))if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;if(gear.isOperational()&&gear.validateParkingHoldInstalled())gear.input();}c.signals.persist();
    }
    static void tick(WorldServer world){
        int budget=1024;for(Map.Entry<EntityGroundVehicle,Job> item:new ArrayList<>(jobs.entrySet())){
            if(budget<256)break;EntityGroundVehicle c=item.getKey();Job job=item.getValue();if(c.world!=world)continue;budget-=256;
            try{if(!installed(c)||world.getTotalWorldTime()-job.request.tick>200)throw new IllegalArgumentException("Release preparation expired; original sections retained");pollInputs(c);VehicleArmCouplerReleasePlan.Status state=job.prepared.step(256);if(state==VehicleArmCouplerReleasePlan.Status.PENDING)continue;if(state!=VehicleArmCouplerReleasePlan.Status.READY)throw new IllegalArgumentException(job.prepared.error());if(!job.prepared.recheck())throw new IllegalArgumentException(job.prepared.error());VehicleCouplingPlan plan=job.prepared.couplingPlan();
                VehicleCouplingTransfer.submit(job.actor,plan,()->{retire(c);status(c,job.request.head,"Separated into two vehicles");},failure->cancel(c,"Coupling recovery: "+message(failure)));
                jobs.remove(c);requests.remove(c); // Existing transfer owns the uninterrupted hold from this point.
            }catch(Exception|LinkageError failure){cancel(c,message(failure));}
        }
        for(Map.Entry<EntityGroundVehicle,Request> item:new ArrayList<>(requests.entrySet()))if(item.getKey().world==world&&(!installed(item.getKey())||world.getTotalWorldTime()-item.getValue().tick>200))cancel(item.getKey(),"Release request expired; original sections retained");
        for(Map.Entry<EntityGroundVehicle,VisualHold> item:new ArrayList<>(visuals.entrySet())){
            EntityGroundVehicle c=item.getKey();VisualHold hold=item.getValue();if(c.world!=world)continue;if(!installed(c)){visuals.remove(c);continue;}long now=world.getTotalWorldTime();
            if(!preparing(c)&&!VehicleCouplingTransfer.busy(c)&&!c.transferInProgress)hold.retiring=true;
            // Tick admission is strict: never send an opposite blocked observation at the same tick.
            if(hold.retiring){if(hold.sent==Long.MIN_VALUE){visuals.remove(c);continue;}if(now>hold.sent&&now>hold.created){MessageGearMotion.send(c,roots(c));visuals.remove(c);}}
            else if(hold.sent==Long.MIN_VALUE&&now>hold.created){MessageGearMotion.sendHeld(c,roots(c));hold.sent=now;}
        }
    }
    static void controlChanged(EntityGroundVehicle c){if(preparing(c))cancel(c,"Controls changed; original sections retained");}
    private static void retire(EntityGroundVehicle c){requests.remove(c);jobs.remove(c);VisualHold hold=visuals.get(c);if(hold!=null)hold.retiring=true;}
    private static void cancel(EntityGroundVehicle c,String reason){Request r=requests.get(c);Job job=jobs.get(c);VehicleArmCouplers.Address head=r!=null?r.head:job==null?null:job.request.head;retire(c);if(installed(c)&&head!=null)status(c,head,"Section retained: "+reason);}
    private static List<BlockPos> roots(EntityGroundVehicle c){List<BlockPos> result=new ArrayList<>();for(TileEntity raw:c.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController())result.add(raw.getPos());return result;}
    private static void status(EntityGroundVehicle c,VehicleArmCouplers.Address head,String text){if(text==null)text="Release unavailable";if(text.length()>256)text=text.substring(0,256);if(head.arm==null){TileEntity raw=c.view.tiles.get(head.cell);if(raw instanceof TileCoupler)((TileCoupler)raw).status(text);}else{VehicleArmCouplerControl control=VehicleArmCouplerControl.resolve(c,head.arm,head.cell);if(control!=null)control.status(text);}}
    static void unload(net.minecraft.world.World world){requests.keySet().removeIf(c->c.world==world);jobs.keySet().removeIf(c->c.world==world);visuals.keySet().removeIf(c->c.world==world);}
    private static String message(Throwable t){return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private VehicleArmReleaseRuntime(){}
}
