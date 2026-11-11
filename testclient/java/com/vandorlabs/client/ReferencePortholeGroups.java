package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.client.TEAnimatedScreenSelector.PortholeGroup;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Frozen e8cc78a1 discovery and per-tick cache for geometry and operation-count comparison. */
final class ReferencePortholeGroups {
    private static final Map<BlockPos, PortholeGroup> PORTHOLE_GROUPS = new HashMap<>();
    private static World portholeCacheWorld;
    private static long portholeCacheTick = Long.MIN_VALUE, portholeCacheRevision = Long.MIN_VALUE;
    private static int axis(BlockPos pos,EnumFacing facing){return PanelPlane.axis(pos,facing);}
    static void clear(){PORTHOLE_GROUPS.clear();portholeCacheWorld=null;portholeCacheTick=Long.MIN_VALUE;portholeCacheRevision=Long.MIN_VALUE;}
    static PortholeGroup portholeGroup(TileEntityAnimatedScreenSelector tile,
            IBlockState state) {
        World world = tile.getWorld();
        long tick = world.getTotalWorldTime();
        long revision = TileEntityAnimatedScreenSelector.getPortholeRevision();
        if (world != portholeCacheWorld || tick != portholeCacheTick
                || revision != portholeCacheRevision) {
            PORTHOLE_GROUPS.clear();
            portholeCacheWorld = world;
            portholeCacheTick = tick;
            portholeCacheRevision = revision;
        }
        PortholeGroup cached = PORTHOLE_GROUPS.get(tile.getPos());
        if (cached != null) return cached;
        EnumFacing facing = state.getValue(BlockProgrammableWall.FACING);
        EnumFacing right = facing.rotateY();
        boolean shallow = ((BlockProgrammableWall) state.getBlock()).isDiagonalShape()
                && tile.isDiagonalHalfHeight();
        EnumFacing up = shallow ? facing.getOpposite() : EnumFacing.UP;
        int extraBorder = state.getBlock() instanceof BlockProgrammablePortholeBlock ? 1 : 0;
        Set<BlockPos> members = tile.isJoinPortholes()
                ? (((BlockProgrammableWall)state.getBlock()).isDiagonalShape()
                        ? collectDiagonalPortholes(world,tile.getPos())
                        : LoadedPlaneConnections.collect(tile.getPos(),PanelPlane.of(facing),
                                world::isBlockLoaded,next -> compatiblePorthole(world,tile.getPos(),next)))
                : java.util.Collections.singleton(tile.getPos());
        // Every member must select the same basis, including opposite-facing shallow rows.
        BlockPos first=java.util.Collections.min(members);
        facing=world.getBlockState(first).getValue(BlockProgrammableWall.FACING);
        right=facing.rotateY();up=shallow?facing.getOpposite():EnumFacing.UP;
        int minimum=axis(first,right),maximum=minimum,minY=axis(first,up),maxY=minY;
        for (BlockPos member : members) {
            int coordinate = axis(member, right);
            minimum = Math.min(minimum, coordinate); maximum = Math.max(maximum, coordinate);
            minY = Math.min(minY, axis(member, up)); maxY = Math.max(maxY, axis(member, up));
        }
        boolean capped = members.size() >= LoadedPlaneConnections.LIMIT;
        if (capped) {
            for (BlockPos pos : members) PORTHOLE_GROUPS.put(pos,
                    new PortholeGroup(axis(pos, right), axis(pos, up), 1, 1, right, up,
                            tile.getPortholeShape(), extraBorder));
            return PORTHOLE_GROUPS.get(tile.getPos());
        }
        if ((long) (maximum - minimum + 1) * (maxY - minY + 1) != members.size()) {
            Map<Long, BlockPos> positions = new HashMap<>();
            for (BlockPos pos : members)
                positions.put(PortholeRectangles.cell(axis(pos, right), axis(pos, up)), pos);
            for (PortholeRectangles.Rect rect : PortholeRectangles.partition(positions.keySet())) {
                PortholeGroup part = new PortholeGroup(rect.x, rect.y,
                        rect.width, rect.height, right, up, tile.getPortholeShape(),
                        extraBorder);
                for (int row = rect.y; row < rect.y + rect.height; row++)
                    for (int column = rect.x; column < rect.x + rect.width; column++)
                        PORTHOLE_GROUPS.put(positions.get(PortholeRectangles.cell(column, row)), part);
            }
            return PORTHOLE_GROUPS.get(tile.getPos());
        }
        PortholeGroup group = new PortholeGroup(minimum, minY,
                maximum - minimum + 1, maxY - minY + 1, right, up,
                tile.getPortholeShape(), extraBorder);
        for (BlockPos pos : members) PORTHOLE_GROUPS.put(pos, group);
        return group;
    }

