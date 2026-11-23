package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.physics.CruiseGeography;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.border.WorldBorder;

/** Geographic flight with a separate canonical terrain and save anchor. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class VehicleCruise {
    private static final DataParameter<BlockPos> ROUTE = EntityDataManager.createKey(EntityGroundVehicle.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<BlockPos> ANCHOR=EntityDataManager.createKey(EntityGroundVehicle.class,DataSerializers.BLOCK_POS);
    private static final int SKY_OFFSET = 4096;
    private final EntityGroundVehicle craft;
    private double routeX, routeZ, anchorX, anchorZ;
    private CruiseArrival arrival,resident;
    private int residentSince;
    private double arrivalAltitude;
    private final CruiseTiles tiles=new CruiseTiles();
    private final CruiseGeography geography=new CruiseGeography();
    private static final DataParameter<Integer> FRACTION=EntityDataManager.createKey(EntityGroundVehicle.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> HANDOFF=EntityDataManager.createKey(EntityGroundVehicle.class,DataSerializers.VARINT);

    public double view(boolean east,float partial){
        if(!active()){geography.clear();return east?craft.posX:craft.posZ;}
        if(projected()){
            double last=east?craft.lastTickPosX:craft.lastTickPosZ,now=east?craft.posX:craft.posZ;
            return last+(now-last)*partial;
        }
        BlockPos route=craft.getDataManager().get(ROUTE);
        double tick=(double)craft.ticksExisted+partial;
        geography.accept(route.getX(),route.getZ(),craft.getDataManager().get(FRACTION),tick);
        return geography.at(east,tick);
    }
    private void publishRoute(){
        craft.getDataManager().set(ROUTE,new BlockPos(routeX,1,routeZ));
        craft.getDataManager().set(FRACTION,CruiseGeography.fractions(routeX,routeZ));
    }


    VehicleCruise(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(ROUTE,BlockPos.ORIGIN);
        craft.getDataManager().register(FRACTION,0);
        craft.getDataManager().register(ANCHOR,BlockPos.ORIGIN);
        craft.getDataManager().register(HANDOFF,FlightEnvelope.CRUISE_ALTITUDE);
        if(craft.world!=null&&!craft.world.isRemote)
            craft.getDataManager().set(HANDOFF,BlockAvailability.cruiseHandoffAltitude());
    }
    public int handoffAltitude(){return craft.getDataManager().get(HANDOFF);}
    boolean preparingArrival(){ return arrival!=null; }
    public boolean projected() { return active()&&craft.getDataManager().get(ANCHOR).getY()==1; }
    double anchorX() { return craft.world.isRemote?craft.getDataManager().get(ANCHOR).getX():anchorX; }
    double anchorZ() { return craft.world.isRemote?craft.getDataManager().get(ANCHOR).getZ():anchorZ; }
    private void anchor(double x,double z) { anchorX=x;anchorZ=z; }
    private void projection(boolean enabled) {
        craft.getDataManager().set(ANCHOR,enabled?new BlockPos(anchorX,1,anchorZ):BlockPos.ORIGIN);
    }
    public boolean active() { return craft.getDataManager().get(ROUTE).getY() == 1; }
    public double altitude() { return active() ? craft.posY - SKY_OFFSET : craft.posY; }
    public double x() { return !active() ? craft.posX : craft.world.isRemote ? craft.getDataManager().get(ROUTE).getX() : routeX; }
    public double z() { return !active() ? craft.posZ : craft.world.isRemote ? craft.getDataManager().get(ROUTE).getZ() : routeZ; }

    boolean move(double dx, double dy, double dz) {
        return move(dx,dy,dz,craft.rotationYaw,craft.flightPitch(),craft.flightBank());
    }

    boolean move(double dx,double dy,double dz,float yaw,float pitch,float bank) {
        if (craft.world.isRemote || craft.dimension != 0) return false;
        boolean cruising = active();
        double height = Math.min(FlightEnvelope.MAX_ALTITUDE, altitude() + dy);
        if (!cruising) {
            if (dy < 0 || height <= handoffAltitude()) return false;
            routeX = craft.posX;
            routeZ = craft.posZ;
            anchor(craft.posX,craft.posZ);
        }
        if(arrival!=null)return finishArrival(yaw,pitch,bank);
        WorldBorder border = craft.world.getWorldBorder();
        routeX = MathHelper.clamp(routeX + dx, border.minX()+1, border.maxX()-1);
        routeZ = MathHelper.clamp(routeZ + dz, border.minZ()+1, border.maxZ()-1);
        if (height <= handoffAltitude()) {
            VehiclePose pose=new VehiclePose(routeX,craft.posY,routeZ,yaw,pitch,bank,craft.structure.facing);
            AxisAlignedBB hull=craft.flightBounds(pose).union(craft.getEntityBoundingBox().offset(
                routeX-craft.posX,0,routeZ-craft.posZ));
            closeResident();
            arrivalAltitude=height;
            arrival=new CruiseArrival((WorldServer)craft.world,routeX,routeZ,hull.grow(1),true);
            publishRoute();
            return finishArrival(yaw,pitch,bank);
        }
        boolean residentHop=cruising && CruiseResidentReanchor.ready(craft,routeX,SKY_OFFSET+height,routeZ,yaw,pitch,bank);
        if (!cruising || residentHop || craft.ticksExisted % 3 == 0) {
            publishRoute();
        }
        boolean physical=BlockAvailability.cruiseCoordinates()==BlockAvailability.CruiseCoordinates.PHYSICAL
                && CruiseTracking.ready(craft);
        if(!cruising)relocate(craft.posX,SKY_OFFSET+height,craft.posZ);
        if(physical) {
            closeResident();
            if(!projected()) {
                projection(true);
                craft.snapPosition(routeX,SKY_OFFSET+height,routeZ,craft.rotationYaw);
                for(Entity rider:craft.getPassengers())craft.updatePassenger(rider);
            } else craft.setPosition(routeX,SKY_OFFSET+height,routeZ);
        } else if(projected()) {
            projection(false);
            relocate(anchorX,SKY_OFFSET+height,anchorZ,true);
        } else if(cruising&&!moveToResident(residentHop,SKY_OFFSET+height,yaw,pitch,bank))
            craft.setPosition(craft.posX,SKY_OFFSET+height,craft.posZ);
        updateTerrain(routeX, routeZ, height);
        return true;
    }

    private boolean moveToResident(boolean candidate,double y,float yaw,float pitch,float bank) {
        if(resident==null) {
            if(!candidate)return false;
            VehiclePose target=new VehiclePose(routeX,y,routeZ,yaw,pitch,bank,craft.structure.facing);
            AxisAlignedBB hull=craft.flightBounds(target).union(craft.getEntityBoundingBox().offset(
                routeX-craft.posX,0,routeZ-craft.posZ));
            resident=new CruiseArrival((WorldServer)craft.world,routeX,routeZ,hull.grow(1),false);
            residentSince=craft.ticksExisted;
        }
        if(craft.ticksExisted-residentSince>20||!CruiseResidentReanchor.footprintReady(
                craft,resident.x,y,resident.z,yaw,pitch,bank)) {
            closeResident();
            return false;
        }
        VehiclePose target=new VehiclePose(resident.x,y,resident.z,yaw,pitch,bank,craft.structure.facing);
        if(!resident.ready(craft,target))return false;
        anchor(resident.x,resident.z);
        relocate(resident.x,y,resident.z,true,true);
        closeResident();
        return true;
    }

    private void closeResident() {
        if(resident==null)return;
        resident.close();
        resident=null;
    }

    private boolean finishArrival(float yaw,float pitch,float bank) {
        VehiclePose pose=new VehiclePose(routeX,0,routeZ,yaw,pitch,bank,craft.structure.facing);
        double lowest=Math.min(craft.flightBounds(pose).minY,
            craft.getEntityBoundingBox().minY-craft.posY);
        // Old saves can already be cruising inside the transition band.
        double y=Math.max(FlightEnvelope.WORLD_CEILING-Math.min(0,lowest)+.01,
                Math.min(arrivalAltitude,handoffAltitude()));
        if(!arrival.ready(craft,pose)) {
            double hold=SKY_OFFSET+Math.min(y,altitude());
            craft.setPosition(projected()?routeX:craft.posX,hold,projected()?routeZ:craft.posZ);
            return true;
        }
        CruiseArrival prepared=arrival;
        arrival=null;
        endForLanding();
        craft.applyFlightPose(new VehiclePose(craft.posX,craft.posY,craft.posZ,yaw,pitch,bank,craft.structure.facing));
        relocate(routeX,y,routeZ,true,true);
        prepared.close();
        return true;
    }

    void cancelArrival() {
        closeResident();
        if(arrival==null)return;
        arrival.close();
        arrival=null;
    }

    /** Vanilla may find the saved chunk entity before attempting to restore the player's RootVehicle. */
    @SubscribeEvent public static void resumeSeat(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.dimension != 0 || player.posY < SKY_OFFSET || player.isRiding()) return;
        WorldServer world = player.getServerWorld();
        NBTTagCompound saved=player.getEntityData();
        if(saved.hasUniqueId(CruiseTracking.SAVED_CRAFT)) {
            Entity vehicle=world.getEntityFromUuid(saved.getUniqueId(CruiseTracking.SAVED_CRAFT));
            saved.removeTag(CruiseTracking.SAVED_CRAFT+"Most");
            saved.removeTag(CruiseTracking.SAVED_CRAFT+"Least");
            if(vehicle instanceof EntityGroundVehicle && resume(player,(EntityGroundVehicle)vehicle))return;
        }
        AxisAlignedBB nearby = player.getEntityBoundingBox().grow(4,64,4);
        for(EntityGroundVehicle craft:VehicleLookup.near(world,nearby))if(resume(player,craft))return;
    }

    private static boolean resume(EntityPlayerMP player,EntityGroundVehicle craft) {
        if(!craft.cruise.active() || craft.structure==null || craft.transferInProgress
                || player.getServerWorld().getEntityFromUuid(craft.getUniqueID())!=craft)return false;
        NBTTagCompound assignments=craft.occupiedSeatAssignments();
        String rider=player.getUniqueID().toString();
        if(!assignments.hasKey(rider,4)||!craft.board(player,BlockPos.fromLong(assignments.getLong(rider))))return false;
        craft.updatePassenger(player);
        return true;
    }

    void riderLeft(Entity rider) {
        if(!projected()||craft.world.isRemote)return;
        // Move an ordinary dismount back to an already watched column before vanilla can index it.
        double x=anchorX+rider.posX-craft.posX,z=anchorZ+rider.posZ-craft.posZ;
        if(rider instanceof EntityPlayerMP&&((EntityPlayerMP)rider).connection!=null) {
            EntityPlayerMP player=(EntityPlayerMP)rider;
            player.connection.setPlayerLocation(x,rider.posY,z,rider.rotationYaw,rider.rotationPitch);
        } else rider.setPosition(x,rider.posY,z);
    }

    private void relocate(double x, double y, double z) { relocate(x,y,z,false); }

    private void relocate(double x, double y, double z, boolean moveWatches) {
        relocate(x,y,z,moveWatches,false);
    }

    private void relocate(double x,double y,double z,boolean moveWatches,boolean handoff) {
        // The spatial index unions previous/current render bounds. A teleport has no swept path.
        craft.prevPosX = craft.lastTickPosX = x;
        craft.prevPosY = craft.lastTickPosY = y;
        craft.prevPosZ = craft.lastTickPosZ = z;
        craft.setPosition(x,y,z);
        craft.view.origin = new BlockPos(craft);
        WorldServer world=(WorldServer)craft.world;
        if (moveWatches) world.updateEntityWithOptionalForce(craft,false);
        if(handoff) {
            // Move the client craft out of its old chunk before updateMovingPlayer
            // queues unload packets. The destination origin must arrive first.
            SPacketEntityMetadata metadata=new SPacketEntityMetadata(craft.getEntityId(),craft.getDataManager(),true);
            for(Entity rider:craft.getPassengers())if(rider instanceof EntityPlayerMP)
                ((EntityPlayerMP)rider).connection.sendPacket(metadata);
            VehicleNetwork.state(craft,true);
        }
        for (Entity rider : craft.getPassengers()) {
            craft.updatePassenger(rider);
            if (rider instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)rider;
                player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,player.rotationYaw,player.rotationPitch);
                if (moveWatches) {
                    world.updateEntityWithOptionalForce(player,false);
                    world.getPlayerChunkMap().updateMovingPlayer(player);
                }
            }
        }
    }

    void preloadTerrain() {
        if (!active() && craft.dimension == 0 && craft.posY >= 200 && craft.flying())
            updateTerrain(craft.posX, craft.posZ, Math.max(craft.posY,FlightEnvelope.WORLD_CEILING));
    }

    private void updateTerrain(double x, double z, double height) {
        tiles.update(craft,x,z,height);
    }

    void endForLanding() {
        cancelArrival();
        projection(false);
        craft.getDataManager().set(ROUTE, BlockPos.ORIGIN);
        clearTerrain();
    }

    private void clearTerrain() {
        tiles.clear();
    }

    void write(NBTTagCompound tag) {
        if (!active()) return;
        tag.setDouble("CruiseRouteX", routeX);
        tag.setDouble("CruiseRouteZ", routeZ);
        if(projected())CruiseTracking.writePosition(tag,anchorX,anchorZ);
    }
    void read(NBTTagCompound tag) {
        if (craft.dimension != 0 || !tag.hasKey("CruiseRouteX",6) || !tag.hasKey("CruiseRouteZ",6)) return;
        routeX = tag.getDouble("CruiseRouteX");
        routeZ = tag.getDouble("CruiseRouteZ");
        if (Double.isFinite(routeX) && Double.isFinite(routeZ) && Math.abs(routeX)<=30000000 && Math.abs(routeZ)<=30000000) {
            anchor(craft.posX,craft.posZ);
            publishRoute();
        }
    }
}
