package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
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
    private final CruiseTiles tiles=new CruiseTiles();
    private BlockPos viewSample;
    private double previousX,previousZ;
    private float sampleTime,duration;
    private static final DataParameter<Integer> FRACTION=EntityDataManager.createKey(EntityGroundVehicle.class,DataSerializers.VARINT);
    private int viewFraction;

    /** Render a continuous path between authoritative geographic samples. */
    public double view(boolean east,float partial){
        if(!active()){viewSample=null;return east?craft.posX:craft.posZ;}
        BlockPos packet=craft.getDataManager().get(ROUTE);
        int fraction=craft.getDataManager().get(FRACTION);
        float now=craft.ticksExisted+partial;
        if(!packet.equals(viewSample)||fraction!=viewFraction){
            double x=packet.getX()+(fraction&255)/256.0,z=packet.getZ()+(fraction>>>8)/256.0;
            if(viewSample==null || Math.abs(viewSample.getX()-x)>512 || Math.abs(viewSample.getZ()-z)>512){
                previousX=x;previousZ=z;duration=1;
            }else{
                previousX=interpolate(true,now);previousZ=interpolate(false,now);
                duration=MathHelper.clamp(now-sampleTime,1,4);
            }
            viewSample=packet;viewFraction=fraction;sampleTime=now;
        }
        return interpolate(east,now);
    }
    private double interpolate(boolean east,float now){
        double from=east?previousX:previousZ;
        double to=east?viewSample.getX()+(viewFraction&255)/256.0:viewSample.getZ()+(viewFraction>>>8)/256.0;
        return from+(to-from)*MathHelper.clamp((now-sampleTime)/duration,0,1);
    }
    private void publishRoute(){
        craft.getDataManager().set(ROUTE,new BlockPos(routeX,1,routeZ));
        craft.getDataManager().set(FRACTION,((int)(long)Math.floor(routeX*256)&255)|(((int)(long)Math.floor(routeZ*256)&255)<<8));
    }

    VehicleCruise(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(ROUTE,BlockPos.ORIGIN);
        craft.getDataManager().register(FRACTION,0);
        craft.getDataManager().register(ANCHOR,BlockPos.ORIGIN);
    }
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
            if (dy < 0 || height <= FlightEnvelope.WORLD_CEILING) return false;
            routeX = craft.posX;
            routeZ = craft.posZ;
            anchor(craft.posX,craft.posZ);
        }
        WorldBorder border = craft.world.getWorldBorder();
        routeX = MathHelper.clamp(routeX + dx, border.minX()+1, border.maxX()-1);
        routeZ = MathHelper.clamp(routeZ + dz, border.minZ()+1, border.maxZ()-1);
        if (height <= FlightEnvelope.WORLD_CEILING) {
            // Load the destination only at reentry. The complete hull starts above build height.
            double bottomOffset = craft.posY - craft.getEntityBoundingBox().minY;
            double arrivalY = FlightEnvelope.WORLD_CEILING + Math.max(0, bottomOffset) + .01;
            craft.world.getChunkFromBlockCoords(new BlockPos(routeX, 0, routeZ));
            endForLanding();
            relocate(routeX, arrivalY, routeZ, true);
            return true;
        }
        boolean residentHop=cruising && CruiseResidentReanchor.ready(craft,routeX,SKY_OFFSET+height,routeZ,yaw,pitch,bank);
        if (!cruising || residentHop || craft.ticksExisted % 3 == 0) {
            publishRoute();
        }
        boolean physical=BlockAvailability.cruiseCoordinates()==BlockAvailability.CruiseCoordinates.PHYSICAL
                && CruiseTracking.ready(craft);
        if(!cruising)relocate(craft.posX,SKY_OFFSET+height,craft.posZ);
        if(physical) {
            if(!projected()) {
                projection(true);
                craft.snapPosition(routeX,SKY_OFFSET+height,routeZ,craft.rotationYaw);
                for(Entity rider:craft.getPassengers())craft.updatePassenger(rider);
            } else craft.setPosition(routeX,SKY_OFFSET+height,routeZ);
        } else if(projected()) {
            projection(false);
            relocate(anchorX,SKY_OFFSET+height,anchorZ,true);
        } else if(residentHop) {
            relocate(routeX,SKY_OFFSET+height,routeZ,true);
            anchor(routeX,routeZ);
        } else if(cruising) craft.setPosition(craft.posX,SKY_OFFSET+height,craft.posZ);
        updateTerrain(routeX, routeZ, height);
        return true;
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

    private void relocate(double x, double y, double z, boolean residentHop) {
        // The spatial index unions previous/current render bounds. A teleport has no swept path.
        craft.prevPosX = craft.lastTickPosX = x;
        craft.prevPosY = craft.lastTickPosY = y;
        craft.prevPosZ = craft.lastTickPosZ = z;
        craft.setPosition(x,y,z);
        craft.view.origin = new BlockPos(craft);
        WorldServer world=(WorldServer)craft.world;
        if (residentHop) world.updateEntityWithOptionalForce(craft,false);
        for (Entity rider : craft.getPassengers()) {
            craft.updatePassenger(rider);
            if (rider instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)rider;
                player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,player.rotationYaw,player.rotationPitch);
                if (residentHop) {
                    world.updateEntityWithOptionalForce(player,false);
                    world.getPlayerChunkMap().updateMovingPlayer(player);
                }
            }
        }
    }

    void preloadTerrain() {
        if (!active() && craft.dimension == 0 && craft.posY >= 200 && craft.flying())
            updateTerrain(craft.posX, craft.posZ, FlightEnvelope.WORLD_CEILING);
    }

    private void updateTerrain(double x, double z, double height) {
        tiles.update(craft,x,z,height);
    }

    void endForLanding() {
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
