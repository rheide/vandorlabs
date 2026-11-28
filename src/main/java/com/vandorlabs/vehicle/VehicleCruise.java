package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.physics.CruiseGeography;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEntityMetadata;
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
    private static final int SKY_OFFSET = 4096;
    private final EntityGroundVehicle craft;
    private double routeX, routeZ, anchorX, anchorZ;
    private CruiseArrival arrival;
    private final CruiseTiles tiles=new CruiseTiles();
    private final CruiseGeography geography=new CruiseGeography();

    public double view(boolean east,float partial){
        if(!active()){geography.clear();return east?craft.posX:craft.posZ;}
        if(projected()){
            double last=east?craft.lastTickPosX:craft.lastTickPosZ,now=east?craft.posX:craft.posZ;
            return last+(now-last)*partial;
        }
        BlockPos route=craft.getDataManager().get(EntityGroundVehicle.CRUISE_ROUTE);
        double tick=(double)craft.ticksExisted+partial;
        geography.accept(route.getX(),route.getZ(),craft.getDataManager().get(EntityGroundVehicle.CRUISE_FRACTION),tick);
        return geography.at(east,tick);
    }
    private void publishRoute(){
        craft.getDataManager().set(EntityGroundVehicle.CRUISE_ROUTE,new BlockPos(routeX,1,routeZ));
        craft.getDataManager().set(EntityGroundVehicle.CRUISE_FRACTION,CruiseGeography.fractions(routeX,routeZ));
    }


    VehicleCruise(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(EntityGroundVehicle.CRUISE_ROUTE,BlockPos.ORIGIN);
        craft.getDataManager().register(EntityGroundVehicle.CRUISE_FRACTION,0);
        craft.getDataManager().register(EntityGroundVehicle.CRUISE_ANCHOR,BlockPos.ORIGIN);
        craft.getDataManager().register(EntityGroundVehicle.CRUISE_HANDOFF,FlightEnvelope.CRUISE_ALTITUDE);
        if(craft.world!=null&&!craft.world.isRemote)
            craft.getDataManager().set(EntityGroundVehicle.CRUISE_HANDOFF,BlockAvailability.cruiseHandoffAltitude());
    }
    public int handoffAltitude(){return craft.getDataManager().get(EntityGroundVehicle.CRUISE_HANDOFF);}
    public boolean projected() { return active()&&craft.getDataManager().get(EntityGroundVehicle.CRUISE_ANCHOR).getY()==1; }
    double anchorX() { return craft.world.isRemote?craft.getDataManager().get(EntityGroundVehicle.CRUISE_ANCHOR).getX():anchorX; }
    double anchorZ() { return craft.world.isRemote?craft.getDataManager().get(EntityGroundVehicle.CRUISE_ANCHOR).getZ():anchorZ; }
    private void anchor(double x,double z) { anchorX=x;anchorZ=z; }
    private void projection(boolean enabled) {
        craft.getDataManager().set(EntityGroundVehicle.CRUISE_ANCHOR,enabled?new BlockPos(anchorX,1,anchorZ):BlockPos.ORIGIN);
    }
    public boolean active() { return craft.getDataManager().get(EntityGroundVehicle.CRUISE_ROUTE).getY() == 1; }
    public double altitude() { return active() ? craft.posY - SKY_OFFSET : craft.posY; }
    public double x() { return !active() ? craft.posX : craft.world.isRemote ? craft.getDataManager().get(EntityGroundVehicle.CRUISE_ROUTE).getX() : routeX; }
    public double z() { return !active() ? craft.posZ : craft.world.isRemote ? craft.getDataManager().get(EntityGroundVehicle.CRUISE_ROUTE).getZ() : routeZ; }

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
        WorldBorder border = craft.world.getWorldBorder();
        routeX = MathHelper.clamp(routeX + dx, border.minX()+1, border.maxX()-1);
        routeZ = MathHelper.clamp(routeZ + dz, border.minZ()+1, border.maxZ()-1);
        // With the shortest blend a deep hull may have to hold its origin above
        // the handoff height. Keep the approach alive until it clears or climbs away.
        if (height <= handoffAltitude() || arrival!=null && dy<=0) {
            boolean entering=arrival==null;
            if(entering)arrival=new CruiseArrival((WorldServer)craft.world);
            publishRoute();
            updateTerrain(routeX,routeZ,height);
            return finishArrival(height,dx,dz,yaw,pitch,bank,entering);
        }
        cancelArrival();
        if (!cruising || craft.ticksExisted % 3 == 0) {
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
        } else if(cruising)
            craft.setPosition(craft.posX,SKY_OFFSET+height,craft.posZ);
        updateTerrain(routeX, routeZ, height);
        return true;
    }

    private boolean finishArrival(double height,double dx,double dz,float yaw,float pitch,float bank,boolean entering) {
        VehiclePose pose=new VehiclePose(routeX,0,routeZ,yaw,pitch,bank,craft.structure.facing);
        double lowest=Math.min(craft.flightBounds(pose).minY,
            craft.getEntityBoundingBox().minY-craft.posY);
        // Old saves can already be cruising inside the transition band.
        double floor=FlightEnvelope.WORLD_CEILING-Math.min(0,lowest)+.01;
        double y=Math.max(floor,height);
        AxisAlignedBB hull=craft.flightBounds(pose).union(craft.getEntityBoundingBox().offset(
                routeX-craft.posX,-craft.posY,routeZ-craft.posZ)).grow(1);
        if(!arrival.ready(craft,pose,hull,dx,dz)) {
            // Ease only subsequent descent over unverified ground. The first position
            // inside the band may be a restored or deliberately repositioned aircraft.
            if(!entering)y=Math.max(y,altitude()-Math.max(0,altitude()-floor)*.12);
            craft.setPosition(projected()?routeX:craft.posX,SKY_OFFSET+y,projected()?routeZ:craft.posZ);
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
        lookAhead();
    }

    /** Seconds of travel whose terrain is loaded ahead of low Flight. */
    static final double LOOKAHEAD_SECONDS=6;
    private CruiseArrival lookahead;
    /**
     * Below the seed view, vanilla loads the square around each rider nearest first, so the chunks the craft
     * is about to reach arrive last. Load a corridor along the heading, front first, several seconds ahead.
     */
    private void lookAhead() {
        double speed=Math.hypot(craft.motionX,craft.motionZ);
        boolean wanted=!active()&&craft.dimension==0&&craft.flying()&&craft.posY<handoffAltitude()&&speed>.25
                &&craft.world instanceof net.minecraft.world.WorldServer&&!craft.getPassengers().isEmpty();
        if(!wanted){if(lookahead!=null){lookahead.close();lookahead=null;}return;}
        if(craft.world.getTotalWorldTime()%2!=0)return;
        if(lookahead==null)lookahead=new CruiseArrival((net.minecraft.world.WorldServer)craft.world);
        int view=craft.world.getMinecraftServer().getPlayerList().getViewDistance();
        int lead=Math.max(3,Math.min(view+4,(int)Math.ceil(speed*20*LOOKAHEAD_SECONDS/16)));
        VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw,0,0,craft.structure.facing);
        lookahead.ready(craft,pose,craft.getEntityBoundingBox(),craft.motionX,craft.motionZ,lead);
    }
    void closeLookAhead(){if(lookahead!=null){lookahead.close();lookahead=null;}}
    /** Whether low-flight lookahead has asked for the chunk column; for live checks. */
    public boolean lookingAhead(int chunkX,int chunkZ){return lookahead!=null&&lookahead.requested(new net.minecraft.util.math.ChunkPos(chunkX,chunkZ));}

    private void updateTerrain(double x, double z, double height) {
        tiles.update(craft,x,z,height);
    }

    void endForLanding() {
        cancelArrival();
        projection(false);
        craft.getDataManager().set(EntityGroundVehicle.CRUISE_ROUTE, BlockPos.ORIGIN);
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
