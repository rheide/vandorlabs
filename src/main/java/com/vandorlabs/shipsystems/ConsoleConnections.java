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
        // Pair back-to-back rows before partitioning the remaining same-facing cells.
        // A fixed orientation makes ownership independent of which member refreshed.
        EnumFacing canonical=seed.facing().getAxis()==EnumFacing.Axis.Z?EnumFacing.NORTH:EnumFacing.EAST;
        MountFrame canonicalFrame=new MountFrame(canonical,seed.mount);
        if(seed.mount.getAxis()==EnumFacing.Axis.Y) {
            List<BlockPos> ordered=new ArrayList<>(cells);
            ordered.sort(Comparator.comparingInt((BlockPos p)->along(p,canonicalFrame.back)).thenComparingInt(p->along(p,canonicalFrame.right)));
            for(BlockPos owner:ordered) {
                if(!opposedPair(w,cells,owner,canonical,canonicalFrame))continue;
                int width=1;
                while(width<3 && opposedPair(w,cells,owner.offset(canonicalFrame.right,width),canonical,canonicalFrame))width++;
                apply(w,new Rectangle(owner,width,2,seed.mount),canonical,true);
                for(int x=0;x<width;x++)for(int z=0;z<2;z++)cells.remove(canonicalFrame.cell(owner,x,0,z));
            }
        }
        for(EnumFacing facing:new EnumFacing[]{seed.facing(),seed.facing().getOpposite()}) {
            Set<BlockPos> aligned=new HashSet<>();
            for(BlockPos cell:cells)if(TileEntityShipSystem.at(w,cell).facing()==facing)aligned.add(cell);
            for(Rectangle rectangle:rectangles(aligned,facing,seed.mount))apply(w,rectangle,facing,false);
        }
        return true;
    }
    private static boolean opposedPair(World w,Set<BlockPos> cells,BlockPos front,EnumFacing facing,MountFrame frame) {
        BlockPos back=front.offset(frame.back);
        return cells.contains(front) && cells.contains(back) && TileEntityShipSystem.at(w,front).facing()==facing
                && TileEntityShipSystem.at(w,back).facing()==facing.getOpposite();
    }
    private static void apply(World w,Rectangle rectangle,EnumFacing facing,boolean opposed) {
        boolean powered=false;
        for(int z=0;z<rectangle.depth;z++)for(int x=0;x<rectangle.width;x++)
            powered |= TileEntityShipSystem.at(w,rectangle.cell(facing,x,z)).inputPowered();
        powered=TileEntityShipSystem.at(w,rectangle.owner).evaluatePower(powered);
        for(int z=0;z<rectangle.depth;z++)for(int x=0;x<rectangle.width;x++)
            TileEntityShipSystem.at(w,rectangle.cell(facing,x,z)).applyConsole(rectangle.owner,x,z,rectangle.width,rectangle.depth,powered,opposed);
    }
    private static boolean matches(World w, BlockPos p, TileEntityShipSystem seed) {
        TileEntityShipSystem tile = TileEntityShipSystem.at(w, p);
        return tile != null && tile.isAnchor() && tile.block() == seed.block() && tile.mount == seed.mount
                && (tile.facing() == seed.facing() || seed.block().kind.equals("vektor_console") && seed.mount.getAxis()==EnumFacing.Axis.Y && tile.facing()==seed.facing().getOpposite());
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
