package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Loaded-only, bounded discovery. Input changes propose an ownership transfer, never a partial cut. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class VehicleCouplings {
    private static final Map<EntityGroundVehicle,Job> JOBS=new IdentityHashMap<>();
    private static final Map<EntityGroundVehicle,Long> NEXT=new WeakHashMap<>();
    public static void wake(EntityGroundVehicle craft){if(craft!=null)NEXT.remove(craft);}
    private static final class Job {
        final EntityGroundVehicle craft;final EntityPlayerMP actor;final BlockPos primary;final VehicleStructure before,proposed;final VehicleScan scan;final boolean attach;final double x,y,z;final float yaw;
        Job(EntityGroundVehicle craft,EntityPlayerMP actor,BlockPos primary,VehicleStructure proposed,VehicleScan scan,boolean attach){this.craft=craft;this.actor=actor;this.primary=primary;before=craft.structure;this.proposed=proposed;this.scan=scan;this.attach=attach;x=craft.posX;y=craft.posY;z=craft.posZ;yaw=craft.rotationYaw;}
    }
    public static boolean matching(IBlockState a,IBlockState b){return a.getBlock() instanceof BlockCoupler&&b.getBlock() instanceof BlockCoupler&&a.getValue(BlockCoupler.FACING)==b.getValue(BlockCoupler.FACING).getOpposite()&&Math.abs(BlockCoupler.couplingHeight(a)-BlockCoupler.couplingHeight(b))<1e-6;}
    /** A carried pair stays physically latched until the parked-section transaction can commit. */
    public static boolean hasPair(EntityGroundVehicle craft,BlockPos pos){if(craft==null||craft.structure==null)return false;VehicleStructure.Cell cell=craft.structure.at(pos);if(cell==null||!(cell.state.getBlock() instanceof BlockCoupler))return false;VehicleStructure.Cell other=craft.structure.at(pos.offset(cell.state.getValue(BlockCoupler.FACING)));return other!=null&&matching(cell.state,other.state);}
    private static TileCoupler tile(EntityGroundVehicle craft,BlockPos at){TileEntity raw=craft.view.getTileEntity(at);return raw instanceof TileCoupler?(TileCoupler)raw:null;}
    public static EntityPlayerMP authorizer(EntityGroundVehicle craft){
        VehicleCouplingPlan.require(craft!=null&&craft.world instanceof WorldServer&&!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress,"Vehicle automation is unavailable during transfer");WorldServer world=(WorldServer)craft.world;
        if(craft.getControllingPassenger() instanceof EntityPlayerMP){EntityPlayerMP driver=(EntityPlayerMP)craft.getControllingPassenger();if(active(driver,world)){craft.recordCouplingActor(driver);return driver;}}
        EntityPlayerMP actor=craft.couplingActor()==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(craft.couplingActor());
        VehicleCouplingPlan.require(active(actor,world),"Drive the vehicle or open its Coupler dialog to authorize automation in this dimension");return actor;
    }
    private static boolean active(EntityPlayerMP actor,WorldServer world){return actor!=null&&actor.world==world&&!actor.isDead&&!actor.isSpectator()&&actor.connection!=null&&actor.connection.netManager.isChannelOpen();}
    private static EntityPlayerMP actor(EntityGroundVehicle craft,TileCoupler ignored){return authorizer(craft);}
    private static void status(EntityGroundVehicle craft,BlockPos at,String text){TileCoupler tile=craft.view==null?null:tile(craft,at);if(tile!=null)tile.status(text);}
    private static void start(EntityGroundVehicle craft){
        if(craft.structure==null||craft.view==null||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||VehicleCouplingTransfer.busy(craft))return;
        BlockPos release=null;List<BlockPos> candidates=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells)if(cell.state.getBlock() instanceof BlockCoupler){TileCoupler tile=tile(craft,cell.pos);if(tile==null)continue;tile.sample();
            if(hasPair(craft,cell.pos)){TileCoupler other=tile(craft,cell.pos.offset(cell.state.getValue(BlockCoupler.FACING)));if(other!=null&&(!tile.requested()||!other.requested()))release=cell.pos;}
            else if(tile.requested())candidates.add(cell.pos);
        }
        if(release!=null){try{
            TileCoupler source=tile(craft,release);EntityPlayerMP actor=actor(craft,source);VehicleCouplingPlan.require(craft.stopped(),"Stop the vehicle before detaching; section remains attached");status(craft,release,"Checking safe block parking; section remains attached");craft.signals.persist();
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells){IBlockState state=cell.state;if(state.getBlock() instanceof BlockCoupler&&hasPair(craft,cell.pos)){TileCoupler coupler=tile(craft,cell.pos);state=state.withProperty(BlockCoupler.LOCKED,coupler!=null&&coupler.requested());}cells.add(new VehicleStructure.Cell(cell.pos,state,cell.tileData()));}
            VehicleStructure proposed=new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);VehicleWorld view=new VehicleWorld(craft.world,proposed);view.hover=craft.hover;BlockPos controller=craft.structure.controller()==null?craft.structure.seat:craft.structure.controller().pos;
            JOBS.put(craft,new Job(craft,actor,release,proposed,VehicleScan.attachment(view,controller,Collections.emptySet()),false));
        }catch(Exception failure){status(craft,release,failure.getMessage());}return;}
        for(BlockPos at:candidates)try{
            TileCoupler source=tile(craft,at);EntityPlayerMP actor=actor(craft,source);craft.signals.persist();VehicleCouplingPlan.Alignment alignment=new VehicleCouplingPlan.Alignment(craft,craft.structure);
            BlockPos local=VehicleRotation.cell(at,(int)craft.structure.bounds.maxX,(int)craft.structure.bounds.maxZ,alignment.turns);IBlockState state=alignment.placed.at(local).state;EnumFacing face=state.getValue(BlockCoupler.FACING);BlockPos neighbor=alignment.origin.add(local).offset(face);WorldServer world=(WorldServer)craft.world;
            VehicleCouplingPlan.require(world.isBlockLoaded(neighbor,false),"Load the opposing coupler and world section");IBlockState other=world.getBlockState(neighbor);if(!(other.getBlock() instanceof BlockCoupler)){status(craft,at,"Waiting for an opposing world-block coupler");continue;}
            VehicleCouplingPlan.require(matching(state,other),"Align opposing coupler faces and slab heights");TileEntity raw=world.getTileEntity(neighbor);VehicleCouplingPlan.require(raw instanceof TileCoupler,"Opposing coupler data is unavailable");((TileCoupler)raw).sample();VehicleCouplingPlan.require(((TileCoupler)raw).requested(),"Activate both opposing couplers before joining");
            status(craft,at,"Scanning the opposing world section");craft.signals.persist();Set<BlockPos> excluded=new HashSet<>();for(VehicleStructure.Cell cell:craft.structure.cells)excluded.add(alignment.worldCell(craft.structure,cell.pos));
            JOBS.put(craft,new Job(craft,actor,at,null,VehicleScan.attachment(world,neighbor,excluded),true));return;
        }catch(Exception failure){status(craft,at,failure.getMessage());}
    }
    private static void physical(EntityGroundVehicle craft,VehicleStructure proposed){for(VehicleStructure.Cell cell:proposed.cells)if(cell.state.getBlock() instanceof BlockCoupler){TileCoupler coupler=tile(craft,cell.pos);if(coupler!=null)craft.signals.couplerState(coupler,cell.state.getValue(BlockCoupler.LOCKED));}}
    public static void tick(WorldServer world){
        for(EntityGroundVehicle craft:new ArrayList<>(VehicleLookup.loaded(world))){if(JOBS.containsKey(craft)||world.getTotalWorldTime()<NEXT.getOrDefault(craft,0L))continue;NEXT.put(craft,world.getTotalWorldTime()+20);start(craft);}
        int budget=1024;Iterator<Map.Entry<EntityGroundVehicle,Job>> iterator=JOBS.entrySet().iterator();
        while(iterator.hasNext()&&budget>0){Job job=iterator.next().getValue();if(job.craft.world!=world)continue;budget-=256;
            try{
                EntityGroundVehicle craft=job.craft;VehicleCouplingPlan.require(!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress&&job.actor.world==world&&!job.actor.isDead&&!job.actor.isSpectator(),"Coupling authorization changed; section retained");craft.signals.persist();VehicleCouplingPlan.require(craft.structure.write().equals(job.before.write())&&craft.posX==job.x&&craft.posY==job.y&&craft.posZ==job.z&&craft.rotationYaw==job.yaw,"Vehicle moved or its blocks, inputs or inventory changed; retrying");
                if(!job.scan.step(256))continue;iterator.remove();if(job.scan.error!=null)throw new IllegalArgumentException(job.scan.error);
                if(!job.attach&&job.scan.result.cells.size()==job.before.cells.size()){physical(craft,job.proposed);status(craft,job.primary,"Released coupler; section remains joined through another connection");continue;}
                VehicleCouplingPlan plan=job.attach?VehicleCouplingPlan.attach(craft,job.actor,job.primary,job.scan):VehicleCouplingPlan.detach(craft,job.actor,job.primary,job.proposed,job.scan);
                VehicleCouplingTransfer.submit(job.actor,plan,()->status(craft,plan.afterPrimary,plan.attaching?"World section attached":"Section safely restored as world blocks"),failure->status(craft,job.primary,"Coupling recovery: "+failure.getMessage()));
            }catch(Exception failure){if(JOBS.get(job.craft)==job)iterator.remove();status(job.craft,job.primary,"Section retained: "+failure.getMessage());}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){if(event.phase==TickEvent.Phase.END&&event.world instanceof WorldServer)tick((WorldServer)event.world);}
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){JOBS.entrySet().removeIf(e->e.getKey().world==event.getWorld());NEXT.keySet().removeIf(e->e.world==event.getWorld());}
    private VehicleCouplings(){}
}
