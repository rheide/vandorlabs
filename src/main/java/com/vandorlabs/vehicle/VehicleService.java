package com.vandorlabs.vehicle;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.items.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Owns expiring server previews; the client can confirm only the server's exact selection. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class VehicleService {
    private static final Map<UUID,Preview> previews=new HashMap<>();
    private static final Map<UUID,Job> jobs=new HashMap<>();
    private static final Map<String,Long> requests=new HashMap<>();
    private static final Set<World> recovered=Collections.newSetFromMap(new WeakHashMap<World,Boolean>());
    private static final class Job {final EntityPlayerMP player;final World world;final VehicleScan scan;boolean signal,taxi;int ticks;Job(EntityPlayerMP p,BlockPos pos){player=p;world=p.world;scan=new VehicleScan(p.world,pos);}}
    public static void taxiAssembly(EntityPlayerMP player,BlockPos seat) {
        if(player.isSpectator() || !(player.world.getTileEntity(seat) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)
                || !((com.vandorlabs.tiles.TileEntityPilotSeat)player.world.getTileEntity(seat)).isTaxiMode() || !seatedAt(player,seat))return;
        if(jobs.containsKey(player.getUniqueID()) || jobs.size()>=16)return;
        previews.remove(player.getUniqueID());Job job=new Job(player,seat);job.taxi=true;jobs.put(player.getUniqueID(),job);
        player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Taxi Mode: inspecting connected craft..."),true);
    }
    private static boolean seatedAt(EntityPlayerMP player,BlockPos pos) {
        return player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat
                && pos.equals(((com.vandorlabs.entity.EntityChairSeat)player.getRidingEntity()).getChairPos());
    }
    public static void taxiParking(EntityPlayerMP player,EntityGroundVehicle craft) {
        craft.stopForParking();craft.taxiReturnPending=true;craft.taxiReturnActor=player.getUniqueID();craft.taxiNextAttempt=0;
    }
    private static void returnTaxi(WorldServer world,EntityGroundVehicle craft) {
        if(!craft.taxiReturnPending || craft.isDead || craft.ownershipBlocked || craft.structure==null || craft.transferInProgress
                || !craft.getPassengers().isEmpty() || world.getTotalWorldTime()<craft.taxiNextAttempt)return;
        boolean first=craft.taxiNextAttempt==0;craft.taxiNextAttempt=world.getTotalWorldTime()+20;
        EntityPlayerMP player=craft.taxiReturnActor==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(craft.taxiReturnActor);
        if(player==null || player.world!=world || player.isDead || player.isSpectator())return;
        if(!VehicleTaxiMode.enabled(craft.structure)){craft.taxiReturnPending=false;return;}
        try {
            craft.stopForParking();BlockPos origin=VehicleRotation.plan(craft).origin;
            validateParking(player,craft,origin,true,false);
            VehicleTransfer.park(world,craft,origin,player,false);
            if(distance(player,craft.getEntityBoundingBox())<=64)com.vandorlabs.entity.SafeDismount.move(player,com.vandorlabs.entity.SafeDismount.find(world,player,new AxisAlignedBB(origin.add(craft.structure.seat))),new AxisAlignedBB(origin.add(craft.structure.seat)));
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Taxi Mode: craft restored as blocks."),true);
        }catch(Exception failure){if(first)VehicleNetwork.error(player,"Taxi Mode could not park: "+failure.getMessage()+". Craft preserved; parking retries automatically. You can reboard or use the Configurizer.");}
    }
    @SubscribeEvent public static void taxiExit(net.minecraftforge.event.entity.EntityMountEvent event) {
        if(event.isMounting() || !(event.getEntityMounting() instanceof EntityPlayerMP) || !(event.getEntityBeingMounted() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle craft=(EntityGroundVehicle)event.getEntityBeingMounted();EntityPlayerMP player=(EntityPlayerMP)event.getEntityMounting();
        if(craft.transferInProgress || craft.isDead || player.isDead || !player.connection.netManager.isChannelOpen() || !VehicleTaxiMode.enabled(craft.structure))return;
        try{VehicleRotation.plan(craft);}catch(IllegalArgumentException failure){event.setCanceled(true);if(craft.world.getTotalWorldTime()>=craft.taxiNextWarning){craft.taxiNextWarning=craft.world.getTotalWorldTime()+20;VehicleNetwork.error(player,failure.getMessage());}}
    }
    private static final java.util.ArrayDeque<Signal> signals=new java.util.ArrayDeque<>();
    private static final class Signal {
        final com.vandorlabs.tiles.TileEntityPilotSeat seat;final EntityGroundVehicle craft;
        Signal(com.vandorlabs.tiles.TileEntityPilotSeat s,EntityGroundVehicle e){seat=s;craft=e;}
    }
    public static void signal(com.vandorlabs.tiles.TileEntityPilotSeat seat,EntityGroundVehicle craft) {
        if(signals.size()<64)signals.add(new Signal(seat,craft));
    }
    private static void signal(WorldServer world,Signal signal) {
        com.vandorlabs.tiles.TileEntityPilotSeat seat=signal.seat;
        if(seat.owner==null)return;
        EntityPlayerMP player=world.getMinecraftServer().getPlayerList().getPlayerByUUID(seat.owner);
        if(player==null || player.world!=world)return;
        try {
            if(signal.craft!=null) {
                EntityGroundVehicle craft=signal.craft;if(craft.isDead || craft.ownershipBlocked)return;
                BlockPos origin=VehicleRotation.plan(craft).origin;
                validateParking(player,craft,origin,true,false);VehicleTransfer.park(world,craft,origin,player);
            } else {
                if(world.getTileEntity(seat.getPos())!=seat || jobs.size()>=16)return;
                for(Job existing:jobs.values())if(existing.world==world && existing.scan.startPosition().equals(seat.getPos()))return;
                Job job=new Job(player,seat.getPos());job.signal=true;jobs.put(UUID.randomUUID(),job);
            }
        }catch(Exception e){VandorLabs.logger.warn("Pilot Seat signal failed: "+e.getMessage());VehicleNetwork.error(player,"Pilot Seat signal: "+e.getMessage());}
    }
    private static void assembleSignal(Job job) throws Exception {
        VehicleStructure structure=job.scan.result;WorldServer world=(WorldServer)job.world;BlockPos origin=job.scan.origin;
        require(structure.matches(world,origin),"Blocks or settings changed during signal scan.");
        for(BlockPos boundary:job.scan.airBoundary)require(world.isBlockLoaded(boundary) && world.isAirBlock(boundary),"Craft connections changed during signal scan.");
        require(clearOfPeople(world,new VehicleCollision(new VehicleWorld(world,structure)),origin,job.player),"Another occupant obstructs assembly.");
        for(VehicleStructure.Cell cell:structure.cells){BlockPos at=origin.add(cell.pos);permission(job.player,at);require(!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,at,cell.state,job.player)),"A protection rule prevented assembly.");}
        VehicleTransfer.assemble(world,structure,origin,job.player);
    }
    private static final class Preview {
        final UUID token=UUID.randomUUID();final WorldServer world;final VehicleStructure structure;final BlockPos origin;final EntityGroundVehicle entity;final long expires;
        final int turns;
        final Set<BlockPos> airBoundary=new HashSet<>();
        Preview(WorldServer w,VehicleStructure s,BlockPos p,EntityGroundVehicle e){world=w;structure=s;origin=p;entity=e;turns=e==null?0:VehicleRotation.turns(e.rotationYaw);expires=w.getTotalWorldTime()+1200;}
    }
    private static boolean tool(EntityPlayerMP p){return !p.isSpectator() && p.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER;}
    private static void require(boolean test,String message){if(!test)throw new IllegalArgumentException(message);}
    public static void previewAssembly(EntityPlayerMP player,BlockPos pos) {
        if(!tool(player) || !player.world.isBlockLoaded(pos) || player.getDistanceSqToCenter(pos)>64)return;
        if(player.world.getBlockState(pos).getValue(BlockPilotSeat.UPPER))pos=pos.down();
        if(jobs.containsKey(player.getUniqueID()))return;
        previews.remove(player.getUniqueID());jobs.put(player.getUniqueID(),new Job(player,pos));
        player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Inspecting connected craft..."),true);
    }
    public static void previewParking(EntityPlayerMP player,EntityGroundVehicle entity) {
        if(!tool(player) || entity.structure==null || distance(player,entity.getEntityBoundingBox())>64)return;
        try {
            VehicleRotation.Plan plan=VehicleRotation.plan(entity);BlockPos origin=plan.origin;
            validateParking(player,entity,origin,false);
            Preview preview=new Preview(player.getServerWorld(),entity.structure,origin,entity);previews.put(player.getUniqueID(),preview);
            VehicleNetwork.send(player,plan.structure,entity.getEntityId(),preview.token,origin,true,true);
        }catch(Exception e){VehicleNetwork.error(player,e.getMessage());}
    }
    public static void confirm(EntityPlayerMP player,UUID token) {
        Preview preview=previews.get(player.getUniqueID());
        if(preview==null || !preview.token.equals(token))return;
        previews.remove(player.getUniqueID());
        try {
            require(tool(player) && preview.world==player.world && preview.expires>=player.world.getTotalWorldTime(),"Preview expired; inspect the craft again.");
            if(preview.entity!=null) {
                require(VehicleRotation.turns(preview.entity.rotationYaw)==preview.turns,"Craft rotated; inspect it again.");
                validateParking(player,preview.entity,preview.origin,true);
                require(preview.entity.structure==preview.structure,"Craft changed; inspect it again.");
                VehicleTransfer.park(preview.world,preview.entity,preview.origin,player);
            } else {
                require(player.getDistanceSqToCenter(preview.origin.add(preview.structure.seat))<=64,"Move closer to the Pilot Seat.");
                require(preview.structure.matches(player.world,preview.origin),"Blocks or settings changed; inspect the craft again.");
                for(BlockPos p:preview.airBoundary)require(player.world.isBlockLoaded(p) && player.world.isAirBlock(p),"The craft's connections changed; inspect it again.");
                VehicleCollision geometry=new VehicleCollision(new VehicleWorld(player.world,preview.structure));
                require(clearOfPeople(player.world,geometry,preview.origin,player),"Another occupant obstructs assembly.");
                for(VehicleStructure.Cell cell:preview.structure.cells) {
                    BlockPos p=preview.origin.add(cell.pos);permission(player,p);
                    require(!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.world,p,cell.state,player)),"A protection rule prevented assembly.");
                }
                VehicleTransfer.assemble(preview.world,preview.structure,preview.origin,player);
            }
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(preview.entity==null?"Craft assembled. Right-click to drive; W/S drives, A/D steers, Space brakes, Sneak dismounts.":"Craft parked as blocks."),false);
        }catch(Exception e){VandorLabs.logger.warn("Vehicle action failed",e);VehicleNetwork.error(player,e.getMessage()==null?"Vehicle transfer failed; the recovery record was retained.":e.getMessage());}
    }
    public static void configureTaxi(EntityPlayerMP player,VehicleNetwork.Packet packet) {
        if(player.isSpectator())return;
        // The placed-seat dialog uses its existing container authorization.
        if(packet.token.equals(new UUID(0,0))) {
            if(!(player.openContainer instanceof com.vandorlabs.container.ContainerRedstoneChannel) || !player.world.isBlockLoaded(packet.origin))return;
            com.vandorlabs.container.ContainerRedstoneChannel container=(com.vandorlabs.container.ContainerRedstoneChannel)player.openContainer;
            net.minecraft.tileentity.TileEntity tile=player.world.getTileEntity(packet.origin);
            if(tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat && container.member==tile && container.canInteractWith(player)) {
                ((com.vandorlabs.tiles.TileEntityPilotSeat)tile).setTaxiMode(packet.flag);previews.remove(player.getUniqueID());
            }
            return;
        }
        Preview preview=previews.get(player.getUniqueID());
        if(preview==null || !preview.token.equals(packet.token))return;
        try {
            require(tool(player) && preview.world==player.world && preview.expires>=player.world.getTotalWorldTime(),"Preview expired; inspect again.");
            VehicleStructure changed=VehicleTaxiMode.withSetting(preview.structure,packet.flag);
            if(preview.entity==null) {
                require(player.getDistanceSqToCenter(preview.origin.add(preview.structure.seat))<=64 && preview.structure.matches(player.world,preview.origin),"Craft changed; inspect again.");
                permission(player,preview.origin.add(preview.structure.seat));
                ((com.vandorlabs.tiles.TileEntityPilotSeat)player.world.getTileEntity(preview.origin.add(preview.structure.seat))).setTaxiMode(packet.flag);
            } else {
                require(!preview.entity.isDead && !preview.entity.ownershipBlocked && preview.entity.structure==preview.structure
                        && distance(player,preview.entity.getEntityBoundingBox())<=64,"Craft changed; inspect again.");
                VehicleTaxiMode.apply(preview.entity,packet.flag);changed=preview.entity.structure;
                VehicleNetwork.Packet update=new VehicleNetwork.Packet(VehicleNetwork.TAXI);update.entity=preview.entity.getEntityId();update.token=preview.entity.getUniqueID();update.flag=packet.flag;
                com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(update,preview.entity);
                if(preview.entity.getControllingPassenger() instanceof EntityPlayerMP)com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(update,(EntityPlayerMP)preview.entity.getControllingPassenger());
            }
            Preview next=new Preview(preview.world,changed,preview.origin,preview.entity);next.airBoundary.addAll(preview.airBoundary);previews.put(player.getUniqueID(),next);
            VehicleNetwork.send(player,next.entity==null?next.structure:VehicleRotation.plan(next.entity).structure,next.entity==null?-1:next.entity.getEntityId(),next.token,next.origin,true,next.entity!=null);
        }catch(Exception failure){VehicleNetwork.error(player,failure.getMessage());}
    }
    public static void permission(EntityPlayerMP player,BlockPos p) {
        require(player.world.isBlockLoaded(p) && player.world.getWorldBorder().contains(p) && p.getY()>=0 && p.getY()<player.world.getHeight(),"A target cell is outside the loaded world.");
        require(player.world.isBlockModifiable(player,p) && player.canPlayerEdit(p,EnumFacing.UP,player.getHeldItemMainhand()),"You cannot modify a target block.");
    }
    private static void validateParking(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos origin,boolean events) {validateParking(player,entity,origin,events,true);}
    private static void validateParking(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos origin,boolean events,boolean reach) {
        require(!entity.isDead && entity.world==player.world && (!reach || distance(player,entity.getEntityBoundingBox())<=64),"Move closer to the craft.");
        require(entity.stopped(),"Stop before parking.");
        VehicleRotation.Plan plan=VehicleRotation.plan(entity);
        require(plan.origin.equals(origin),"Craft moved; open a new parking preview.");
        VehicleCollision parkedGeometry=new VehicleCollision(new VehicleWorld(player.world,plan.structure));
        for(VehicleStructure.Cell cell:plan.structure.cells) {
            BlockPos p=origin.add(cell.pos);permission(player,p);require(player.world.isAirBlock(p),"Parking is blocked at "+p);
        }
        for(net.minecraft.tileentity.TileEntity raw:entity.view.tiles.values())if(raw instanceof com.vandorlabs.tiles.TileEntityRampController)
            for(BlockPos source:((com.vandorlabs.tiles.TileEntityRampController)raw).vehicleSources())if(entity.structure.at(source)==null){permission(player,origin.add(source));require(player.world.isAirBlock(origin.add(source)),"Parking blocks a ramp recovery source at "+origin.add(source));}
        require(clearOfPeople(player.world,parkedGeometry,origin,player,entity.getControllingPassenger()),"The parking area is occupied.");
        for(AxisAlignedBB box:parkedGeometry.boxes)
            require(player.world.getCollisionBoxes(entity,box.offset(origin).shrink(1e-6)).isEmpty(),"Snapped craft geometry overlaps an obstacle.");
    }
    private static boolean clearOfPeople(World world,VehicleCollision geometry,BlockPos origin,Entity... allowed) {
        java.util.Set<Entity> excluded=new java.util.HashSet<>();for(Entity e:allowed)if(e!=null){excluded.add(e);if(e.isRiding())excluded.add(e.getRidingEntity());}
        for(Entity person:world.getEntitiesWithinAABB(Entity.class,geometry.bounds.offset(origin).grow(.01),e->!e.isDead && !excluded.contains(e) && (e instanceof net.minecraft.entity.EntityLivingBase || e instanceof com.vandorlabs.entity.EntityChairSeat && !e.getPassengers().isEmpty())))
            for(AxisAlignedBB box:geometry.boxes)if(box.offset(origin).grow(.01).intersects(person.getEntityBoundingBox()))return false;
        return true;
    }
    private static double distance(Entity e,AxisAlignedBB b){double x=MathHelper.clamp(e.posX,b.minX,b.maxX),y=MathHelper.clamp(e.posY,b.minY,b.maxY),z=MathHelper.clamp(e.posZ,b.minZ,b.maxZ);return e.getDistanceSq(x,y,z);}
    public static boolean allowSnapshot(EntityPlayerMP player,EntityGroundVehicle vehicle) {
        long now=System.currentTimeMillis();requests.entrySet().removeIf(e->now-e.getValue()>10000);
        String key=player.getUniqueID()+":"+vehicle.getUniqueID();Long before=requests.get(key);
        if(before!=null && now-before<1500 || requests.size()>1024)return false;requests.put(key,now);return true;
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        VehicleLookup.unload(event.getWorld());
        jobs.values().removeIf(job->job.world==event.getWorld());previews.values().removeIf(preview->preview.world==event.getWorld());
        signals.removeIf(signal->(signal.craft==null?signal.seat.getWorld():signal.craft.world)==event.getWorld());
    }
    @SubscribeEvent public static void collision(net.minecraftforge.event.world.GetCollisionBoxesEvent event) {
        if(event.getWorld() instanceof VehicleWorld)return;
        for(EntityGroundVehicle craft:VehicleLookup.near(event.getWorld(),event.getAabb())) {
            if(!craft.getEntityBoundingBox().intersects(event.getAabb()))continue;
            if(craft==event.getEntity() || craft.collision==null || craft.isPassenger(event.getEntity()))continue;
            for(VehicleCollision.OrientedBox box:craft.collision.pose(craft.rotationYaw)) {
                AxisAlignedBB at=box.broad.offset(craft.posX,craft.posY,craft.posZ);
                if(at.intersects(event.getAabb()))event.getCollisionBoxesList().add(at);
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.world.isRemote)return;
        WorldServer world=(WorldServer)event.world;
        if(recovered.add(world))try{VehicleTransfer.recover(world);}catch(Exception e){VandorLabs.logger.error("Vehicle recovery blocked; journal retained",e);}
        for(EntityGroundVehicle craft:VehicleLookup.loaded(world))returnTaxi(world,craft);
        previews.entrySet().removeIf(e->e.getValue().world==world && e.getValue().expires<world.getTotalWorldTime());
        java.util.Iterator<Signal> queued=signals.iterator();
        while(queued.hasNext()) {
            Signal request=queued.next();World owner=request.craft==null?request.seat.getWorld():request.craft.world;
            if(owner==world){queued.remove();signal(world,request);}
            else if(owner==null || owner.getMinecraftServer()==null)queued.remove();
        }
        int workBudget=1024;
        Iterator<Job> iterator=jobs.values().iterator();
        while(iterator.hasNext()) {
            Job job=iterator.next();if(job.world!=world)continue;
            if(job.player.world!=job.world || !job.signal && !job.taxi && !tool(job.player)
                    || job.taxi && (!seatedAt(job.player,job.scan.startPosition()) || !((job.world.getTileEntity(job.scan.startPosition())) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)
                    || !((com.vandorlabs.tiles.TileEntityPilotSeat)job.world.getTileEntity(job.scan.startPosition())).isTaxiMode())
                    || job.player.connection.netManager.isChannelOpen()==false || ++job.ticks>100){iterator.remove();continue;}
            if(workBudget<=0)break;workBudget-=256;
            if(!job.scan.step(256))continue;iterator.remove();
            if(job.scan.error!=null){VehicleNetwork.error(job.player,job.scan.error);continue;}
            if(job.signal || job.taxi){try{assembleSignal(job);}catch(Exception e){VehicleNetwork.error(job.player,(job.taxi?"Taxi Mode: ":"Pilot Seat signal: ")+e.getMessage());}continue;}
            Preview preview=new Preview(world,job.scan.result,job.scan.origin,null);previews.put(job.player.getUniqueID(),preview);
            preview.airBoundary.addAll(job.scan.airBoundary);
            VehicleNetwork.send(job.player,preview.structure,-1,preview.token,preview.origin,true,false);
        }
    }
}
