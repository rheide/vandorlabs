package com.vandorlabs.shipsystems;

import com.vandorlabs.blocks.BlockShipSystem;
import com.vandorlabs.tiles.TileEntityShipSystem;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Loaded Hadron rows and Vektor rectangles, grouped deterministically from the front left. */
public final class ConsoleConnections {
    public static final class Rectangle {
        public final BlockPos owner;
        public final int width, depth;
        public final EnumFacing mount;
        Rectangle(BlockPos owner, int width, int depth,EnumFacing mount) { this.owner=owner; this.width=width; this.depth=depth; this.mount=mount; }
        public BlockPos cell(EnumFacing facing, int x, int z) {
            return new MountFrame(facing,mount).cell(owner,x,0,z);
        }
    }
    /** Partition filled cells only: gaps and incomplete rows never reserve empty space. */
    public static List<Rectangle> rectangles(Set<BlockPos> cells, EnumFacing facing) {
        return rectangles(cells,facing,EnumFacing.UP);
    }
    private static int along(BlockPos p,EnumFacing direction) {
        return p.getX()*direction.getFrontOffsetX()+p.getY()*direction.getFrontOffsetY()+p.getZ()*direction.getFrontOffsetZ();
    }
    public static List<Rectangle> rectangles(Set<BlockPos> cells, EnumFacing facing,EnumFacing mount) {
        MountFrame frame=new MountFrame(facing,mount);
        EnumFacing right=frame.right,back=frame.back;
        Comparator<BlockPos> order=Comparator.comparingInt((BlockPos p)->along(p,back)).thenComparingInt(p->along(p,right));
        Set<BlockPos> remaining=new TreeSet<>(order);
        remaining.addAll(cells);
        List<Rectangle> result=new ArrayList<>();
        while(!remaining.isEmpty()) {
            BlockPos owner=remaining.iterator().next();
            int width=1,depth=1;
            while(width<3 && remaining.contains(owner.offset(right,width))) width++;
            while(depth<3) {
                boolean full=true;
                for(int x=0;x<width;x++)full &= remaining.contains(owner.offset(right,x).offset(back,depth));
                if(!full)break;
                depth++;
            }
            Rectangle rectangle=new Rectangle(owner,width,depth,mount);
            result.add(rectangle);
            for(int z=0;z<depth;z++)for(int x=0;x<width;x++)remaining.remove(rectangle.cell(facing,x,z));
        }
        return result;
    }

    private static boolean refreshRectangle(World w, BlockPos pos, TileEntityShipSystem seed) {
        Set<BlockPos> cells=new HashSet<>();
        ArrayDeque<BlockPos> pending=new ArrayDeque<>();
        cells.add(pos);pending.add(pos);
        while(!pending.isEmpty()) {
            BlockPos cell=pending.remove();
            if(!TileEntityShipSystem.at(w,cell).formed())return false;
            MountFrame frame=seed.frame();
            for(EnumFacing side:new EnumFacing[]{frame.right,frame.right.getOpposite(),frame.back,frame.back.getOpposite()}) {
                BlockPos next=cell.offset(side);
                if(!w.isBlockLoaded(next))return false;
                if(matches(w,next,seed) && cells.add(next))pending.add(next);
            }
        }
        EnumFacing facing=seed.facing();
        for(Rectangle rectangle:rectangles(cells,facing,seed.mount)) {
            boolean powered=false;
            for(int z=0;z<rectangle.depth;z++)for(int x=0;x<rectangle.width;x++)
                powered |= TileEntityShipSystem.at(w,rectangle.cell(facing,x,z)).inputPowered();
            powered=TileEntityShipSystem.at(w,rectangle.owner).evaluatePower(powered);
            for(int z=0;z<rectangle.depth;z++)for(int x=0;x<rectangle.width;x++)
                TileEntityShipSystem.at(w,rectangle.cell(facing,x,z)).applyConsole(rectangle.owner,x,z,rectangle.width,rectangle.depth,powered);
        }
        return true;
    }
    private static boolean matches(World w, BlockPos p, TileEntityShipSystem seed) {
        TileEntityShipSystem tile = TileEntityShipSystem.at(w, p);
        return tile != null && tile.isAnchor() && tile.block() == seed.block() && tile.facing() == seed.facing() && tile.mount == seed.mount;
    }
    public static boolean refresh(World w, BlockPos seedPos) {
        if (w.isRemote) return false;
        TileEntityShipSystem seed = TileEntityShipSystem.at(w, seedPos);
        if (seed == null || !seed.isAnchor() || seed.block() == null || !seed.block().isConsole()) return false;
        if(seed.block().kind.equals("vektor_console"))return refreshRectangle(w,seedPos,seed);
        EnumFacing right = seed.frame().right;
        BlockPos left = seedPos;
        while (true) {
            BlockPos next = left.offset(right.getOpposite());
            if (!w.isBlockLoaded(next)) return false;
            if (!matches(w, next, seed)) break;
            left = next;
        }
        List<BlockPos> row = new ArrayList<>();
        for (BlockPos p = left; ; p = p.offset(right)) {
            if (!w.isBlockLoaded(p)) return false;
            if (!matches(w, p, seed)) break;
            // Hadron reserves the upper cell as part of each individual console.
            for (BlockPos cell : seed.block().cells(p, seed.facing(),seed.mount)) {
                if (!w.isBlockLoaded(cell)) return false;
                TileEntityShipSystem member = TileEntityShipSystem.at(w, cell);
                if (member == null || !p.equals(member.anchor)) return false;
            }
            row.add(p);
        }
        for (int start = 0; start < row.size(); start += 3) {
            int width = Math.min(3, row.size() - start);
            boolean powered = false;
            for (int i = 0; i < width; i++) powered |= TileEntityShipSystem.at(w, row.get(start+i)).inputPowered();
            powered = TileEntityShipSystem.at(w, row.get(start)).evaluatePower(powered);
            for (int i = 0; i < width; i++) TileEntityShipSystem.at(w, row.get(start+i)).applyConsole(row.get(start), i, width, powered);
        }
        return true;
    }
    public static boolean poweredAny(World w, Iterable<BlockPos> cells) {
        for (BlockPos p : cells) if (w.isBlockLoaded(p) && w.isBlockPowered(p)) return true;
        return false;
    }
    private ConsoleConnections() {}
}
