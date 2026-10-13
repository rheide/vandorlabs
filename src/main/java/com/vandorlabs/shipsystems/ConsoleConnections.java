package com.vandorlabs.shipsystems;

import com.vandorlabs.blocks.BlockShipSystem;
import com.vandorlabs.tiles.TileEntityShipSystem;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Loaded Hadron console rows, grouped deterministically from the left. */
public final class ConsoleConnections {
    private static boolean matches(World w, BlockPos p, TileEntityShipSystem seed) {
        TileEntityShipSystem tile = TileEntityShipSystem.at(w, p);
        return tile != null && tile.isAnchor() && tile.block() == seed.block() && tile.mount == seed.mount
                && tile.facing() == seed.facing();
    }
    public static boolean refresh(World w, BlockPos seedPos) {
        if (w.isRemote) return false;
        TileEntityShipSystem seed = TileEntityShipSystem.at(w, seedPos);
        if (seed == null || !seed.isAnchor() || seed.block() == null || !seed.block().isConsole()) return false;
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
