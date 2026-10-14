package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Route work is bounded to one downward ray and the three next route cells. */
public final class VehicleAutopilot {
    private final EntityGroundVehicle craft;
    private TileEntityAutopilot controller;
    private int revision=-1;
    private BlockPos previous,target,waiting,departed;
    private long arrived;
    private EnumFacing direction;
    private boolean departurePulsed;
    private boolean journey;
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
        if(direction==null||target==null){EnumFacing front=craft.view.getBlockState(controller.getPos()).getValue(BlockVandorDirectional.FACING);double a=Math.toRadians(craft.rotationYaw);direction=EnumFacing.getFacingFromVector((float)(front.getFrontOffsetX()*Math.cos(a)-front.getFrontOffsetZ()*Math.sin(a)),0,(float)(front.getFrontOffsetX()*Math.sin(a)+front.getFrontOffsetZ()*Math.cos(a)));}
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
            BlockPos next=null;int options=0;
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos p=target.offset(face);if(p.equals(previous)||!route(p))continue;if(previous==null && face!=direction)continue;next=p;options++;}
            if(options!=1){stop(options==0?"End of route":"Ambiguous route");return true;}
            previous=target;target=next;direction=EnumFacing.getFacingFromVector(target.getX()-previous.getX(),0,target.getZ()-previous.getZ());
            dx=target.getX()+.5-sensor.x;dz=target.getZ()+.5-sensor.z;distance=Math.hypot(dx,dz);controller.setStatus("Following route");
        }
        // Rotate about the sensor so a corner does not sweep it off its route.
        EnumFacing front=craft.view.getBlockState(controller.getPos()).getValue(BlockVandorDirectional.FACING);
        float desired=(float)Math.toDegrees(Math.atan2(direction.getFrontOffsetZ(),direction.getFrontOffsetX())-Math.atan2(front.getFrontOffsetZ(),front.getFrontOffsetX()));
        craft.collision.begin(craft.world,craft);
        try{
            float delta=MathHelper.clamp(MathHelper.wrapDegrees(desired-craft.rotationYaw),-3,3);
            float yaw=craft.collision.turn(craft.world,craft,craft.posX,craft.posY,craft.posZ,craft.rotationYaw,delta);
            VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,yaw);Vec3d after=pose.world(local,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
            boolean turning=Math.abs(MathHelper.wrapDegrees(desired-craft.rotationYaw))>.1;
            double mx=turning?sensor.x-after.x:dx*Math.min(.12,distance)/distance,mz=turning?sensor.z-after.z:dz*Math.min(.12,distance)/distance;
            if(!craft.collision.loaded(craft.world,craft.posX,craft.posY,craft.posZ,mx,0,mz,yaw)){controller.setStatus("Waiting for terrain");return true;}
            double x=craft.collision.clip(craft.world,craft,craft.posX,craft.posY,craft.posZ,mx,0,yaw);
            double z=craft.collision.clip(craft.world,craft,craft.posX+x,craft.posY,craft.posZ,mz,2,yaw);
            if(Math.abs(x-mx)>.00001||Math.abs(z-mz)>.00001||Math.abs(yaw-craft.rotationYaw)<.00001&&turning){controller.setStatus("Blocked");return true;}
            craft.rotationYaw=yaw;craft.setPosition(craft.posX+x,craft.posY,craft.posZ+z);craft.motionX=x;craft.motionZ=z;craft.velocityChanged=true;
        }finally{craft.collision.end();}
        return true;
    }
    private boolean applyMode(int mode,int height){
        if(mode==2){if(!craft.setHover(true))return false;craft.hoverTarget=target==null?craft.posY+height-craft.groundClearance():target.getY()+1+height-controller.getPos().getY();}
        else if(mode==1)return craft.setHover(false);
        return true;
    }
    private boolean park(TileEntityWaypoint waypoint){
        TileEntity raw=craft.view.getTileEntity(craft.structure.seat);if(!(raw instanceof TileEntityPilotSeat))return false;
        java.util.UUID owner=((TileEntityPilotSeat)raw).owner;
        net.minecraft.entity.player.EntityPlayerMP player=owner==null?null:craft.world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
        if(player==null||player.world!=craft.world){controller.setStatus("Waiting for owner");return false;}
        try{
            craft.stopForParking();craft.signals.persist();VehicleRotation.Plan plan=VehicleRotation.plan(craft);
            VehicleService.validateParking(player,craft,plan.origin,true,false);
            BlockPos placedController=plan.origin.add(VehicleRotation.cell(controller.getPos(),(int)craft.structure.bounds.maxX,(int)craft.structure.bounds.maxZ,VehicleRotation.turns(craft.rotationYaw)));
            VehicleTransfer.park((net.minecraft.world.WorldServer)craft.world,craft,plan.origin,player);
            TileEntityAutopilot placed=(TileEntityAutopilot)craft.world.getTileEntity(placedController);
            placed.departureSeat=plan.origin.add(plan.structure.seat);placed.departureOwner=owner;placed.departureMode=waypoint.departMode;placed.departureHeight=waypoint.hoverHeight;
            placed.departureDirect=waypoint.directSignal;placed.departureWaypoint=waypoint.getPos();
            placed.departureAt=waypoint.autoDepart?craft.world.getTotalWorldTime()+Math.max(1,waypoint.delay):Long.MAX_VALUE;
            placed.changed();if(placed.departureAt!=Long.MAX_VALUE)craft.world.scheduleUpdate(placedController,placed.getBlockType(),Math.max(1,waypoint.delay));return true;
        }catch(Exception error){controller.setStatus("Parking blocked");return false;}
    }
    /** Only occupied waypoint departures schedule work; unoccupied markers never tick. */
    public static void depart(TileEntityAutopilot tile){
        if(!tile.enabled()||tile.departureAt<=0||tile.departureSeat==null||tile.getWorld().isRemote)return;
        net.minecraft.world.WorldServer world=(net.minecraft.world.WorldServer)tile.getWorld();
        if(world.getTotalWorldTime()<tile.departureAt){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),(int)Math.min(1200,tile.departureAt-world.getTotalWorldTime()));return;}
        if(!tile.departureDirect){tile.departureAt=Long.MAX_VALUE;tile.changed();TileEntity marker=tile.departureWaypoint==null?null:world.getTileEntity(tile.departureWaypoint);if(marker instanceof TileEntityWaypoint)((TileEntityWaypoint)marker).emit();return;}
        net.minecraft.entity.player.EntityPlayerMP owner=tile.departureOwner==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(tile.departureOwner);
        if(owner==null||owner.world!=world){tile.setStatus("Waiting for owner");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);return;}
        try{
            VehicleScan scan=departures.get(tile);if(scan==null){scan=new VehicleScan(world,tile.departureSeat);departures.put(tile,scan);}
            if(!scan.step(256)){world.scheduleUpdate(tile.getPos(),tile.getBlockType(),1);return;}departures.remove(tile);
            if(scan.error!=null)throw new IllegalArgumentException(scan.error);
            VehicleStructure.Cell controllerCell=scan.result.at(tile.getPos().subtract(scan.origin));
            if(controllerCell==null||controllerCell.state.getBlock()!=tile.getBlockType())throw new IllegalArgumentException("Waypoint controller moved or was copied");
            VehicleFlight.requireThruster(scan.result);if(tile.departureMode==2&&!VehicleFlight.canHover(scan.result))throw new IllegalArgumentException("Hover requires three lift blocks");
            if(!scan.result.matches(world,scan.origin))throw new IllegalArgumentException("Craft changed during departure");
            for(BlockPos p:scan.airBoundary)if(!world.isBlockLoaded(p)||!world.isAirBlock(p))throw new IllegalArgumentException("Craft connections changed");
            if(!VehicleService.clearOfPeople(world,new VehicleCollision(new VehicleWorld(world,scan.result)),scan.origin,owner))throw new IllegalArgumentException("Departure area is occupied");
            VehicleService.authorizeAssembly(owner,scan.result,scan.origin);
            TileEntity marker=tile.departureWaypoint==null?null:world.getTileEntity(tile.departureWaypoint);if(marker instanceof TileEntityWaypoint)((TileEntityWaypoint)marker).emit();
            EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,owner);
            if(tile.departureMode==2&&craft.setHover(true))craft.hoverTarget=tile.departureWaypoint==null?craft.posY+tile.departureHeight-craft.groundClearance():tile.departureWaypoint.getY()+1+tile.departureHeight-craft.autopilot.controller().getPos().getY();
        }catch(Exception error){tile.setStatus("Departure blocked");world.scheduleUpdate(tile.getPos(),tile.getBlockType(),100);}
    }
}
