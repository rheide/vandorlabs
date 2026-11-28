package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityDisplayArm;
import com.vandorlabs.world.LoadedTileIndex;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Bounded queries find posed parts outside their owning voxel without loading chunks. */
@Mod.EventBusSubscriber(modid = "vandorlabs")
public final class DisplayArmInteractions {
    public static final LoadedTileIndex<TileEntityDisplayArm> INDEX = new LoadedTileIndex<>(TileEntityDisplayArm.class);
    // Maximum 32px offset plus the rotated screen/deck diagonal, in blocks.
    public static final double EXTENT = 4;
    private DisplayArmInteractions() { }

    public static RayTraceResult trace(World world, Vec3d start, Vec3d end) {
        AxisAlignedBB search = new AxisAlignedBB(start.x, start.y, start.z, end.x, end.y, end.z).grow(EXTENT);
        RayTraceResult nearest = null;
        for (TileEntityDisplayArm tile : INDEX.query(world, search)) {
            RayTraceResult hit = tile.shape().trace(tile.getPos(), start, end);
            if (hit != null && (nearest == null || start.squareDistanceTo(hit.hitVec)
                    < start.squareDistanceTo(nearest.hitVec))) nearest = hit;
        }
        return nearest;
    }

    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event) {
        for (TileEntityDisplayArm tile : INDEX.query(event.getWorld(), event.getAabb().grow(EXTENT)))
            tile.shape().addCollisions(tile.getPos(), event.getAabb(), event.getCollisionBoxesList());
    }
}
