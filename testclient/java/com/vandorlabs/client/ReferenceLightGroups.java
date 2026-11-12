package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Exact pre-cache topology path: one global map, discarded every client tick. */
final class ReferenceLightGroups {
    private static final Map<BlockPos,TEAnimatedScreenSelector.LightGroup> LIGHT_GROUPS=new HashMap<>();
    private static World lightCacheWorld;
    private static long lightCacheTick=Long.MIN_VALUE,lightCacheRevision=Long.MIN_VALUE;
    static void clear(){LIGHT_GROUPS.clear();lightCacheWorld=null;lightCacheTick=lightCacheRevision=Long.MIN_VALUE;}
    private static int axis(BlockPos pos,EnumFacing facing){return PanelPlane.axis(pos,facing);}
    static TEAnimatedScreenSelector.LightGroup lightGroup(com.vandorlabs.tiles.TileEntityProgrammableLight tile,
            IBlockState state) {
        World world = tile.getWorld();
        long tick = world.getTotalWorldTime();
        long revision = com.vandorlabs.tiles.TileEntityProgrammableLight.getJoinRevision();
        if (world != lightCacheWorld || tick != lightCacheTick
                || revision != lightCacheRevision) {
            LIGHT_GROUPS.clear();
            lightCacheWorld = world;
            lightCacheTick = tick;
            lightCacheRevision = revision;
        }
        TEAnimatedScreenSelector.LightGroup cached = LIGHT_GROUPS.get(tile.getPos());
        if (cached != null) return cached;
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        PanelPlane plane = PanelPlane.of(facing);
        EnumFacing right = plane.right;
        EnumFacing up = plane.up;
        if (!tile.isJoin()) {
            TEAnimatedScreenSelector.LightGroup group = new TEAnimatedScreenSelector.LightGroup(axis(tile.getPos(), right), axis(tile.getPos(), up),
                    1, 1, right, up);
            LIGHT_GROUPS.put(tile.getPos(), group);
            return group;
        }
        Set<BlockPos> members = com.vandorlabs.blocks.ProgrammableLightConnections
                .members(tile, state);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos pos : members) {
            minX = Math.min(minX, axis(pos, right)); maxX = Math.max(maxX, axis(pos, right));
            minY = Math.min(minY, axis(pos, up)); maxY = Math.max(maxY, axis(pos, up));
        }
        if ((long)(maxX-minX+1)*(maxY-minY+1) == members.size()) {
            TEAnimatedScreenSelector.LightGroup group = new TEAnimatedScreenSelector.LightGroup(minX, minY, maxX-minX+1, maxY-minY+1, right, up);
            for (BlockPos pos : members) LIGHT_GROUPS.put(pos, group);
            return group;
        }
        Map<Long, BlockPos> positions = new HashMap<>();
        for (BlockPos pos : members)
            positions.put(PortholeRectangles.cell(axis(pos, right), axis(pos, up)), pos);
        for (PortholeRectangles.Rect rect : PortholeRectangles.partition(positions.keySet())) {
            TEAnimatedScreenSelector.LightGroup group = new TEAnimatedScreenSelector.LightGroup(rect.x, rect.y, rect.width, rect.height,
                    right, up);
            for (int row = rect.y; row < rect.y + rect.height; row++)
                for (int col = rect.x; col < rect.x + rect.width; col++)
                    LIGHT_GROUPS.put(positions.get(PortholeRectangles.cell(col, row)), group);
        }
        return LIGHT_GROUPS.get(tile.getPos());
    }

    static int visibleFaces(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,EnumFacing facing) {
        if(tile.isSmallInput())return 63;
        int visible=0;
        for(EnumFacing local:EnumFacing.values()) {
            EnumFacing worldSide=TEAnimatedScreenSelector.lightWorldSide(facing,local);
            BlockPos neighbor=tile.getPos().offset(worldSide);
            if(!tile.getWorld().isBlockLoaded(neighbor)
                    || tile.getWorld().getTileEntity(neighbor) instanceof TileEntityProgrammableLight && ((TileEntityProgrammableLight)tile.getWorld().getTileEntity(neighbor)).isSmallInput()
                    || tile.getWorld().getBlockState(neighbor).getBlock()!=tile.getWorld().getBlockState(tile.getPos()).getBlock()
                    || tile.getWorld().getBlockState(neighbor).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableLightSlab
                    && tile.getWorld().getBlockState(neighbor).getValue(com.vandorlabs.blocks.BlockProgrammableLightSlab.HALF)
                    !=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockProgrammableLightSlab.HALF))
                visible |= 1 << local.getIndex();
        }
        return visible;
    }

}
