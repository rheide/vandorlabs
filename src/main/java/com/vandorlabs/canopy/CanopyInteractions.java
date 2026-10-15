package com.vandorlabs.canopy;

import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/** Only inspect loaded chunk tile maps; moving shells may occupy air cells. */
public final class CanopyInteractions {
    public static double distanceSq(Vec3d point,AxisAlignedBB box){double x=MathHelper.clamp(point.x,box.minX,box.maxX),y=MathHelper.clamp(point.y,box.minY,box.maxY),z=MathHelper.clamp(point.z,box.minZ,box.maxZ);return point.squareDistanceTo(new Vec3d(x,y,z));}

    public static List<TileEntityCanopy> owners(World world, AxisAlignedBB region) {
        List<TileEntityCanopy> found = new ArrayList<>();
        AxisAlignedBB query = region.grow(12);
        for (int x = MathHelper.floor(query.minX) >> 4; x <= MathHelper.floor(query.maxX) >> 4; x++)
            for (int z = MathHelper.floor(query.minZ) >> 4;
                    z <= MathHelper.floor(query.maxZ) >> 4;
                    z++) {
                Chunk c = world.getChunkProvider().getLoadedChunk(x, z);
                if (c == null) continue;
                for (TileEntity t : c.getTileEntityMap().values())
                    if (t instanceof TileEntityCanopy
                            && query.intersects(new AxisAlignedBB(t.getPos()))
                            && ((TileEntityCanopy) t).draws()) found.add((TileEntityCanopy) t);
            }
        return found;
    }

    /** A surface ray addresses its component; broad windows remain their own placement barrier. */
    @SubscribeEvent
    public void blockPlacement(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        net.minecraft.item.Item item=event.getItemStack().getItem();
        if(!(item instanceof net.minecraft.item.ItemBlock) || item instanceof com.vandorlabs.blocks.BlockCanopy.CanopyItem)return;
        TileEntityCanopy tile=TileEntityCanopy.at(event.getWorld(),event.getPos());
        if(tile!=null && tile.owner().resizable())event.setUseItem(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
    }

    @SubscribeEvent
    public void collision(GetCollisionBoxesEvent event) {
        for (TileEntityCanopy t : owners(event.getWorld(), event.getAabb()))
            for (AxisAlignedBB box : t.boxes())
                if (box.intersects(event.getAabb())) event.getCollisionBoxesList().add(box);
    }

    public static RayTraceResult trace(World world, Vec3d start, Vec3d end) {
        RayTraceResult best = null;
        for (TileEntityCanopy t : owners(world, new AxisAlignedBB(start, end))) {
            RayTraceResult hit = t.trace(start, end);
            if (hit != null
                    && (best == null
                            || start.squareDistanceTo(hit.hitVec)
                                    < start.squareDistanceTo(best.hitVec))) best = hit;
        }
        return best;
    }
}