    private static boolean compatiblePorthole(World world,BlockPos root,BlockPos next) {
        if(!world.isBlockLoaded(next))return false;
        IBlockState a=world.getBlockState(root),b=world.getBlockState(next);
        if(a.getBlock()!=b.getBlock() || !(a.getBlock() instanceof BlockProgrammableWall))return false;
        net.minecraft.tileentity.TileEntity ar=world.getTileEntity(root),br=world.getTileEntity(next);
        if(!(ar instanceof TileEntityAnimatedScreenSelector)||!(br instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector at=(TileEntityAnimatedScreenSelector)ar,bt=(TileEntityAnimatedScreenSelector)br;
        if(!at.isJoinPortholes()||!bt.isJoinPortholes()||at.getPortholeShape()!=bt.getPortholeShape())return false;
        if(TEAnimatedScreenSelector.solidDiagonalPortholeJunction(at,a) || TEAnimatedScreenSelector.solidDiagonalPortholeJunction(bt,b))return false;
        EnumFacing af=a.getValue(BlockProgrammableWall.FACING),bf=b.getValue(BlockProgrammableWall.FACING);
        if(!((BlockProgrammableWall)a.getBlock()).isDiagonalShape())return af==bf && a.getValue(BlockProgrammableWall.DEPTH).equals(b.getValue(BlockProgrammableWall.DEPTH));
        if(at.isDiagonalHalfHeight()!=bt.isDiagonalHalfHeight()||at.isDiagonalFullWidth()!=bt.isDiagonalFullWidth())return false;
        int mode=at.isDiagonalHalfHeight()?2:at.isDiagonalFullWidth()?1:0;
        return com.vandorlabs.blocks.DiagonalPanelGeometry.samePlane(root,a,next,b,mode)
                || com.vandorlabs.blocks.DiagonalPanelGeometry.rowsMeet(root,a,next,b,mode);
    }

    private static Set<BlockPos> collectDiagonalPortholes(World world,BlockPos root) {
        Set<BlockPos> members=new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<BlockPos> queue=new java.util.ArrayDeque<>();
        members.add(root);queue.add(root);
        while(!queue.isEmpty() && members.size()<LoadedPlaneConnections.LIMIT) {
            BlockPos pos=queue.removeFirst();IBlockState state=world.getBlockState(pos);
            int mode=BlockProgrammableWall.geometry(world,pos);
            java.util.List<BlockPos> candidates=new java.util.ArrayList<>();
            EnumFacing right=state.getValue(BlockProgrammableWall.FACING).rotateY();
            candidates.add(pos.offset(right));candidates.add(pos.offset(right.getOpposite()));
            for(boolean positive:new boolean[]{false,true})candidates.addAll(
                    com.vandorlabs.blocks.DiagonalPanelGeometry.rowCandidates(pos,state,mode,positive));
            for(BlockPos next:candidates) {
                if(members.contains(next)||!compatiblePorthole(world,pos,next))continue;
                members.add(next);queue.add(next);
                if(members.size()>=LoadedPlaneConnections.LIMIT)break;
            }
        }
        return members;
    }

    private ReferencePortholeGroups(){}
}
