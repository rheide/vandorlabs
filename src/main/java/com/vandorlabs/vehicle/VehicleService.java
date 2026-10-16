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
    private static final class Job {final EntityPlayerMP player;final World world;final VehicleScan scan;boolean signal,taxi,screen;int ticks,requestedMode=-1;Job(EntityPlayerMP p,BlockPos pos){player=p;world=p.world;scan=new VehicleScan(p.world,pos);}}
    /** Screen clicks use the clicking player, bounded scans and the normal transfer checks. */
    public static void screenMode(EntityPlayerMP player,BlockPos screen,EntityGroundVehicle craft,int mode){
        require(mode>=0&&mode<=2,"Invalid vehicle mode.");
        if(craft==null){
            if(mode==0||jobs.containsKey(player.getUniqueID()))return;
            require(jobs.size()<16,"Too many craft inspections; try again.");
            Job job=new Job(player,screen);job.signal=true;job.screen=true;job.requestedMode=mode;jobs.put(player.getUniqueID(),job);return;
        }
        require(validCaptured(player,craft)&&!craft.transferInProgress,"Craft unavailable during transfer.");
        VehicleStructure.Cell controller=craft.structure.controller();require(controller!=null,"Install a Vehicle Controller before changing mode.");
        permission(player,new BlockPos(craft.toWorld(new Vec3d(controller.pos).addVector(.5,.5,.5))));
        if(mode!=0){require(craft.setHover(mode==2),mode==2?"Hover requires three lift blocks and a Vehicle Thruster.":"Extend the landing gear fully and descend to within three blocks of solid ground before selecting Taxi.");VehicleNetwork.state(craft);return;}
        require(!craft.hover,"Select Taxi mode before parking as blocks.");
        require(craft.stopped(),"Stop before parking.");
        VehicleTransfer.submit(player.getServerWorld(),()->{
            require(validCaptured(player,craft),"Craft unavailable.");
            BlockPos at=new BlockPos(craft.toWorld(new Vec3d(screen).addVector(.5,.5,.5)));require(player.getDistanceSqToCenter(at)<=64,"Move closer to the screen.");permission(player,at);
            VehicleRotation.Plan plan=VehicleRotation.plan(craft);validateParking(player,craft,plan.origin,true);
            VehicleTransfer.park(player.getServerWorld(),craft,plan.origin,player);
        },error->VehicleNetwork.error(player,error.getMessage()));
    }
    public static void taxiAssembly(EntityPlayerMP player,BlockPos seat) {
        if(player.isSpectator() || !(player.world.getTileEntity(seat) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)
                || !seatedAt(player,seat))return;
        if(!((com.vandorlabs.tiles.TileEntityPilotSeat)player.world.getTileEntity(seat)).isTaxiMode() || jobs.containsKey(player.getUniqueID()) || jobs.size()>=16)return;
        previews.remove(player.getUniqueID());Job job=new Job(player,seat);job.taxi=true;jobs.put(player.getUniqueID(),job);
        player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Taxi Mode: inspecting connected craft..."),true);
    }
    private static boolean seatedAt(EntityPlayerMP player,BlockPos pos) {
        return player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat
                && pos.equals(((com.vandorlabs.entity.EntityChairSeat)player.getRidingEntity()).getChairPos());
    }
    public static void taxiParking(EntityPlayerMP player,EntityGroundVehicle craft) {
        if(craft.hover || craft.autopilot.enabled())return;
        craft.stopForParking();craft.taxiReturnPending=true;craft.taxiReturnActor=player.getUniqueID();craft.taxiNextAttempt=0;
    }
    private static void returnTaxi(WorldServer world,EntityGroundVehicle craft) {
        if(!craft.taxiReturnPending || craft.isDead || craft.ownershipBlocked || craft.structure==null || craft.transferInProgress
                || !craft.getPassengers().isEmpty() || world.getTotalWorldTime()<craft.taxiNextAttempt)return;
        boolean first=craft.taxiNextAttempt==0;craft.taxiNextAttempt=world.getTotalWorldTime()+20;
        EntityPlayerMP player=craft.taxiReturnActor==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(craft.taxiReturnActor);
        if(player==null || player.world!=world || player.isDead || player.isSpectator())return;
        if(!VehicleTaxiMode.enabled(craft.structure,craft.taxiReturnSeat)){craft.taxiReturnPending=false;return;}
        craft.taxiNextAttempt=world.getTotalWorldTime()+200;
        VehicleTransfer.submit(world,()->{
            require(craft.getPassengers().isEmpty()&&craft.taxiReturnPending,"Parking canceled by reboarding.");
            craft.stopForParking();VehicleRotation.Plan plan=VehicleRotation.plan(craft);BlockPos origin=plan.origin;
            validateParking(player,craft,origin,true,false);
            int turns=VehicleRotation.turns(craft.rotationYaw),width=(int)craft.structure.bounds.maxX,depth=(int)craft.structure.bounds.maxZ;
            BlockPos selected=craft.taxiReturnSeat==null?craft.structure.seat:craft.taxiReturnSeat;
            BlockPos seat=origin.add(VehicleRotation.cell(selected,width,depth,turns)).add(plan.structure.seat.subtract(VehicleRotation.cell(craft.structure.seat,width,depth,turns)));
            VehicleTransfer.park(world,craft,origin,player,false);
            if(distance(player,craft.getEntityBoundingBox())<=64){
                AxisAlignedBB seatArea=new AxisAlignedBB(seat).expand(0,1,0);Vec3d near=new Vec3d(seat).addVector(.5,0,.5);
                Vec3d exit=com.vandorlabs.entity.SafeDismount.findNear(world,player,seatArea,near);
                if(exit==null)exit=com.vandorlabs.entity.SafeDismount.find(world,player,seatArea);
                com.vandorlabs.entity.SafeDismount.move(player,exit,craft.getEntityBoundingBox());
            }
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Taxi Mode: craft restored as blocks."),true);
        },failure->{craft.taxiNextAttempt=world.getTotalWorldTime()+20;if(first)VehicleNetwork.error(player,"Taxi Mode could not park: "+failure.getMessage()+". Craft preserved; parking retries automatically. You can reboard or use the Configurizer.");});
    }
    @SubscribeEvent public static void taxiExit(net.minecraftforge.event.entity.EntityMountEvent event) {
        if(event.isMounting() || !(event.getEntityMounting() instanceof EntityPlayerMP) || !(event.getEntityBeingMounted() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle craft=(EntityGroundVehicle)event.getEntityBeingMounted();EntityPlayerMP player=(EntityPlayerMP)event.getEntityMounting();
        if(craft.hover && player.isSneaking() && !craft.flightDismount && !craft.transferInProgress){event.setCanceled(true);return;}
        if(craft.hover || craft.autopilot.enabled())return;
        if(craft.transferInProgress || craft.isDead || player.isDead || !player.connection.netManager.isChannelOpen() || !VehicleTaxiMode.enabled(craft.structure,craft.seatForPassenger(player)))return;
        try{VehicleRotation.turns(craft.rotationYaw);}catch(IllegalArgumentException failure){if(craft.world.getTotalWorldTime()>=craft.taxiNextWarning){craft.taxiNextWarning=craft.world.getTotalWorldTime()+20;VehicleNetwork.error(player,"Exited Taxi Mode; craft was not converted back: "+failure.getMessage()+" Reboard to align it, or use the Configurizer.");}}
    }
    private static final java.util.ArrayDeque<Signal> signals=new java.util.ArrayDeque<>();
    private static final class Signal {
        final com.vandorlabs.tiles.TileEntityPilotSeat seat;final EntityGroundVehicle craft;int mode=-1;
        Signal(com.vandorlabs.tiles.TileEntityPilotSeat s,EntityGroundVehicle e){seat=s;craft=e;}
    }
    public static void signal(com.vandorlabs.tiles.TileEntityPilotSeat seat,EntityGroundVehicle craft) {
        if(signals.size()<64)signals.add(new Signal(seat,craft));
    }
    public static void modeSignal(com.vandorlabs.tiles.TileEntityPilotSeat seat,EntityGroundVehicle craft,int mode){
        if(mode<0 || mode>2 || signals.size()>=64)return;
        Signal request=new Signal(seat,craft);request.mode=mode;signals.add(request);
    }
    private static void signal(WorldServer world,Signal signal) {
        com.vandorlabs.tiles.TileEntityPilotSeat seat=signal.seat;
        if(signal.craft!=null){
            if(signal.craft.structure==null || !VehicleControlSettings.position(signal.craft.structure).equals(seat.getPos()))return;
            net.minecraft.tileentity.TileEntity current=signal.craft.view.getTileEntity(seat.getPos());
            if(!(current instanceof com.vandorlabs.tiles.TileEntityPilotSeat))return;
            com.vandorlabs.tiles.TileEntityPilotSeat live=(com.vandorlabs.tiles.TileEntityPilotSeat)current;
            if(!java.util.Objects.equals(live.owner,seat.owner) || !live.getRedstoneChannels().equals(seat.getRedstoneChannels()) || live.getRedstoneMode()!=seat.getRedstoneMode())return;
            for(int mode=0;mode<3;mode++)if(!live.modeChannels(mode).equals(seat.modeChannels(mode)))return;
            seat=live;
        }
        if(signal.craft==null && !(seat instanceof com.vandorlabs.tiles.TileEntityVehicleController))return;
        if(signal.craft!=null && (signal.mode==1 || signal.mode==2)){
            EntityGroundVehicle craft=signal.craft;
            if(!craft.isDead && !craft.ownershipBlocked && !craft.setHover(signal.mode==2))
                VandorLabs.logger.debug("Requested vehicle mode unavailable: check lift blocks or ground clearance.");
            return;
        }
        if(seat.owner==null)return;
        EntityPlayerMP player=world.getMinecraftServer().getPlayerList().getPlayerByUUID(seat.owner);
        if(player==null || player.world!=world)return;
        try {
            if(signal.craft!=null) {
                EntityGroundVehicle craft=signal.craft;if(craft.isDead || craft.ownershipBlocked)return;
                if(signal.mode==0){if(craft.autopilot.controller()!=null)craft.autopilot.controller().setEnabled(false);craft.stopForParking();}
                VehicleTransfer.submit(world,()->{BlockPos origin=VehicleRotation.plan(craft).origin;validateParking(player,craft,origin,true,false);VehicleTransfer.park(world,craft,origin,player);},e->VehicleNetwork.error(player,e.getMessage()));
            } else {
                if(signal.mode==0)return;
                if(world.getTileEntity(seat.getPos())!=seat || jobs.size()>=16)return;
                for(Job existing:jobs.values())if(existing.world==world && existing.scan.startPosition().equals(seat.getPos()))return;
                Job job=new Job(player,seat.getPos());job.signal=true;job.requestedMode=signal.mode;jobs.put(UUID.randomUUID(),job);
            }
        }catch(Exception e){VandorLabs.logger.warn("Vehicle Controller signal failed: "+e.getMessage());VehicleNetwork.error(player,"Vehicle Controller signal: "+e.getMessage());}
    }
    private static void assembleSignal(Job job) throws Exception {
        VehicleStructure structure=job.scan.result;WorldServer world=(WorldServer)job.world;BlockPos origin=job.scan.origin;
        if(job.screen){require(job.player.getDistanceSqToCenter(job.scan.startPosition())<=64,"Move closer to the screen.");permission(job.player,job.scan.startPosition());}
        require(structure.controller()!=null,"Install a Vehicle Controller before assembling a craft.");
        require(!job.taxi || VehicleTaxiMode.enabled(structure,job.scan.startPosition().subtract(origin)),"Taxi Mode is disabled on the Pilot Seat.");
        require(job.requestedMode!=2 || VehicleFlight.canHover(structure),"Hover requires three Antigravity Plates, Repulsor Arrays or Vertical Hover Thrusters.");
        require(structure.matches(world,origin),"Blocks or settings changed during signal scan.");
        for(BlockPos boundary:job.scan.airBoundary)require(world.isBlockLoaded(boundary) && world.isAirBlock(boundary),"Craft connections changed during signal scan.");
        require(clearOfPeople(world,VehicleTransfer.geometry(world,structure),origin,assemblyAllowed(world,structure,origin,job.player)),"Another occupant obstructs assembly.");
        authorizeAssembly(job.player,structure,origin);
        EntityGroundVehicle assembled=VehicleTransfer.assemble(world,structure,origin,job.player);
        if(job.requestedMode==2 && !assembled.setHover(true))VehicleNetwork.error(job.player,"Craft assembled in Taxi: Hover requires three lift blocks.");
    }
    private static final class Preview {
        final UUID token=UUID.randomUUID();final WorldServer world;VehicleStructure structure;final BlockPos origin;final EntityGroundVehicle entity;final long expires;
        final int turns;final boolean controller;final BlockPos selected;final String status;
        net.minecraft.nbt.NBTTagCompound controlData;
        final Set<BlockPos> airBoundary=new HashSet<>();
        Preview(WorldServer world,VehicleStructure structure,BlockPos origin,EntityGroundVehicle entity,String status,boolean controller,BlockPos selected){
            this.world=world;this.structure=structure;this.origin=origin;this.entity=entity;this.status=status;this.controller=controller;this.selected=selected.toImmutable();
            turns=controller && entity!=null && status.isEmpty()?VehicleRotation.turns(entity.rotationYaw):-1;expires=world.getTotalWorldTime()+1200;
        }
        Preview changed(VehicleStructure structure){Preview next=new Preview(world,structure,origin,entity,status,controller,selected);next.airBoundary.addAll(airBoundary);next.controlData=controlData==null?null:controlData.copy();return next;}
    }
    private static boolean tool(EntityPlayerMP p){return !p.isSpectator() && p.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER;}
    private static void require(boolean test,String message){if(!test)throw new IllegalArgumentException(message);}
    /** Compatibility entry name: a Pilot Seat opens its own settings, without scanning a hull. */
    public static void previewAssembly(EntityPlayerMP player,BlockPos pos){
        if(player.isSpectator() || player.isDead || !player.world.isBlockLoaded(pos) || player.getDistanceSqToCenter(pos)>64)return;
        if(player.world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController){previewController(player,pos);return;}
        if(!(player.world.getBlockState(pos).getBlock() instanceof BlockPilotSeat))return;
        if(player.world.getBlockState(pos).getValue(BlockPilotSeat.UPPER))pos=pos.down();
        try{permission(player,pos);net.minecraft.tileentity.TileEntity tile=player.world.getTileEntity(pos);require(tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat,"Pilot Seat missing.");
            VehicleStructure snapshot=singleSeat(player.world.getBlockState(pos),tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),BlockPos.ORIGIN.subtract(pos));
            Preview preview=new Preview(player.getServerWorld(),snapshot,pos,null,"",false,BlockPos.ORIGIN);open(player,preview);
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    private static VehicleStructure singleSeat(net.minecraft.block.state.IBlockState state,net.minecraft.nbt.NBTTagCompound data,BlockPos translation){
        return new VehicleStructure(java.util.Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,VehicleTileData.translated(data,translation))),BlockPos.ORIGIN,state.getValue(BlockPilotSeat.FACING),1);
    }
    public static void previewController(EntityPlayerMP player,BlockPos pos){
        if(player.isDead || player.isSpectator() || !player.world.isBlockLoaded(pos) || player.getDistanceSqToCenter(pos)>64 || !(player.world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController))return;
        try{permission(player,pos);if(jobs.containsKey(player.getUniqueID()))return;require(jobs.size()<16,"Too many craft inspections; try again.");
            previews.remove(player.getUniqueID());jobs.put(player.getUniqueID(),new Job(player,pos));VehicleNetwork.controllerInspection(player,pos,"Inspecting connected craft...");
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    /** Legacy hull Configurizer routing resolves the unique Vehicle Controller. */
    public static void previewParking(EntityPlayerMP player,EntityGroundVehicle entity){
        if(!tool(player) || entity==null || entity.structure==null)return;VehicleStructure.Cell controller=entity.structure.controller();
        if(controller==null){if(validCaptured(player,entity))openCapturedController(player,entity,entity.structure.seat);return;}
        previewController(player,entity,controller.pos);
    }
    public static void previewSeat(EntityPlayerMP player,EntityGroundVehicle entity){
        VehicleInteraction.Target target=VehicleInteraction.pointed(player);if(target==null || target.craft!=entity || target.hit.action!=VehicleSelection.Action.BOARD)return;
        previewSeat(player,entity,target.hit.cell.state.getValue(BlockPilotSeat.UPPER)?target.hit.cell.pos.down():target.hit.cell.pos);
    }
    public static void previewSeat(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos selected){
        if(!validCaptured(player,entity) || !entity.structure.pilotSeats().contains(selected))return;
        if(!tool(player)){
            VehicleInteraction.Target target=VehicleInteraction.pointed(player);if(!player.isSneaking() || target==null || target.craft!=entity || target.hit.action!=VehicleSelection.Action.BOARD)return;
            BlockPos reached=target.hit.cell.state.getValue(BlockPilotSeat.UPPER)?target.hit.cell.pos.down():target.hit.cell.pos;if(!selected.equals(reached))return;
        }
        try{seatReach(player,entity,selected);Preview preview=new Preview(player.getServerWorld(),entity.structure,new BlockPos(entity),entity,"",false,selected);open(player,preview);}
        catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    public static void previewController(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos selected){
        if(!validCaptured(player,entity))return;VehicleStructure.Cell cell=entity.structure.at(selected);if(cell==null || !(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController))return;
        openCapturedController(player,entity,selected);
    }
    private static void openCapturedController(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos selected){
        try{Vec3d point=entity.toWorld(new Vec3d(selected).addVector(.5,.5,.5));require(player.getDistanceSq(point.x,point.y,point.z)<=64,"Move closer to the Vehicle Controller.");permission(player,new BlockPos(point));
            VehicleRotation.Plan plan=null;String status="";try{plan=VehicleRotation.plan(entity);validateParking(player,entity,plan.origin,false);}catch(Exception blocked){status=blocked.getMessage()==null?"Parking unavailable.":blocked.getMessage();}
            open(player,new Preview(player.getServerWorld(),entity.structure,status.isEmpty()?plan.origin:new BlockPos(entity),entity,status,true,selected));
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    private static boolean validCaptured(EntityPlayerMP player,EntityGroundVehicle entity){return !player.isDead && !player.isSpectator() && entity!=null && !entity.isDead && !entity.ownershipBlocked && entity.world==player.world && entity.structure!=null && distance(player,entity.getEntityBoundingBox())<=64;}
    private static void seatReach(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos selected){
        Vec3d point=entity.toWorld(new Vec3d(selected).addVector(.5,.5,.5));require(entity.isPassenger(player) && selected.equals(entity.seatForPassenger(player)) || player.getDistanceSq(point.x,point.y,point.z)<=64,"Move closer to the Pilot Seat.");permission(player,new BlockPos(point));
    }
    private static VehicleStructure display(Preview preview){
        if(!preview.controller){VehicleStructure.Cell seat=preview.structure.at(preview.selected);return singleSeat(seat.state,seat.tileData(),BlockPos.ORIGIN.subtract(preview.selected));}
        return preview.entity==null || !preview.status.isEmpty()?preview.structure:VehicleRotation.plan(preview.entity).structure;
    }
    private static void open(EntityPlayerMP player,Preview preview){jobs.remove(player.getUniqueID());previews.put(player.getUniqueID(),preview);if(preview.structure==null){VehicleNetwork.controllerInspection(player,preview.origin,preview.status,preview.token,preview.controlData);return;}VehicleNetwork.send(player,display(preview),preview.entity==null?-1:preview.entity.getEntityId(),preview.token,preview.origin,true,preview.entity!=null,preview.status,preview.controller);}
    private static void standaloneController(EntityPlayerMP player,BlockPos pos,String status){
        try{require(!player.isDead&&!player.isSpectator()&&player.world.isBlockLoaded(pos)&&player.getDistanceSqToCenter(pos)<=64,"Move closer to the Vehicle Controller.");permission(player,pos);
            net.minecraft.tileentity.TileEntity raw=player.world.getTileEntity(pos);require(raw instanceof com.vandorlabs.tiles.TileEntityVehicleController,"Vehicle Controller missing.");
            Preview preview=new Preview(player.getServerWorld(),null,pos,null,status==null?"Selection unavailable.":status,true,BlockPos.ORIGIN);
            preview.controlData=raw.writeToNBT(new net.minecraft.nbt.NBTTagCompound());open(player,preview);
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    /** Mode buttons use the same authenticated controller preview as settings. */
    public static void selectMode(EntityPlayerMP player,VehicleNetwork.Packet packet){
        try{
            require(packet.sequence>=0&&packet.sequence<=2,"Invalid vehicle mode.");
            Preview preview=controllerPreview(player,packet.token,packet.entity,packet.origin);
            editableController(player,preview);
            require(preview.structure!=null,"Complete the craft before changing mode.");
            require(preview.entity!=null||packet.sequence!=0,"Craft is already parked as blocks.");
            if(preview.entity==null || packet.sequence==0){confirm(player,packet.token,packet.sequence);return;}
            require(validCaptured(player,preview.entity),"Craft unavailable; inspect it again.");
            require(preview.entity.setHover(packet.sequence==2),packet.sequence==2?
                    "Hover requires three lift blocks and a Vehicle Thruster.":
                    "Extend the landing gear fully and descend to within three blocks of solid ground before selecting Taxi.");
            previews.remove(player.getUniqueID());
            VehicleNetwork.state(preview.entity);
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage()==null?"Vehicle mode change failed.":denied.getMessage());}
    }
    public static void confirm(EntityPlayerMP player,UUID token){confirm(player,token,-1);}
    private static void confirm(EntityPlayerMP player,UUID token,int requestedMode) {
        Preview preview=previews.get(player.getUniqueID());
        if(preview==null || !preview.token.equals(token))return;
        previews.remove(player.getUniqueID());
        try{
            require(!player.isDead&&!player.isSpectator()&&preview.world==player.world,"Pilot unavailable.");
            BlockPos at=preview.entity==null?preview.origin.add(preview.selected):new BlockPos(preview.entity.toWorld(new Vec3d(preview.selected).addVector(.5,.5,.5)));
            require(player.getDistanceSqToCenter(at)<=64,"Move closer to the Vehicle Controller.");permission(player,at);
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());return;}
        VehicleTransfer.submit(preview.world,preview.entity==null?preview.structure:null,preview.origin,()->{
            require(!player.isSpectator() && !player.isDead && preview.world==player.world && preview.expires>=player.world.getTotalWorldTime(),"Preview expired; inspect the craft again.");
            require(preview.controller && preview.structure!=null && preview.status.isEmpty(),"Use the Vehicle Controller to assemble or park this craft.");
            if(preview.entity!=null) {
                Vec3d controllerPoint=preview.entity.toWorld(new Vec3d(preview.selected).addVector(.5,.5,.5));
                require(player.getDistanceSq(controllerPoint.x,controllerPoint.y,controllerPoint.z)<=64,"Move closer to the Vehicle Controller.");permission(player,new BlockPos(controllerPoint));
                require(VehicleRotation.turns(preview.entity.rotationYaw)==preview.turns,"Craft rotated; inspect it again.");
                validateParking(player,preview.entity,preview.origin,true);
                require(preview.entity.structure==preview.structure,"Craft changed; inspect it again.");
                VehicleTransfer.park(preview.world,preview.entity,preview.origin,player);
            } else {
                require(preview.structure.controller()!=null && player.getDistanceSqToCenter(preview.origin.add(preview.selected))<=64,"Move closer to the Vehicle Controller.");
                require(preview.structure.matches(player.world,preview.origin),"Blocks or settings changed; inspect the craft again.");
                for(BlockPos p:preview.airBoundary)require(player.world.isBlockLoaded(p) && player.world.isAirBlock(p),"The craft's connections changed; inspect it again.");
                VehicleCollision geometry=VehicleTransfer.geometry(player.world,preview.structure);
                require(clearOfPeople(player.world,geometry,preview.origin,assemblyAllowed(player.world,preview.structure,preview.origin,player)),"Another occupant obstructs assembly.");
                authorizeAssembly(player,preview.structure,preview.origin);
                if(requestedMode==2){require(VehicleFlight.canHover(preview.structure),"Hover requires three lift blocks.");VehicleFlight.requireThruster(preview.structure);}
                EntityGroundVehicle assembled=VehicleTransfer.assemble(preview.world,preview.structure,preview.origin,player);
                if(requestedMode==2)require(assembled.setHover(true),"Unable to select Hover.");
            }
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(preview.entity==null?"Craft assembled. Right-click to drive; W/S drives, A/D steers, Space brakes, Sneak dismounts.":"Craft parked as blocks."),false);
        },e->{VandorLabs.logger.warn("Vehicle action failed",e);VehicleNetwork.error(player,e.getMessage()==null?"Vehicle transfer failed; the recovery record was retained.":e.getMessage());});
    }
    /** Protection events describe the real blocks removed, including generated ramp cells.
     * Forge harvest checks read the state back from the event world. */
    public static void authorizeAssembly(EntityPlayerMP player,VehicleStructure structure,BlockPos origin)throws Exception {
        for(VehicleStructure.Cell cell:structure.cells){
            BlockPos at=origin.add(cell.pos);permission(player,at);
            net.minecraft.tileentity.TileEntity tile=player.world.getTileEntity(at);
            if(tile instanceof com.vandorlabs.tiles.TileEntityRampController)
                for(BlockPos source:((com.vandorlabs.tiles.TileEntityRampController)tile).vehicleSources())permission(player,source);
        }
        VehicleEscalators.WorldSnapshot actual=VehicleEscalators.expandedWorldSnapshot(player.world,structure,origin);
        for(VehicleStructure.Cell cell:actual.structure.cells){
            BlockPos at=actual.origin.add(cell.pos);permission(player,at);
            require(!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.world,at,player.world.getBlockState(at),player)),"A protection rule prevented assembly.");
        }
    }
    private static Preview seatPreview(EntityPlayerMP player,UUID token,int entity,BlockPos selected){
        Preview preview=previews.get(player.getUniqueID());require(preview!=null && preview.token.equals(token) && !preview.controller && !player.isDead && !player.isSpectator() && preview.world==player.world && preview.expires>=player.world.getTotalWorldTime(),"Pilot Seat settings expired; inspect again.");
        require(entity==(preview.entity==null?-1:preview.entity.getEntityId()) && selected.equals(BlockPos.ORIGIN),"Pilot Seat identity changed.");return preview;
    }
    private static BlockPos displaySelected(Preview preview){return preview.structure==null?preview.origin:preview.controller?VehicleControlSettings.position(display(preview)):BlockPos.ORIGIN;}
    private static Preview controllerPreview(EntityPlayerMP player,UUID token,int entity,BlockPos selected){
        Preview preview=previews.get(player.getUniqueID());
        require(preview!=null && preview.token.equals(token) && preview.controller && !player.isDead && !player.isSpectator() && preview.world==player.world && preview.expires>=player.world.getTotalWorldTime(),"Vehicle Controller settings expired; inspect again.");
        require(entity==(preview.entity==null?-1:preview.entity.getEntityId()) && selected.equals(displaySelected(preview)),"Vehicle Controller identity changed.");return preview;
    }
    private static boolean controllerSettingsMatch(net.minecraft.nbt.NBTTagCompound actual,net.minecraft.nbt.NBTTagCompound expected){
        if(expected==null)return false;actual=actual.copy();expected=expected.copy();
        actual.removeTag("VehicleSignalHigh");expected.removeTag("VehicleSignalHigh");
        for(int i=0;i<3;i++){actual.getCompoundTag("VehicleModeChannels"+i).removeTag("High");expected.getCompoundTag("VehicleModeChannels"+i).removeTag("High");}
        return actual.equals(expected);
    }
    private static com.vandorlabs.tiles.TileEntityPilotSeat editableController(EntityPlayerMP player,Preview preview){
        if(preview.structure==null){
            BlockPos at=preview.origin;require(player.world.isBlockLoaded(at)&&player.getDistanceSqToCenter(at)<=64,"Move closer to the Vehicle Controller.");permission(player,at);
            net.minecraft.tileentity.TileEntity raw=player.world.getTileEntity(at);require(raw instanceof com.vandorlabs.tiles.TileEntityVehicleController,"Vehicle Controller missing.");
            require(controllerSettingsMatch(raw.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),preview.controlData),"Vehicle Controller settings changed; inspect again.");
            return (com.vandorlabs.tiles.TileEntityVehicleController)raw;
        }
        require(preview.selected.equals(VehicleControlSettings.position(preview.structure)),"Vehicle Controller selection changed.");
        net.minecraft.tileentity.TileEntity tile;
        if(preview.entity==null){BlockPos at=preview.origin.add(preview.selected);require(player.getDistanceSqToCenter(at)<=64 && VehicleSeatSettings.matchesPlacedAt(preview.structure,player.world,preview.origin,preview.selected),"Vehicle Controller changed; inspect again.");permission(player,at);tile=player.world.getTileEntity(at);require(tile instanceof com.vandorlabs.tiles.TileEntityVehicleController,"Vehicle Controller missing.");}
        else{require(validCaptured(player,preview.entity) && preview.entity.structure==preview.structure,"Craft changed; inspect again.");Vec3d at=preview.entity.toWorld(new Vec3d(preview.selected).addVector(.5,.5,.5));require(player.getDistanceSq(at.x,at.y,at.z)<=64,"Move closer to the Vehicle Controller.");permission(player,new BlockPos(at));tile=preview.entity.view.getTileEntity(preview.selected);}
        require(tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat,"Vehicle Controller data missing.");return (com.vandorlabs.tiles.TileEntityPilotSeat)tile;
    }
    private static com.vandorlabs.tiles.TileEntityPilotSeat editableSeat(EntityPlayerMP player,Preview preview){
        net.minecraft.tileentity.TileEntity tile;
        if(preview.entity==null){BlockPos at=preview.origin.add(preview.selected);require(player.getDistanceSqToCenter(at)<=64 && VehicleSeatSettings.matchesPlaced(preview.structure,player.world,preview.origin),"Pilot Seat changed; inspect again.");permission(player,at);tile=player.world.getTileEntity(at);}
        else{require(validCaptured(player,preview.entity) && preview.entity.structure==preview.structure,"Craft changed; inspect again.");seatReach(player,preview.entity,preview.selected);tile=preview.entity.view.getTileEntity(preview.selected);}
        require(tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat,"Pilot Seat data missing.");return (com.vandorlabs.tiles.TileEntityPilotSeat)tile;
    }
    private static void changedSeat(EntityPlayerMP player,Preview previous,com.vandorlabs.tiles.TileEntityPilotSeat seat){
        if(previous.structure==null){Preview next=previous.changed(null);next.controlData=seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound());previews.put(player.getUniqueID(),next);previewSettingsAck(player,previous,next);return;}
        VehicleStructure changed=VehicleSeatSettings.replaceAt(previous.structure,previous.selected,seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),previous.entity==null?BlockPos.ORIGIN.subtract(previous.origin):BlockPos.ORIGIN);
        if(previous.entity!=null){EntityGroundVehicle craft=previous.entity;craft.updateSettings(changed);
            try{java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()),bytes);
                VehicleNetwork.Packet update=new VehicleNetwork.Packet(VehicleNetwork.TILE);update.entity=craft.getEntityId();update.token=craft.getUniqueID();update.origin=previous.selected;update.sequence=net.minecraft.block.Block.getStateId(craft.view.getBlockState(previous.selected));update.data=bytes.toByteArray();
                com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(update,craft);for(Entity rider:craft.getPassengers())if(rider instanceof EntityPlayerMP)com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(update,(EntityPlayerMP)rider);
            }catch(java.io.IOException failure){throw new IllegalStateException("Cannot synchronize Pilot Seat",failure);}
            craft.signals.persist();changed=craft.structure;for(Entity passenger:craft.getPassengers())craft.updatePassenger(passenger);
        }
        Preview next=previous.changed(changed);previews.put(player.getUniqueID(),next);previewSettingsAck(player,previous,next);
    }
    public static void configureTaxi(EntityPlayerMP player,VehicleNetwork.Packet packet){
        try{Preview preview=seatPreview(player,packet.token,packet.entity,packet.origin);com.vandorlabs.tiles.TileEntityPilotSeat seat=editableSeat(player,preview);seat.setTaxiMode(packet.flag);changedSeat(player,preview,seat);}catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    public static void configureHeight(EntityPlayerMP player,VehicleNetwork.Packet packet){
        try{require(packet.strafe>=0 && packet.strafe<=3,"Invalid Pilot Seat height.");Preview preview=seatPreview(player,packet.token,packet.entity,packet.origin);com.vandorlabs.tiles.TileEntityPilotSeat seat=editableSeat(player,preview);seat.setHeight(packet.strafe);changedSeat(player,preview,seat);}catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    public static void configureChannels(EntityPlayerMP player,VehicleNetwork.Packet packet){
        Preview preview=previews.get(player.getUniqueID());if(preview==null || !preview.token.equals(packet.token))return;
        try{preview=controllerPreview(player,packet.token,packet.entity,packet.origin);com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.parse(packet.text);
            require(channels!=null && packet.forward>=0 && packet.forward<=2,"Invalid Vehicle Controller settings.");
            com.vandorlabs.tiles.TileEntityPilotSeat seat=editableController(player,preview);seat.setVehicleChannels(channels,player.world);seat.configure(packet.forward,seat.getHeight(),player.world);changedSeat(player,preview,seat);
        }catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    private static void previewSettingsAck(EntityPlayerMP player,Preview previous,Preview next){
        try{net.minecraft.nbt.NBTTagCompound data=new net.minecraft.nbt.NBTTagCompound();data.setUniqueId("PreviewToken",next.token);data.setTag("Seat",next.structure==null?next.controlData.copy():display(next).at(displaySelected(next)).tileData());
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(data,bytes);
            VehicleNetwork.Packet ack=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);ack.sequence=2;ack.entity=next.entity==null?-1:next.entity.getEntityId();ack.token=previous.token;ack.origin=displaySelected(next);ack.data=bytes.toByteArray();com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(ack,player);
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot acknowledge Pilot Seat settings",failure);}
    }
    public static void configureModeChannels(EntityPlayerMP player,UUID token,int entity,BlockPos selected,com.vandorlabs.redstone.ChannelList[] values){
        Preview preview=previews.get(player.getUniqueID());if(preview==null || !preview.token.equals(token))return;
        try{preview=controllerPreview(player,token,entity,selected);require(values!=null && values.length==3 && java.util.Arrays.stream(values).allMatch(java.util.Objects::nonNull),"Invalid mode channels.");com.vandorlabs.tiles.TileEntityPilotSeat seat=editableController(player,preview);for(int i=0;i<3;i++)seat.setModeChannels(i,values[i]);changedSeat(player,preview,seat);}
        catch(Exception denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    public static void permission(EntityPlayerMP player,BlockPos p) {
        require(player.world.isBlockLoaded(p) && player.world.getWorldBorder().contains(p) && p.getY()>=0 && p.getY()<player.world.getHeight(),"A target cell is outside the loaded world.");
        require(player.world.isBlockModifiable(player,p) && player.canPlayerEdit(p,EnumFacing.UP,player.getHeldItemMainhand()),"You cannot modify a target block.");
    }
    private static void validateParking(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos origin,boolean events) {validateParking(player,entity,origin,events,true);}
    static void validateParking(EntityPlayerMP player,EntityGroundVehicle entity,BlockPos origin,boolean events,boolean reach) {
        require(!entity.isDead && entity.world==player.world && (!reach || distance(player,entity.getEntityBoundingBox())<=64),"Move closer to the craft.");
        require(!entity.hover,"Select Taxi mode before parking as blocks.");
        require(entity.stopped(),"Stop before parking.");
        VehicleRotation.Plan plan=VehicleRotation.plan(entity);
        require(plan.origin.equals(origin),"Craft moved; open a new parking preview.");
        VehicleWorld parkedView=new VehicleWorld(player.world,plan.structure);VehicleCollision parkedGeometry=VehicleTransfer.geometry(player.world,plan.structure);
        for(VehicleStructure.Cell cell:plan.structure.cells) {
            BlockPos p=origin.add(cell.pos);permission(player,p);require(player.world.isAirBlock(p),"Parking is blocked at "+p);
        }
        for(net.minecraft.tileentity.TileEntity raw:parkedView.tiles.values())if(raw instanceof com.vandorlabs.tiles.TileEntityRampController)
            for(BlockPos source:((com.vandorlabs.tiles.TileEntityRampController)raw).vehicleSources())if(plan.structure.at(source)==null){permission(player,origin.add(source));require(player.world.isAirBlock(origin.add(source)),"Parking blocks a ramp recovery source at "+origin.add(source));}
        java.util.List<Entity> passengers=new ArrayList<>(entity.getPassengers());passengers.add(player);
        require(clearOfPeople(player.world,parkedGeometry,origin,passengers.toArray(new Entity[0])),"The parking area is occupied.");
        for(AxisAlignedBB box:parkedGeometry.boxes)
            require(player.world.getCollisionBoxes(entity,box.offset(origin).shrink(1e-6)).isEmpty(),"Snapped craft geometry overlaps an obstacle.");
    }
    static Entity[] assemblyAllowed(World world,VehicleStructure structure,BlockPos origin,Entity actor){
        java.util.List<Entity> allowed=new ArrayList<>();allowed.add(actor);
        for(com.vandorlabs.entity.EntityChairSeat chair:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,structure.bounds.offset(origin).grow(1)))
            if(structure.pilotSeats().contains(chair.getChairPos().subtract(origin)))allowed.addAll(chair.getPassengers());
        return allowed.toArray(new Entity[0]);
    }
    static boolean clearOfPeople(World world,VehicleCollision geometry,BlockPos origin,Entity... allowed) {
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
        VehicleLookup.unload(event.getWorld());VehicleAutopilot.unload(event.getWorld());
        if(event.getWorld() instanceof WorldServer)VehicleTransfer.unload((WorldServer)event.getWorld());
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
        VehicleTransfer.tick(world);
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
            if(job.player.world!=job.world || job.player.isDead || job.player.isSpectator()
                    || job.taxi && (!seatedAt(job.player,job.scan.startPosition()) || !((job.world.getTileEntity(job.scan.startPosition())) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)
                    )
                    || job.player.connection.netManager.isChannelOpen()==false || ++job.ticks>100){iterator.remove();continue;}
            if(workBudget<=0)break;workBudget-=256;
            if(!job.scan.step(256))continue;iterator.remove();
            if(VehicleDiagnostics.ENABLED)System.out.println("[vandorlabs] vehicle-scan ticks="+job.ticks+" cells="+(job.scan.result==null?0:job.scan.result.cells.size())+" accepted="+(job.scan.error==null));
            if(job.scan.error!=null){if(!job.signal && !job.taxi)standaloneController(job.player,job.scan.startPosition(),job.scan.error);else VehicleNetwork.error(job.player,job.scan.error);continue;}
            if(job.signal || job.taxi){VehicleTransfer.submit(world,job.scan.result,job.scan.origin,()->{require(job.player.world==world&&!job.player.isDead&&!job.player.isSpectator()&&job.player.connection.netManager.isChannelOpen(),"Pilot unavailable.");require(!job.taxi||seatedAt(job.player,job.scan.startPosition()),"Pilot left the seat.");assembleSignal(job);},e->VehicleNetwork.error(job.player,(job.taxi?"Taxi Mode: ":"Vehicle Controller signal: ")+e.getMessage()));continue;}
            Preview preview=new Preview(world,job.scan.result,job.scan.origin,null,"",true,job.scan.startPosition().subtract(job.scan.origin));previews.put(job.player.getUniqueID(),preview);
            preview.airBoundary.addAll(job.scan.airBoundary);
            VehicleNetwork.send(job.player,preview.structure,-1,preview.token,preview.origin,true,false,"",true);
        }
    }
}
