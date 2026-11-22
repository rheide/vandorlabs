package com.vandorlabs.drives;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.MachineMountSurface;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Yellow and white afterburner particles follow the nozzle on terrain and aboard a craft. */
public final class DriveParticles {
    private DriveParticles() {}

    public static Vec3d center(TileEntityConstructor tile) {
        int width = tile.structureWidth(), depth = tile.structureDepth();
        Vec3d local = new Vec3d(width * .5, tile.structureHeight() * .55, depth - .05);
        return MachineMountSurface.rotate(local, tile.facing(), width, depth).add(new Vec3d(tile.getPos().subtract(tile.pivot())));
    }

    public static EnumFacing direction(TileEntityConstructor tile) { return tile.facing().getOpposite(); }

    public static void emit(TileEntityConstructor tile) {
        World world = tile.getWorld();
        if (world == null || !world.isRemote || !tile.renderReady() || tile.maxwellActiveLevel() != 15) return;
        EntityGroundVehicle craft = world instanceof VehicleWorld ? ((VehicleWorld) world).craft : null;
        if (world instanceof VehicleWorld && (craft == null || craft.ownershipBlocked || craft.transferInProgress
                || !craft.structure.isActive(tile.getPos()))) return;
        Vec3d point = center(tile);
        EnumFacing face = direction(tile);
        Vec3d direction = null;
        if (craft != null) {
            Vec3d origin = craft.toWorld(point, tile.getPos());
            direction = craft.toWorld(point.add(new Vec3d(face.getDirectionVec())), tile.getPos()).subtract(origin).normalize();
            point = origin;
            world = craft.world;
        }
        float spread = .95F * tile.structureWidth() / 3;
        for (int i = 0; i < 8; i++) {
            int color = i % 3 == 0 ? 0xFFFFFF : 0xFFE29A;
            if (craft == null) VandorLabs.proxy.spawnThrusterParticle(world, point.x, point.y, point.z, face, color, 0, .25F, spread);
            else VandorLabs.proxy.spawnPosedThrusterParticle(world, point, face, direction, color, .25F, spread);
        }
    }
}
