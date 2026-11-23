package com.vandorlabs.vehicle;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

/** Keep the pilot attached to the tracked aircraft while walking on its local collision surfaces. */
final class FlightCabin {
    private static final DataParameter<NBTTagCompound> POSITION =
            EntityDataManager.createKey(EntityGroundVehicle.class, DataSerializers.COMPOUND_TAG);
    private final EntityGroundVehicle craft;
    private UUID walker;
    private Vec3d feet, previous;
    private int updatedAt;
    private long lastToggle = Long.MIN_VALUE;

    FlightCabin(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(POSITION, new NBTTagCompound());
    }

    void changed(DataParameter<?> key) {
        if (!POSITION.equals(key)) return;
        NBTTagCompound position = craft.getDataManager().get(POSITION);
        UUID next = position.hasUniqueId("Walker") ? position.getUniqueId("Walker") : null;
        previous = next != null && next.equals(walker) ? feet : null;
        walker = next;
        feet = next == null ? null : new Vec3d(position.getDouble("X"), position.getDouble("Y"), position.getDouble("Z"));
        updatedAt = craft.ticksExisted;
    }

    boolean walking(Entity rider) { return rider != null && rider.getUniqueID().equals(walker); }

    Vec3d position(Entity rider, float partial) {
        if (!walking(rider)) return null;
        if (!craft.world.isRemote || previous == null || craft.ticksExisted > updatedAt + 1) return feet;
        float t = Math.max(0, Math.min(1, partial));
        return previous.add(feet.subtract(previous).scale(t));
    }

    void clear() {
        if (walker != null) craft.getDataManager().set(POSITION, new NBTTagCompound());
    }

    void write(NBTTagCompound state) {
        if (walker != null) state.setTag("FlightCabin", craft.getDataManager().get(POSITION).copy());
    }

    void read(NBTTagCompound state) {
        NBTTagCompound position = state.getCompoundTag("FlightCabin");
        if (!craft.flying() || !position.hasUniqueId("Walker") || craft.collision == null) return;
        double x = position.getDouble("X"), y = position.getDouble("Y"), z = position.getDouble("Z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if (!Double.isFinite(craft.collision.deckHeight(x,z,y-.01,y+.01,.3,1.8))) return;
        set(position.getUniqueId("Walker"), new Vec3d(x,y,z));
    }

    private void set(UUID player, Vec3d local) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("Walker", player);
        tag.setDouble("X", local.x);
        tag.setDouble("Y", local.y);
        tag.setDouble("Z", local.z);
        craft.getDataManager().set(POSITION, tag);
    }

    boolean toggle(EntityPlayerMP player) {
        if (craft.transferInProgress || !craft.flying() || craft.getControllingPassenger() != player) return false;
        long now = craft.world.getTotalWorldTime();
        if (now == lastToggle) return false;
        lastToggle = now;
        if (walking(player)) {
            clear();
            player.sendStatusMessage(new TextComponentString("Back in the pilot seat."), true);
            return true;
        }
        if (!craft.poweredFlight() || craft.collision == null) return false;
        BlockPos seat = craft.seatForPassenger(player);
        Vec3d localSeat = craft.toLocal(craft.seatPosition(craft.flightPose(), player, 1));
        // Prefer the closest standing spot beside the seat, including half-height floors.
        Vec3d best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if (dx == 0 && dz == 0) continue;
            double x = seat.getX()+.5+dx, z = seat.getZ()+.5+dz;
            double y = craft.collision.deckHeight(x,z,localSeat.y-1.25,localSeat.y+.6,player.width/2,player.height);
            if (!Double.isFinite(y)) continue;
            Vec3d candidate = new Vec3d(x,y,z);
            double distance = candidate.squareDistanceTo(localSeat);
            if (distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        if (best == null) {
            player.sendStatusMessage(new TextComponentString("No clear standing space beside the pilot seat."), true);
            return false;
        }
        set(player.getUniqueID(), best);
        player.sendStatusMessage(new TextComponentString("Cabin walk. Use movement keys to walk. Sprint + Sneak returns to the pilot seat."), true);
        return true;
    }

    void move(Entity player, int forward, int right) {
        if (!walking(player) || forward == 0 && right == 0) return;
        double angle = Math.toRadians(player.rotationYaw - craft.pilotViewReference(craft.flightPose()));
        double speed = .215 / Math.hypot(forward,right);
        double x = (feet.x - (forward*Math.sin(angle)+right*Math.cos(angle))*speed);
        double z = (feet.z + (forward*Math.cos(angle)-right*Math.sin(angle))*speed);
        Vec3d target = step(player,x,z);
        if (target == null) target = step(player,x,feet.z);
        if (target == null || feet.equals(target)) target = step(player,feet.x,z);
        if (target != null && !feet.equals(target)) set(walker,target);
    }

    private Vec3d step(Entity player, double x, double z) {
        double y = craft.collision.deckHeight(x,z, feet.y-.6, feet.y+.6,player.width/2,player.height);
        return Double.isFinite(y) ? new Vec3d(x,y,z) : null;
    }
}
