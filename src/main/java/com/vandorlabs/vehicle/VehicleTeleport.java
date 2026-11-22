package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** Called by vanilla and JourneyMap at the point where an authorized teleport dismounts its subject. */
public final class VehicleTeleport {
    public static void detach(Entity rider) {
        if (!(rider instanceof EntityPlayerMP) || !(rider.getRidingEntity() instanceof EntityGroundVehicle)) {
            rider.dismountRidingEntity();
            return;
        }
        EntityGroundVehicle craft=(EntityGroundVehicle)rider.getRidingEntity();
        boolean pilot=craft.getControllingPassenger()==rider;
        double x=rider.posX, y=rider.posY, z=rider.posZ;
        craft.teleportingPassenger=rider;
        try {
            rider.dismountRidingEntity();
        } finally {
            craft.teleportingPassenger=null;
        }
        if (rider.getRidingEntity()==craft) return;
        // The pilot may still hold the mount even if relocation dropped it from the client entity index.
        // Release that reference before the teleport position packet reaches the client.
        VehicleNetwork.releaseTeleportedRider((EntityPlayerMP)rider,craft);
        // Vanilla's ordinary dismount can move the player onto the roof. Relative /tp arguments
        // are applied after this call and must still be relative to the original seat.
        rider.setPosition(x,y,z);
        if (pilot && craft.getControllingPassenger()==null && !craft.transferInProgress && !craft.ownershipBlocked)
            land(craft,(EntityPlayerMP)rider);
    }

    private static void land(EntityGroundVehicle craft,EntityPlayerMP departing) {
        if (craft.collision==null || craft.isDead) return;
        if (craft.autopilot.enabled()) craft.autopilot.toggle();
        craft.stopForParking();
        if (!craft.flying() && !craft.hover && !craft.takeoffRoll() && craft.onGround) return;
        WorldServer world=(WorldServer)craft.world;
        double x=craft.cruise.x(), z=craft.cruise.z();
        AxisAlignedBB hull=craft.articulation.active()?craft.articulation.worldBounds(x,0,z,craft.rotationYaw)
                :craft.collision.bounds(craft.rotationYaw).offset(x,0,z);
        if (!world.getWorldBorder().contains(new BlockPos(hull.minX,64,hull.minZ))
                || !world.getWorldBorder().contains(new BlockPos(hull.maxX,64,hull.maxZ))) {
            VehicleNetwork.error(departing,"The aircraft is too close to the world border for automatic landing.");
            return;
        }
        // This is the landing site. No intermediate route column is queried.
        int top=0;
        for (int cx=MathHelper.floor(hull.minX-1)>>4; cx<=MathHelper.floor(hull.maxX+1)>>4; cx++)
            for (int cz=MathHelper.floor(hull.minZ-1)>>4; cz<=MathHelper.floor(hull.maxZ+1)>>4; cz++) {
                Chunk chunk=world.getChunkProvider().provideChunk(cx,cz);
                top=Math.max(top,chunk.getTopFilledSegment()+16);
            }
        double y=top+2-hull.minY;
        craft.collision.begin(world,craft);
        boolean articulated=craft.articulation.begin();
        try {
            while (y+hull.minY>0) {
                double step=-Math.min(16,y+hull.minY+1);
                double allowed=craft.collision.clip(world,craft,x,y,z,step,1,craft.rotationYaw);
                y+=allowed;
                if (allowed>step+1e-6) {
                    craft.landAfterTeleport(x,y,z);
                    return;
                }
            }
        } finally {
            if (articulated) craft.articulation.end();
            craft.collision.end();
        }
        VehicleNetwork.error(departing,"No solid surface below the aircraft for automatic landing.");
    }
    private VehicleTeleport(){}
}
