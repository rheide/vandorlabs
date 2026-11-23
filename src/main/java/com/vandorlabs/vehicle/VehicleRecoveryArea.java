package com.vandorlabs.vehicle;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Native publication and entity recovery footprints of a retained transfer journal. */
final class VehicleRecoveryArea {
    final UUID id;
    final BlockPos origin;
    private final AxisAlignedBB blocks,entity;
    VehicleRecoveryArea(NBTTagCompound record)throws IOException {
        if(!record.hasUniqueId("Entity")||!record.hasKey("Origin",4)||!record.hasKey("Parking",1)
                ||!record.hasKey("Craft",10)||!record.hasKey("X",6)||!record.hasKey("Y",6)
                ||!record.hasKey("Z",6)||!record.hasKey("Yaw",5))throw new IOException("Incomplete vehicle recovery identity");
        id=record.getUniqueId("Entity");
        VehicleStructure craft=VehicleStructure.read(record.getCompoundTag("Craft"));
        boolean parking=record.getBoolean("Parking");
        String recipe=parking?"ParkedCraft":"AssemblyOriginal";
        VehicleStructure published=record.hasKey(recipe,10)?VehicleStructure.read(record.getCompoundTag(recipe)):craft;
        origin=BlockPos.fromLong(record.getLong(!parking&&record.hasKey("AssemblyOriginalOrigin",4)?"AssemblyOriginalOrigin":"Origin"));
        blocks=published.bounds.offset(origin);
        double x=record.getDouble("X"),y=record.getDouble("Y"),z=record.getDouble("Z");
        float yaw=record.getFloat("Yaw");
        float pitch=record.getFloat("MaxwellFlightPitch"),bank=record.getFloat("MaxwellFlightBank");
        if(!Float.isFinite(pitch)||!Float.isFinite(bank)||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw))
            throw new IOException("Invalid vehicle recovery position");
        VehiclePose pose=new VehiclePose(x,y,z,yaw,pitch,bank,craft.facing);
        // A one-block margin protects attached parts and the support of a hanging painting.
        entity=pose.world(craft.bounds,craft.bounds.maxX/2,craft.bounds.maxZ/2).grow(1);
    }
    boolean overlaps(VehicleRecoveryArea next){
        return id.equals(next.id)||blocks.intersects(next.blocks)||entity.intersects(next.blocks)
                ||blocks.intersects(next.entity)||entity.intersects(next.entity);
    }
}
