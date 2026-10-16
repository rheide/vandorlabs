package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Bounded route sensing keeps the sensor on adjacent cells while looking ahead for steering. */
public final class VehicleAutopilot {
    private final EntityGroundVehicle craft;
    private TileEntityAutopilot controller;
    private int revision=-1;
    private BlockPos previous,target,waiting,departed;
    private long arrived;
    private EnumFacing direction;
    private boolean departurePulsed;
    private boolean journey,parkingQueued;
    private static final java.util.Set<TileEntityAutopilot> queuedDepartures=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<TileEntityAutopilot,Boolean>());
    private static final double LOOKAHEAD=2.5;
    private static final int LOOKAHEAD_CELLS=6;
    private final BlockPos[] lookaheadPath=new BlockPos[LOOKAHEAD_CELLS+1];
    private static final java.util.Map<net.minecraft.world.World,ScanBudget> scanBudgets=new java.util.WeakHashMap<>();
    private static final class ScanBudget {long tick=Long.MIN_VALUE;int remaining;}
    private static boolean scanStep(net.minecraft.world.WorldServer world,VehicleScan scan){
        ScanBudget budget=scanBudgets.computeIfAbsent(world,w->new ScanBudget());long tick=world.getTotalWorldTime();if(budget.tick!=tick){budget.tick=tick;budget.remaining=1024;}
        if(budget.remaining<256)return false;budget.remaining-=256;return scan.step(256);
    }
    private static final java.util.Map<TileEntityAutopilot,VehicleScan> departures=new java.util.WeakHashMap<>();
    public VehicleAutopilot(EntityGroundVehicle craft){this.craft=craft;}
    public net.minecraft.nbt.NBTTagCompound write(){
        net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();n.setInteger("Schema",1);n.setBoolean("Journey",journey);n.setLong("Arrived",arrived);n.setBoolean("DeparturePulsed",departurePulsed);
        if(previous!=null)n.setLong("Previous",previous.toLong());if(target!=null)n.setLong("Target",target.toLong());if(waiting!=null)n.setLong("Waiting",waiting.toLong());if(departed!=null)n.setLong("Departed",departed.toLong());if(direction!=null)n.setInteger("Direction",direction.getHorizontalIndex());return n;
    }
    public void read(net.minecraft.nbt.NBTTagCompound n){
        reset();if(n.getInteger("Schema")!=1||!enabled())return;
        previous=n.hasKey("Previous",4)?BlockPos.fromLong(n.getLong("Previous")):null;target=n.hasKey("Target",4)?BlockPos.fromLong(n.getLong("Target")):null;waiting=n.hasKey("Waiting",4)?BlockPos.fromLong(n.getLong("Waiting")):null;departed=n.hasKey("Departed",4)?BlockPos.fromLong(n.getLong("Departed")):null;
        direction=n.hasKey("Direction",3)?EnumFacing.getHorizontal(n.getInteger("Direction")&3):null;arrived=n.getLong("Arrived");departurePulsed=n.getBoolean("DeparturePulsed");journey=n.getBoolean("Journey");
        if(target==null||direction==null||waiting!=null&&!waiting.equals(target)||previous!=null&&Math.abs(previous.getX()-target.getX())+Math.abs(previous.getZ()-target.getZ())!=1)reset();
    }
    public TileEntityAutopilot controller(){
        if(craft.view==null)return null;
        if(revision!=craft.view.tileRevision){revision=craft.view.tileRevision;controller=null;for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityAutopilot && !(tile instanceof TileEntityWaypoint)){controller=(TileEntityAutopilot)tile;break;}}
        return controller;
    }
    public boolean enabled(){TileEntityAutopilot tile=controller();return tile!=null&&tile.enabled();}
    private void reset(){target=previous=waiting=departed=null;direction=null;departurePulsed=false;journey=false;}
    public void toggle(){TileEntityAutopilot tile=controller();if(tile!=null){tile.setEnabled(!tile.enabled());reset();}}
    public void pulseDeparture(){
        TileEntityAutopilot tile=controller();if(tile==null)return;
        if(!tile.enabled()||!journey){reset();journey=true;}
        tile.setEnabled(true);
        BlockPos under=ground(craft.toWorld(new Vec3d(tile.getPos()).addVector(.5,0,.5)));
        if(under!=null&&craft.world.getTileEntity(under) instanceof TileEntityWaypoint){departed=under;waiting=null;}
        tile.setStatus("Following route");
    }
    private boolean route(BlockPos p){
        if(!craft.world.isBlockLoaded(p))return false;
        IBlockState state=craft.world.getBlockState(p);if(craft.world.getTileEntity(p) instanceof TileEntityWaypoint)return true;
        return state.getBlock()==Block.getBlockFromName(controller.marker) && state.getBlock().getMetaFromState(state)==controller.markerMeta;
    }
    private BlockPos ground(Vec3d sensor){
        int top=MathHelper.floor(sensor.y-.001);
        for(int y=top;y>=top-16;y--){BlockPos p=new BlockPos(sensor.x,y,sensor.z);if(!craft.world.isBlockLoaded(p))return null;if(!craft.world.isAirBlock(p)&&!craft.world.getBlockState(p).getMaterial().isReplaceable())return p;}
        return null;
    }
    private void stop(String reason){controller.setEnabled(false);controller.setStatus(reason);reset();craft.stopForParking();}
    public boolean tick(){
        if(craft.world.isRemote)return false;
        if(!enabled()){reset();return false;}
        if(!journey){reset();journey=true;}
        craft.autopilotControls((byte)0,(byte)0,true);craft.driveSpeed=0;
        Vec3d local=new Vec3d(controller.getPos()).addVector(.5,0,.5),sensor=craft.toWorld(local);
        BlockPos under=ground(sensor);
        if(under==null){stop("Ground out of range");return true;}
        if(!route(under)){stop("Route lost");return true;}
        if(departed!=null&&!under.equals(departed))departed=null;
        if(controller.departureSeat!=null){departed=under;controller.departureSeat=null;controller.departureAt=0;controller.changed();}
        if(direction==null||target==null){EnumFacing front=craft.structure.facing;double a=Math.toRadians(craft.rotationYaw);direction=EnumFacing.getFacingFromVector((float)(front.getFrontOffsetX()*Math.cos(a)-front.getFrontOffsetZ()*Math.sin(a)),0,(float)(front.getFrontOffsetX()*Math.sin(a)+front.getFrontOffsetZ()*Math.cos(a)));}
        if(target==null)target=under;
        double dx=target.getX()+.5-sensor.x,dz=target.getZ()+.5-sensor.z,distance=Math.hypot(dx,dz);
        if(distance<.025){
            TileEntity waypoint=craft.world.getTileEntity(target);
            if(waypoint instanceof TileEntityWaypoint && !target.equals(departed) && !controller.powered()){
                TileEntityWaypoint tile=(TileEntityWaypoint)waypoint;
                if(!target.equals(waiting)){waiting=target;departurePulsed=false;arrived=craft.world.getTotalWorldTime();craft.stopForParking();applyMode(tile.stopMode,tile.hoverHeight);controller.setStatus("Parked at waypoint");}
                if(tile.stopMode==2&&!craft.hover&&!applyMode(2,tile.hoverHeight)){controller.setStatus("Hover unavailable");return true;}
                if(tile.stopMode==1 && craft.hover){craft.hoverTarget=craft.posY-craft.groundClearance();craft.setHover(false);if(craft.hover)return true;}
                if(tile.stopMode==0){if(craft.hover){craft.hoverTarget=craft.posY-craft.groundClearance();craft.setHover(false);}if(!craft.hover && park(tile))return true;}
                if(!tile.autoDepart || craft.world.getTotalWorldTime()-arrived<tile.delay)return true;
                if(!departurePulsed){departurePulsed=true;tile.emit();}
                if(!tile.directSignal)return true;
                if(tile.departMode==1&&craft.hover)craft.hoverTarget=craft.posY-craft.groundClearance();
                if(!applyMode(tile.departMode,tile.hoverHeight)){controller.setStatus("Departure mode unavailable");return true;}departed=target;waiting=null;
            }else if(target.equals(waiting)){waiting=null;departed=target;}
            BlockPos next=null;int options=0,best=-1;
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos p=target.offset(face);if(p.equals(previous)||!route(p))continue;
                if(previous==null){int score=face.getFrontOffsetX()*direction.getFrontOffsetX()+face.getFrontOffsetZ()*direction.getFrontOffsetZ();if(score<0||score<best)continue;if(score>best){best=score;options=0;}}
                next=p;options++;
            }
            if(options!=1){stop(options==0?(previous==null?"No route ahead":"End of route"):"Ambiguous route");return true;}
            previous=target;target=next;direction=EnumFacing.getFacingFromVector(target.getX()-previous.getX(),0,target.getZ()-previous.getZ());
            dx=target.getX()+.5-sensor.x;dz=target.getZ()+.5-sensor.z;distance=Math.hypot(dx,dz);controller.setStatus("Following route");
        }
        // The captured Pilot Seat defines forward, exactly as manual driving does.
        // Anticipate unambiguous bends, but move the sensor along the actual route
        // segments rather than taking a diagonal shortcut across unmarked ground.
        Vec3d aim=lookahead(sensor);EnumFacing front=craft.structure.facing;
        double headingX=aim.x-sensor.x,headingZ=aim.z-sensor.z;
        if(Math.hypot(headingX,headingZ)<1e-6){headingX=direction.getFrontOffsetX();headingZ=direction.getFrontOffsetZ();}
        float desired=(float)Math.toDegrees(Math.atan2(headingZ,headingX)-Math.atan2(front.getFrontOffsetZ(),front.getFrontOffsetX()));
        craft.collision.begin(craft.world,craft);
        try{
            float delta=MathHelper.clamp(MathHelper.wrapDegrees(desired-craft.rotationYaw),-3,3);
            float yaw=craft.collision.turn(craft.world,craft,craft.posX,craft.posY,craft.posZ,craft.rotationYaw,delta);
            VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,yaw);Vec3d after=pose.world(local,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
            boolean turning=Math.abs(MathHelper.wrapDegrees(desired-craft.rotationYaw))>.1;
            double speed=.12*Math.max(.25,Math.cos(Math.toRadians(MathHelper.wrapDegrees(desired-yaw))));
            double fraction=distance<1e-9?0:Math.min(speed,distance)/distance;
            double mx=sensor.x-after.x+dx*fraction,mz=sensor.z-after.z+dz*fraction;
            if(!craft.collision.loaded(craft.world,craft.posX,craft.posY,craft.posZ,mx,0,mz,yaw)){controller.setStatus("Waiting for terrain");return true;}
            double x=craft.collision.clip(craft.world,craft,craft.posX,craft.posY,craft.posZ,mx,0,yaw);
            double z=craft.collision.clip(craft.world,craft,craft.posX+x,craft.posY,craft.posZ,mz,2,yaw);
            if(Math.abs(x-mx)>.00001||Math.abs(z-mz)>.00001||Math.abs(yaw-craft.rotationYaw)<.00001&&turning){controller.setStatus("Blocked");return true;}
            craft.rotationYaw=yaw;craft.setPosition(craft.posX+x,craft.posY,craft.posZ+z);craft.motionX=x;craft.motionZ=z;craft.velocityChanged=true;
        }finally{craft.collision.end();}
        return true;
    }
    private Vec3d lookahead(Vec3d sensor){
        BlockPos at=target,last=previous;lookaheadPath[0]=at;int count=1,changes=0;EnumFacing first=null,second=null,previousDirection=null;boolean staircase=true;
        for(int step=0;step<LOOKAHEAD_CELLS;step++){
            if(!craft.world.isBlockLoaded(at)||craft.world.getTileEntity(at) instanceof TileEntityWaypoint)break;
            BlockPos next=null;int options=0;
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos candidate=at.offset(face);if(candidate.equals(last)||!route(candidate))continue;if(last==null&&face!=direction)continue;next=candidate;options++;}
            if(options!=1)break;boolean visited=false;for(int i=0;i<count;i++)if(lookaheadPath[i].equals(next)){visited=true;break;}if(visited)break;
            EnumFacing forward=EnumFacing.getFacingFromVector(next.getX()-at.getX(),0,next.getZ()-at.getZ());
            if(first==null)first=forward;else if(forward!=first&&second==null)second=forward;
            if(forward!=first&&forward!=second||second!=null&&first.getAxis()==second.getAxis())staircase=false;
            if(previousDirection!=null&&previousDirection!=forward)changes++;previousDirection=forward;
            lookaheadPath[count++]=next;last=at;at=next;
        }
        // A repeating orthogonal staircase gets a full diagonal period of lookahead.
        // A single 90-degree corner keeps the original steering distance.
        double remaining=staircase&&changes>=2?4:LOOKAHEAD;
        Vec3d point=new Vec3d(target).addVector(.5,0,.5);double distance=Math.hypot(point.x-sensor.x,point.z-sensor.z);
        if(distance>=remaining)return sensor.addVector((point.x-sensor.x)*remaining/distance,0,(point.z-sensor.z)*remaining/distance);
        remaining-=distance;
        for(int i=1;i<count&&remaining>0;i++){
            BlockPos from=lookaheadPath[i-1],to=lookaheadPath[i];double fraction=Math.min(1,remaining);
            point=point.addVector((to.getX()-from.getX())*fraction,0,(to.getZ()-from.getZ())*fraction);remaining-=fraction;
        }
        return point;
    }
    private boolean applyMode(int mode,int height){
        if(mode==2){if(!craft.setHover(true))return false;craft.hoverTarget=target==null?craft.posY+height-craft.groundClearance():target.getY()+1+height-controller.getPos().getY();}
        else if(mode==1)return craft.setHover(false);
        return true;
    }
    private boolean park(TileEntityWaypoint waypoint){
        if(parkingQueued)return true;
        TileEntity raw=craft.view.getTileEntity(craft.structure.seat);if(!(raw instanceof TileEntityPilotSeat))return false;
        java.util.UUID owner=((TileEntityPilotSeat)raw).owner;
        net.minecraft.entity.player.EntityPlayerMP player=owner==null?null:craft.world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
        if(player==null||player.world!=craft.world){controller.setStatus("Waiting for owner");return false;}
        parkingQueued=true;
        VehicleTransfer.submit((net.minecraft.world.WorldServer)craft.world,()->{
            if(craft.isDead||player.world!=craft.world)throw new IllegalArgumentException("Parking owner or craft unavailable");
            craft.stopForParking();craft.signals.persist();VehicleRotation.Plan plan=VehicleRotation.plan(craft);
            VehicleService.validateParking(player,craft,plan.origin,true,false);
            BlockPos placedController=plan.origin.add(VehicleRotation.cell(controller.getPos(),(int)craft.structure.bounds.maxX,(int)craft.structure.bounds.maxZ,VehicleRotation.turns(craft.rotationYaw)));
            VehicleTransfer.park((net.minecraft.world.WorldServer)craft.world,craft,plan.origin,player);
            TileEntityAutopilot placed=(TileEntityAutopilot)craft.world.getTileEntity(placedController);
            placed.departureSeat=plan.origin.add(plan.structure.seat);placed.departureOwner=owner;placed.departureMode=waypoint.departMode;placed.departureHeight=waypoint.hoverHeight;
            placed.departureDirect=waypoint.directSignal;placed.departureWaypoint=waypoint.getPos();
            placed.departureAt=waypoint.autoDepart?craft.world.getTotalWorldTime()+Math.max(1,waypoint.delay):Long.MAX_VALUE;
            placed.changed();if(placed.departureAt!=Long.MAX_VALUE)craft.world.scheduleUpdate(placedController,placed.getBlockType(),Math.max(1,waypoint.delay));
        },error->{parkingQueued=false;controller.setStatus("Parking blocked");});return true;
    }
    static void unload(net.minecraft.world.World world){departures.keySet().removeIf(tile->tile.getWorld()==world);queuedDepartures.removeIf(tile->tile.getWorld()==world);scanBudgets.remove(world);}
    /** Start an unoccupied placed craft through the same bounded scan and durable transfer queue. */
    private static void start(TileEntityAutopilot tile){
        net.minecraft.world.WorldServer world=(net.minecraft.world.WorldServer)tile.getWorld();
        try{
            VehicleScan scan=departures.get(tile);
            if(scan==null){if(departures.size()>=16){tile.setStatus("Inspection busy");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),20);return;}tile.setStatus("Inspecting craft");scan=new VehicleScan(world,tile.getPos());departures.put(tile,scan);}
            if(!scanStep(world,scan)){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),1);return;}
            departures.remove(tile);if(scan.error!=null)throw new IllegalArgumentException(scan.error);
            VehicleStructure.Cell control=scan.result.controller();if(control==null)throw new IllegalArgumentException("Install a Vehicle Controller");
            net.minecraft.nbt.NBTTagCompound settings=control.tileData();
            if(settings==null||!settings.hasUniqueId("VehicleOwner"))settings=scan.result.at(scan.result.seat).tileData();
            java.util.UUID ownerId=settings!=null&&settings.hasUniqueId("VehicleOwner")?settings.getUniqueId("VehicleOwner"):null;
            net.minecraft.entity.player.EntityPlayerMP owner=ownerId==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerId);
            if(owner==null||owner.world!=world){tile.setStatus("Waiting for owner");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);return;}
            VehicleFlight.requireThruster(scan.result);requireRouteBelow(tile,scan.result,scan.origin);
            final VehicleScan ready=scan;queuedDepartures.add(tile);
            VehicleTransfer.submit(world,ready.result,ready.origin,()->{
                if(world.getTileEntity(tile.getPos())!=tile||!tile.enabled()||owner.world!=world||owner.isDead||owner.isSpectator())throw new IllegalArgumentException("Start cancelled or owner unavailable");
                if(!ready.result.matches(world,ready.origin))throw new IllegalArgumentException("Craft changed during start");
                for(BlockPos pos:ready.airBoundary)if(!world.isBlockLoaded(pos)||!world.isAirBlock(pos))throw new IllegalArgumentException("Craft connections changed");
                requireRouteBelow(tile,ready.result,ready.origin);
                if(!VehicleService.clearOfPeople(world,VehicleTransfer.geometry(world,ready.result),ready.origin,VehicleService.assemblyAllowed(world,ready.result,ready.origin,owner)))throw new IllegalArgumentException("Departure area is occupied");
                VehicleService.authorizeAssembly(owner,ready.result,ready.origin);
                EntityGroundVehicle assembled=VehicleTransfer.assemble(world,ready.result,ready.origin,owner);assembled.autopilot.pulseDeparture();queuedDepartures.remove(tile);
            },error->{queuedDepartures.remove(tile);startBlocked(tile,error);});
        }catch(Exception error){departures.remove(tile);startBlocked(tile,error);}
    }
    private static void startBlocked(TileEntityAutopilot tile,Exception error){
        if(!tile.enabled())return;tile.departureAt=0;tile.setStatus("Start blocked: "+(error.getMessage()==null?"inspection failed":error.getMessage()));
    }
    private static void requireRouteBelow(TileEntityAutopilot tile,VehicleStructure source,BlockPos origin){
        net.minecraft.world.World world=tile.getWorld();BlockPos top=tile.getPos().down();
        for(int depth=0;depth<=16;depth++){
            BlockPos pos=top.down(depth);if(!world.isBlockLoaded(pos))throw new IllegalArgumentException("Route terrain is not loaded");
            if(source.at(pos.subtract(origin))!=null)continue;
            IBlockState state=world.getBlockState(pos);if(world.isAirBlock(pos)||state.getMaterial().isReplaceable())continue;
            if(world.getTileEntity(pos) instanceof TileEntityWaypoint||state.getBlock()==Block.getBlockFromName(tile.marker)&&state.getBlock().getMetaFromState(state)==tile.markerMeta)return;
            throw new IllegalArgumentException("No route marker below Autopilot");
        }
        throw new IllegalArgumentException("Route ground out of range");
    }
    /** Only occupied waypoint departures schedule work; unoccupied markers never tick. */
    public static void depart(TileEntityAutopilot tile){
        if(tile.getWorld()==null||tile.getWorld().isRemote)return;
        if(!tile.enabled()){departures.remove(tile);return;}
        if(queuedDepartures.contains(tile)||tile.departureAt<=0)return;
        net.minecraft.world.WorldServer world=(net.minecraft.world.WorldServer)tile.getWorld();
        if(world.getTotalWorldTime()<tile.departureAt){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),(int)Math.min(1200,tile.departureAt-world.getTotalWorldTime()));return;}
        if(tile.departureSeat==null){start(tile);return;}
        if(!tile.departureDirect){tile.departureAt=Long.MAX_VALUE;tile.changed();TileEntity marker=tile.departureWaypoint==null?null:world.getTileEntity(tile.departureWaypoint);if(marker instanceof TileEntityWaypoint)((TileEntityWaypoint)marker).emit();return;}
        net.minecraft.entity.player.EntityPlayerMP owner=tile.departureOwner==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(tile.departureOwner);
        if(owner==null||owner.world!=world){tile.setStatus("Waiting for owner");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);return;}
        try{
            VehicleScan scan=departures.get(tile);if(scan==null){if(departures.size()>=16){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),20);return;}scan=new VehicleScan(world,tile.departureSeat);departures.put(tile,scan);}
            if(!scanStep(world,scan)){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),1);return;}departures.remove(tile);
            if(scan.error!=null)throw new IllegalArgumentException(scan.error);
            VehicleStructure.Cell controllerCell=scan.result.at(tile.getPos().subtract(scan.origin));
            if(controllerCell==null||controllerCell.state.getBlock()!=tile.getBlockType())throw new IllegalArgumentException("Waypoint controller moved or was copied");
            VehicleFlight.requireThruster(scan.result);if(tile.departureMode==2&&!VehicleFlight.canHover(scan.result))throw new IllegalArgumentException("Hover requires three lift blocks");
            final VehicleScan ready=scan;queuedDepartures.add(tile);
            VehicleTransfer.submit(world,ready.result,ready.origin,()->{
            if(world.getTileEntity(tile.getPos())!=tile||owner.world!=world)throw new IllegalArgumentException("Departure owner or controller unavailable");
            if(!ready.result.matches(world,ready.origin))throw new IllegalArgumentException("Craft changed during departure");
            for(BlockPos p:ready.airBoundary)if(!world.isBlockLoaded(p)||!world.isAirBlock(p))throw new IllegalArgumentException("Craft connections changed");
            if(!VehicleService.clearOfPeople(world,VehicleTransfer.geometry(world,ready.result),ready.origin,owner))throw new IllegalArgumentException("Departure area is occupied");
            VehicleService.authorizeAssembly(owner,ready.result,ready.origin);
            EntityGroundVehicle craft=VehicleTransfer.assemble(world,ready.result,ready.origin,owner);
            TileEntity marker=tile.departureWaypoint==null?null:world.getTileEntity(tile.departureWaypoint);if(marker instanceof TileEntityWaypoint)((TileEntityWaypoint)marker).emit();
            if(tile.departureMode==2&&craft.setHover(true))craft.hoverTarget=tile.departureWaypoint==null?craft.posY+tile.departureHeight-craft.groundClearance():tile.departureWaypoint.getY()+1+tile.departureHeight-craft.autopilot.controller().getPos().getY();
            queuedDepartures.remove(tile);
            },error->{queuedDepartures.remove(tile);tile.setStatus("Departure blocked");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);});
        }catch(Exception error){tile.setStatus("Departure blocked");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);}
    }
}
