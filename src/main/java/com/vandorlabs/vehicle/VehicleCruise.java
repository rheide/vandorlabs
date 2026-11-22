package com.vandorlabs.vehicle;

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
    private static final DataParameter<BlockPos> STORAGE = EntityDataManager.createKey(EntityGroundVehicle.class, DataSerializers.BLOCK_POS);
    private static final int SKY_OFFSET = 4096;
    private final EntityGroundVehicle craft;
    private double routeX, routeZ, storageX, storageZ;
    private MessageCruiseTerrain pending;
    private int nextPixel, publishedX, publishedZ, publishedStep;

    VehicleCruise(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(ROUTE,BlockPos.ORIGIN);
        craft.getDataManager().register(STORAGE,BlockPos.ORIGIN);
    }
    double storageX() {return craft.world.isRemote?craft.getDataManager().get(STORAGE).getX():storageX;}
    double storageZ() {return craft.world.isRemote?craft.getDataManager().get(STORAGE).getZ():storageZ;}
    private void storage(double x,double z) {
        storageX=x;storageZ=z;
        if(CruiseCoordinateProbe.FULL) craft.getDataManager().set(STORAGE,new BlockPos(x,0,z));
    }
    public boolean active() { return craft.getDataManager().get(ROUTE).getY() == 1; }
    public double altitude() { return active() ? craft.posY - SKY_OFFSET : craft.posY; }
    public double x() { return !active() ? craft.posX : craft.world.isRemote ? craft.getDataManager().get(ROUTE).getX() : routeX; }
    public double z() { return !active() ? craft.posZ : craft.world.isRemote ? craft.getDataManager().get(ROUTE).getZ() : routeZ; }

    boolean move(double dx, double dy, double dz) {
        if (craft.world.isRemote || craft.dimension != 0) return false;
        boolean cruising = active();
        double height = Math.min(FlightEnvelope.MAX_ALTITUDE, altitude() + dy);
        if (!cruising) {
            if (dy < 0 || height <= FlightEnvelope.WORLD_CEILING) return false;
            routeX = craft.posX;
            routeZ = craft.posZ;
            storage(craft.posX,craft.posZ);
        }
        WorldBorder border = craft.world.getWorldBorder();
        routeX = MathHelper.clamp(routeX + dx, border.minX()+1, border.maxX()-1);
        routeZ = MathHelper.clamp(routeZ + dz, border.minZ()+1, border.maxZ()-1);
        if (height <= FlightEnvelope.WORLD_CEILING) {
            // Load the destination only at reentry. The complete hull starts above build height.
            double bottomOffset = craft.posY - craft.getEntityBoundingBox().minY;
            double arrivalY = FlightEnvelope.WORLD_CEILING + Math.max(0, bottomOffset) + .01;
            craft.world.getChunkFromBlockCoords(new BlockPos(routeX, 0, routeZ));
            craft.getDataManager().set(ROUTE, BlockPos.ORIGIN);
            relocate(routeX, arrivalY, routeZ);
            pending = null;
            publishedStep = 0;
            return true;
        }
        if (!cruising || craft.ticksExisted % 3 == 0) {
            craft.getDataManager().set(ROUTE, new BlockPos(routeX,1,routeZ));
        }
        if (!cruising) relocate(craft.posX, SKY_OFFSET + height, craft.posZ);
        else if(CruiseCoordinateProbe.FULL) craft.setPosition(routeX,SKY_OFFSET+height,routeZ);
        else craft.setPosition(craft.posX, SKY_OFFSET + height, craft.posZ);
        updateTerrain(height);
        return true;
    }

    /** Vanilla may find the saved chunk entity before attempting to restore the player's RootVehicle. */
    @SubscribeEvent public static void resumeSeat(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.dimension != 0 || player.posY < SKY_OFFSET || player.isRiding()) return;
        WorldServer world = player.getServerWorld();
        AxisAlignedBB nearby = player.getEntityBoundingBox().grow(4,64,4);
        for (EntityGroundVehicle craft : VehicleLookup.near(world,nearby)) {
            if (!craft.cruise.active() || craft.structure == null || craft.transferInProgress ||
                    world.getEntityFromUuid(craft.getUniqueID()) != craft) continue;
            NBTTagCompound assignments = craft.occupiedSeatAssignments();
            String rider = player.getUniqueID().toString();
            if (!assignments.hasKey(rider,4)) continue;
            if (craft.board(player,BlockPos.fromLong(assignments.getLong(rider)))) {
                craft.updatePassenger(player);
                return;
            }
        }
    }

    private void relocate(double x, double y, double z) {
        // The spatial index unions previous/current render bounds. A teleport has no swept path.
        craft.prevPosX = craft.lastTickPosX = x;
        craft.prevPosY = craft.lastTickPosY = y;
        craft.prevPosZ = craft.lastTickPosZ = z;
        craft.setPosition(x,y,z);
        craft.view.origin = new BlockPos(craft);
        for (Entity rider : craft.getPassengers()) {
            craft.updatePassenger(rider);
            if (rider instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)rider;
                player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,player.rotationYaw,player.rotationPitch);
            }
        }
    }

    private void updateTerrain(double height) {
        if (craft.getPassengers().isEmpty()) return;
        final int size = MessageCruiseTerrain.SIZE;
        int step = Math.max(16,Integer.highestOneBit((int)height / 8));
        int centerX = MathHelper.floor(routeX / step) * step;
        int centerZ = MathHelper.floor(routeZ / step) * step;
        if (pending == null) {
            if (step == publishedStep && Math.abs(routeX-publishedX) < size*step/5 &&
                    Math.abs(routeZ-publishedZ) < size*step/5 && craft.ticksExisted%200 != 0) return;
            pending = new MessageCruiseTerrain(craft.getEntityId(), centerX, centerZ, step);
            nextPixel = 0;
        }
        int end = Math.min(nextPixel + 128, size*size);
        long started = System.nanoTime();
        do {
            int px = pending.centerX + (nextPixel%size-size/2)*pending.step;
            int pz = pending.centerZ + (nextPixel/size-size/2)*pending.step;
            pending.pixels[nextPixel++] = NavigationService.cruiseColor(craft.world,px,pz,pending.step);
        } while (nextPixel < end && System.nanoTime()-started < 750000L);
        if (nextPixel == size*size) {
            for (Entity rider : craft.getPassengers()) if (rider instanceof EntityPlayerMP)
                PacketHandler.INSTANCE.sendTo(pending,(EntityPlayerMP)rider);
            publishedX = pending.centerX;
            publishedZ = pending.centerZ;
            publishedStep = pending.step;
            pending = null;
        }
    }

    void write(NBTTagCompound tag) {
        if (!active()) return;
        tag.setDouble("CruiseRouteX", routeX);
        tag.setDouble("CruiseRouteZ", routeZ);
        if(CruiseCoordinateProbe.FULL) {
            tag.setDouble("CruiseStorageX",storageX);
            tag.setDouble("CruiseStorageZ",storageZ);
            CruiseCoordinateProbe.writePosition(tag,storageX,storageZ);
        }
    }
    void read(NBTTagCompound tag) {
        if (craft.dimension != 0 || !tag.hasKey("CruiseRouteX",6) || !tag.hasKey("CruiseRouteZ",6)) return;
        routeX = tag.getDouble("CruiseRouteX");
        routeZ = tag.getDouble("CruiseRouteZ");
        if (Double.isFinite(routeX) && Double.isFinite(routeZ) && Math.abs(routeX)<=30000000 && Math.abs(routeZ)<=30000000) {
            storage(craft.posX,craft.posZ);
            craft.getDataManager().set(ROUTE,new BlockPos(routeX,1,routeZ));
        }
    }
}
