package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.navigation.NativeNavigationRuntime;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** High Flight advances geographic coordinates while its physical chunk stays at the departure point. */
final class NativeVehicleCruise {
    static final int SKY_OFFSET = 4096;
    private static final int DESTINATION_RADIUS = 4;
    private final NativeGroundVehicle craft;
    private double x, z;
    private NativeCruiseTerrain pending;
    private int nextPixel, publishedX, publishedZ, publishedStep;

    NativeVehicleCruise(NativeGroundVehicle craft) { this.craft = craft; }

    boolean active() { return craft.getEntityData().get(NativeGroundVehicle.CRUISE_ROUTE).getY() == 1; }
    double altitude() { return craft.getY() - (active() ? SKY_OFFSET : 0); }
    double x() { return !active() ? craft.getX() : craft.level().isClientSide ? craft.getEntityData().get(NativeGroundVehicle.CRUISE_ROUTE).getX() : x; }
    double z() { return !active() ? craft.getZ() : craft.level().isClientSide ? craft.getEntityData().get(NativeGroundVehicle.CRUISE_ROUTE).getZ() : z; }

    boolean allowsTransit() { return craft.level().dimension() == Level.OVERWORLD; }

    void enter() {
        if (!(craft.level() instanceof ServerLevel) || !allowsTransit() || craft.getY() <= FlightEnvelope.WORLD_CEILING) return;
        x = craft.getX();
        z = craft.getZ();
        publishRoute();
        relocate(craft.getX(), craft.getY() + SKY_OFFSET, craft.getZ());
    }

    void move(Vec3 motion) {
        if (!(craft.level() instanceof ServerLevel level) || !active()) return;
        var border = level.getWorldBorder();
        double height = Math.min(FlightEnvelope.MAX_ALTITUDE, altitude() + motion.y);
        if (height <= FlightEnvelope.WORLD_CEILING) {
            arrive(level);
            return;
        }
        // Reserve room for the complete hull when the logical route approaches the world border.
        double margin = Math.min(48, border.getSize() * .2);
        x = Mth.clamp(x + motion.x, border.getMinX() + margin, border.getMaxX() - margin);
        z = Mth.clamp(z + motion.z, border.getMinZ() + margin, border.getMaxZ() - margin);
        if (craft.tickCount % 3 == 0) publishRoute();
        craft.setPos(craft.getX(), SKY_OFFSET + height, craft.getZ());
        updateTerrain(level, height);
    }

    private void updateTerrain(ServerLevel level, double altitude) {
        if (!(craft.getFirstPassenger() instanceof ServerPlayer pilot)) {
            pending = null;
            publishedStep = 0;
            return;
        }
        int size = NativeCruiseTerrain.SIZE;
        int step = Math.max(16, Integer.highestOneBit((int)altitude / 8));
        if (pending == null) {
            if (step == publishedStep && Math.abs(x - publishedX) < size * step / 5
                    && Math.abs(z - publishedZ) < size * step / 5 && craft.tickCount % 200 != 0) return;
            int centerX = Mth.floor(x / step) * step;
            int centerZ = Mth.floor(z / step) * step;
            pending = new NativeCruiseTerrain(craft.getId(), centerX, centerZ, step, new int[size * size]);
            nextPixel = 0;
        }
        int end = Math.min(nextPixel + 128, size * size);
        long began = System.nanoTime();
        do {
            int sampleX = pending.x() + (nextPixel % size - size / 2) * pending.step();
            int sampleZ = pending.z() + (nextPixel / size - size / 2) * pending.step();
            pending.pixels()[nextPixel++] = NativeNavigationRuntime.cruiseColor(level, sampleX, sampleZ, pending.step());
        } while (nextPixel < end && System.nanoTime() - began < 750000);
        if (nextPixel == size * size) {
            PacketDistributor.sendToPlayer(pilot, pending);
            publishedX = pending.x();
            publishedZ = pending.z();
            publishedStep = pending.step();
            pending = null;
        }
    }

    private void arrive(ServerLevel level) {
        var chunks = level.getChunkSource();
        var center = new ChunkPos(BlockPos.containing(x, level.getMinBuildHeight(), z));
        chunks.addRegionTicket(TicketType.POST_TELEPORT, center, DESTINATION_RADIUS, craft.getId());
        var offset = craft.getBoundingBox().move(x - craft.getX(), 0, z - craft.getZ());
        if (!NativeVehicleHull.loadedColumns(level, offset.inflate(1e-5))) return;
        double bottomOffset = Math.max(0, craft.getY() - craft.getBoundingBox().minY);
        craft.getEntityData().set(NativeGroundVehicle.CRUISE_ROUTE, BlockPos.ZERO);
        pending = null;
        publishedStep = 0;
        relocate(x, level.getMaxBuildHeight() + bottomOffset + .01, z);
    }

    private void publishRoute() {
        craft.getEntityData().set(NativeGroundVehicle.CRUISE_ROUTE, BlockPos.containing(x, 1, z));
    }

    private void relocate(double x, double y, double z) {
        craft.setPos(x, y, z);
        craft.setOldPosAndRot();
        for (var rider : craft.getPassengers()) {
            craft.positionRider(rider);
            if (rider instanceof ServerPlayer player) {
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
        }
    }

    void write(CompoundTag target) {
        if (!active()) return;
        var route = new CompoundTag();
        route.putDouble("X", x);
        route.putDouble("Z", z);
        target.put("Cruise", route);
    }

    record Route(double x, double z) {
        static Route read(CompoundTag vehicle) {
            if (!vehicle.contains("Cruise")) return null;
            if (!(vehicle.get("Cruise") instanceof CompoundTag route)
                    || !route.contains("X", Tag.TAG_DOUBLE) || !route.contains("Z", Tag.TAG_DOUBLE)) {
                throw new IllegalArgumentException("Malformed high-flight route");
            }
            double x = route.getDouble("X"), z = route.getDouble("Z");
            if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                throw new IllegalArgumentException("Invalid high-flight coordinates");
            }
            return new Route(x, z);
        }
    }

    void restore(Route state) {
        craft.getEntityData().set(NativeGroundVehicle.CRUISE_ROUTE, BlockPos.ZERO);
        if (state == null) return;
        double altitude = craft.getY() - SKY_OFFSET;
        if (!allowsTransit() || altitude <= FlightEnvelope.WORLD_CEILING || altitude > FlightEnvelope.MAX_ALTITUDE) {
            throw new IllegalArgumentException("Invalid high-flight saved altitude");
        }
        x = state.x;
        z = state.z;
        publishRoute();
    }
}
