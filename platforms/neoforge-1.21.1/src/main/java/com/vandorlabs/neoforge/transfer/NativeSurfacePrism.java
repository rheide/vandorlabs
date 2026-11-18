package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import com.vandorlabs.surface.core.SurfaceLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Loaded-world adapter for the shared integral Surface prism. */
public record NativeSurfacePrism(BlockPos min, BlockPos max) {
    public NativeSurfacePrism {
        min = min.immutable();
        max = max.immutable();
        if (span(min.getX(), max.getX()) > SurfaceLayout.MAX_SIZE
                || span(min.getY(), max.getY()) > SurfaceLayout.MAX_SIZE
                || span(min.getZ(), max.getZ()) > SurfaceLayout.MAX_SIZE) {
            throw new IllegalArgumentException("Surface prism dimensions");
        }
    }

    private static long span(int min, int max) {
        if (min > max) throw new IllegalArgumentException("Reversed Surface prism");
        return (long) max - min + 1;
    }

    public static NativeSurfacePrism of(NativeSurfaceLayout layout, int height) {
        var surface = layout.geometry;
        int[] bounds = SurfaceLayout.volume(surface.face, surface.minU, surface.minV, surface.plane,
                surface.width, surface.height, height);
        return new NativeSurfacePrism(new BlockPos(bounds[0], bounds[1], bounds[2]),
                new BlockPos(bounds[3], bounds[4], bounds[5]));
    }

    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1D, max.getY() + 1D, max.getZ() + 1D);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public List<BlockPos> cells() {
        var positions = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(min, max)) positions.add(pos.immutable());
        return List.copyOf(positions);
    }

    public void requireLoaded(ServerLevel world) {
        if (world.isOutsideBuildHeight(min) || world.isOutsideBuildHeight(max)
                || !world.getWorldBorder().isWithinBounds(min) || !world.getWorldBorder().isWithinBounds(max)) {
            throw new IllegalArgumentException("Surface prism must be inside the world");
        }
        for (int x = (min.getX() - 1) >> 4; x <= (max.getX() + 1) >> 4; x++) {
            for (int z = (min.getZ() - 1) >> 4; z <= (max.getZ() + 1) >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x, z) == null) {
                    throw new IllegalArgumentException("Load the complete Surface prism and its boundaries");
                }
            }
        }
    }
}
