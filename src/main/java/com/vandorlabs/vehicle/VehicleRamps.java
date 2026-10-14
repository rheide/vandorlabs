package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import java.util.*;

/** Event-driven ramp endpoints built and checked before publishing a replacement snapshot. */
public final class VehicleRamps {
    private VehicleRamps(){}
    public static boolean signal(EntityGroundVehicle craft,BlockPos at,boolean powered){
        TileEntity raw=craft.view.getTileEntity(at);if(!(raw instanceof TileEntityRampController))return false;
        NBTTagCompound old=raw.writeToNBT(new NBTTagCompound());
        boolean deploy=powered==old.getBoolean("ActivateOnPower");
        if(old.getBoolean("SignalKnown")&&old.getBoolean("Latched")==deploy)return false;
        try{
            // The clone can discover an unexpanded platform and assemble new cells,
            // while the authoritative world and live control subscriptions remain untouched.
            VehicleWorld staging=new VehicleWorld(craft.world,craft.structure);
            TileEntityRampController ramp=(TileEntityRampController)staging.getTileEntity(at);
            List<VehicleStructure.Cell> parts=ramp.vehicleEndpoint(deploy);
            Set<BlockPos> removed=new HashSet<>(ramp.vehicleSources());
            for(VehicleStructure.Cell cell:craft.structure.cells){TileEntity tile=staging.getTileEntity(cell.pos);
                if(tile instanceof TileEntityControlledRamp&&((TileEntityControlledRamp)tile).controller.equals(at))removed.add(cell.pos);}
            Map<BlockPos,VehicleStructure.Cell> cells=new LinkedHashMap<>();
            for(VehicleStructure.Cell cell:craft.structure.cells)if(!removed.contains(cell.pos))cells.put(cell.pos,cell);
            for(VehicleStructure.Cell part:parts){if(cells.containsKey(part.pos))throw new IllegalArgumentException("Ramp movement overlaps another vehicle component");cells.put(part.pos,part);}
            NBTTagCompound controller=ramp.writeToNBT(new NBTTagCompound());controller.setBoolean("ChannelSignal",powered);
            cells.put(at,new VehicleStructure.Cell(at,craft.structure.at(at).state,controller));
            int minX=0,minY=0,minZ=0;for(BlockPos p:cells.keySet()){minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());}
            BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> rebased=new ArrayList<>();
            for(VehicleStructure.Cell cell:cells.values())rebased.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
            VehicleStructure next=new VehicleStructure(rebased,craft.structure.seat.add(shift),craft.structure.facing,craft.structure.gearCount);
            VehicleWorld nextView=new VehicleWorld(craft.world,next);VehicleRotation.validate(next,nextView);next.encode();
            VehicleCollision collision=new VehicleCollision(nextView);
            double oldX=craft.structure.bounds.maxX/2,oldZ=craft.structure.bounds.maxZ/2,newX=next.bounds.maxX/2,newZ=next.bounds.maxZ/2;
            double radians=Math.toRadians(craft.rotationYaw),c=Math.cos(radians),s=Math.sin(radians),dx=newX-oldX-shift.getX(),dz=newZ-oldZ-shift.getZ();
            double x=craft.posX+oldX-newX+c*dx-s*dz,z=craft.posZ+oldZ-newZ+s*dx+c*dz,y=craft.posY-shift.getY();
            if(!collision.loaded(craft.world,x,y,z,0,0,0,craft.rotationYaw))throw new IllegalArgumentException("Ramp movement reaches unloaded terrain or world bounds");
            VehiclePose pose=new VehiclePose(x,y,z,craft.rotationYaw);
            for(AxisAlignedBB box:collision.boxes){AxisAlignedBB absolute=pose.world(box,newX,newZ).grow(-1e-5);
                if(!craft.world.getCollisionBoxes(craft,absolute).isEmpty())throw new IllegalArgumentException("Ramp movement is blocked by terrain");
                for(net.minecraft.entity.Entity entity:craft.world.getEntitiesWithinAABBExcludingEntity(craft,absolute))if(!craft.getPassengers().contains(entity)&&entity.canBeCollidedWith())throw new IllegalArgumentException("Ramp movement is blocked by an occupant");}
            double altitudeShift=y-craft.posY;
            craft.install(next);craft.hoverTarget+=altitudeShift;craft.setPosition(x,y,z);craft.signals.open();
            for(net.minecraft.entity.player.EntityPlayer player:craft.world.playerEntities)if(player instanceof net.minecraft.entity.player.EntityPlayerMP&&player.getDistanceSq(craft)<256*256)
                VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,next,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);
            return true;
        }catch(Exception failure){com.vandorlabs.VandorLabs.logger.debug("Vehicle ramp preserved: {}",failure.getMessage());
            if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)((net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger()).sendStatusMessage(new net.minecraft.util.text.TextComponentString("Ramp unchanged: "+failure.getMessage()),true);
            return false;}
    }
}
