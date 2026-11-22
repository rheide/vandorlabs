package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.navigation.NavigationService;
import com.vandorlabs.network.MessageCruiseTerrain;
import com.vandorlabs.network.PacketHandler;
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

/** A rider keeps the departure column alive while route positions are purely geographic. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class VehicleCruise {
    private static final DataParameter<BlockPos> ROUTE = EntityDataManager.createKey(EntityGroundVehicle.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<BlockPos> ANCHOR=EntityDataManager.createKey(EntityGroundVehicle.class,DataSerializers.BLOCK_POS);
    private static final int SKY_OFFSET = 4096;
    private final EntityGroundVehicle craft;
    private double routeX, routeZ, anchorX, anchorZ;
    private MessageCruiseTerrain pending, published;
    private final java.util.Set<EntityPlayerMP> viewers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private int nextPixel, publishedX, publishedZ, publishedStep;

    VehicleCruise(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(ROUTE,BlockPos.ORIGIN);
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
            craft.getDataManager().set(ROUTE, new BlockPos(routeX,1,routeZ));
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
        if(rider instanceof EntityPlayerMP) {
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
        java.util.List<Entity> riders=craft.getPassengers();
        viewers.retainAll(riders);
        if (riders.isEmpty()) return;
        final int size = MessageCruiseTerrain.SIZE;
        int step = Math.max(8,Integer.highestOneBit((int)height / 8) / 2);
        int centerX = MathHelper.floor(x / step) * step;
        int centerZ = MathHelper.floor(z / step) * step;
        if (published!=null && Math.abs(x-publishedX)<size*publishedStep/4 && Math.abs(z-publishedZ)<size*publishedStep/4) {
            for (Entity rider:riders) if (rider instanceof EntityPlayerMP && viewers.add((EntityPlayerMP)rider))
                PacketHandler.INSTANCE.sendTo(published,(EntityPlayerMP)rider);
        }
        if (pending != null && (Math.abs(x-pending.centerX) >= size*pending.step/4
                || Math.abs(z-pending.centerZ) >= size*pending.step/4)) {
            pending = null;
            publishedStep = 0;
        }
        if (pending == null) {
            if (step == publishedStep && Math.abs(x-publishedX) < size*step/5 &&
                    Math.abs(z-publishedZ) < size*step/5) return;
            pending = new MessageCruiseTerrain(craft.getEntityId(), centerX, centerZ, step);
            nextPixel = 0;
            pending.sampleStride = publishedStep == 0 ? 8 : 1;
        }
        // First publish a coarse image, then refine it in the same geographic footprint.
        int stride = pending.sampleStride;
        int columns = size / stride;
        int end = Math.min(nextPixel + 512, columns*columns);
        long started = System.nanoTime();
        do {
            int col = nextPixel % columns * stride, row = nextPixel / columns * stride;
            int px = pending.centerX + (col-size/2)*pending.step;
            int pz = pending.centerZ + (row-size/2)*pending.step;
            int color = NavigationService.cruiseColor(craft.world,px,pz,pending.step);
            for (int r=row; r<row+stride; r++)
                java.util.Arrays.fill(pending.pixels,r*size+col,r*size+col+stride,color);
            nextPixel++;
        } while (nextPixel < end && System.nanoTime()-started < 750000L);
        if (nextPixel == columns*columns) {
            published=pending;
            for (Entity rider:riders) if (rider instanceof EntityPlayerMP) {
                viewers.add((EntityPlayerMP)rider);
                PacketHandler.INSTANCE.sendTo(published,(EntityPlayerMP)rider);
            }
            publishedX = pending.centerX;
            publishedZ = pending.centerZ;
            publishedStep = pending.step;
            // Forge may encode the message later; refinement needs an independent image.
            if (stride>1) {
                pending = new MessageCruiseTerrain(craft.getEntityId(),publishedX,publishedZ,publishedStep);
                nextPixel = 0;
                pending.sampleStride = stride==8 ? 2 : 1;
            } else pending = null;
        }
    }

    void endForLanding() {
        projection(false);
        craft.getDataManager().set(ROUTE, BlockPos.ORIGIN);
        clearTerrain();
    }

    private void clearTerrain() {
        pending=null;
        published=null;
        publishedStep=0;
        viewers.clear();
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
            craft.getDataManager().set(ROUTE,new BlockPos(routeX,1,routeZ));
        }
    }
}
