package com.vandorlabs.turntable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The captured blocks above, below or beside a Turntable. Positions stay in
 * world coordinates so the renderer can rotate them about the platform pivot.
 *
 * The wire form is a palette plus per-cell positions, matching the compact
 * encoding the vehicle transfers already use so large payloads stay bounded.
 */
public final class TurntableSnapshot {
    public static final int SCHEMA = 1;
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    public static final class Cell {
        public final BlockPos pos;
        public final IBlockState state;
        private final NBTTagCompound tile;

        public Cell(BlockPos pos, IBlockState state, NBTTagCompound tile) {
            this.pos = pos.toImmutable();
            this.state = state;
            this.tile = tile == null ? null : tile.copy();
        }

        public NBTTagCompound tileData() { return tile == null ? null : tile.copy(); }
        public boolean hasTileData() { return tile != null; }
    }

    public final List<Cell> cells;

    public TurntableSnapshot(List<Cell> cells) {
        if (cells.size() > TurntableGeometry.MAX_PAYLOAD) {
            throw new IllegalArgumentException("Turntable payload is too large");
        }
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
    }

    public boolean isEmpty() { return cells.isEmpty(); }

    public NBTTagCompound write() {
        NBTTagCompound out = new NBTTagCompound();
        out.setInteger("Schema", SCHEMA);
        Map<IBlockState, Integer> palette = new LinkedHashMap<>();
        NBTTagList states = new NBTTagList(), blocks = new NBTTagList();
        for (Cell cell : cells) {
            Integer id = palette.get(cell.state);
            if (id == null) {
                id = palette.size();
                palette.put(cell.state, id);
                states.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), cell.state));
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Pos", cell.pos.toLong());
            tag.setInteger("State", id);
            if (cell.tile != null) tag.setTag("Tile", cell.tile.copy());
            blocks.appendTag(tag);
        }
        out.setTag("Palette", states);
        out.setTag("Blocks", blocks);
        return out;
    }

    public static TurntableSnapshot read(NBTTagCompound tag) {
        if (tag.getInteger("Schema") != SCHEMA) throw new IllegalArgumentException("Unsupported turntable schema");
        NBTTagList palette = tag.getTagList("Palette", 10), blocks = tag.getTagList("Blocks", 10);
        if (blocks.tagCount() > TurntableGeometry.MAX_PAYLOAD
                || palette.tagCount() > TurntableGeometry.MAX_PAYLOAD) {
            throw new IllegalArgumentException("Oversize turntable payload");
        }
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound entry = blocks.getCompoundTagAt(i);
            int state = entry.getInteger("State");
            if (state < 0 || state >= palette.tagCount()) throw new IllegalArgumentException("Invalid palette index");
            NBTTagCompound saved = palette.getCompoundTagAt(state);
            ResourceLocation name = new ResourceLocation(saved.getString("Name"));
            if (!Block.REGISTRY.containsKey(name)) throw new IllegalArgumentException("Missing payload block " + name);
            IBlockState parsed = NBTUtil.readBlockState(saved);
            if (parsed.getBlock() == Blocks.AIR) throw new IllegalArgumentException("Air in turntable payload");
            cells.add(new Cell(BlockPos.fromLong(entry.getLong("Pos")), parsed,
                    entry.hasKey("Tile", 10) ? entry.getCompoundTag("Tile") : null));
        }
        return new TurntableSnapshot(cells);
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(new FilterOutputStream(bytes) {
            private int size;
            @Override public void write(int value) throws IOException {
                if (++size > MAX_BYTES) throw new IOException("Turntable payload exceeds 4 MiB");
                out.write(value);
            }
            @Override public void write(byte[] data, int offset, int length) throws IOException {
                size += length;
                if (size > MAX_BYTES) throw new IOException("Turntable payload exceeds 4 MiB");
                out.write(data, offset, length);
            }
        });
        CompressedStreamTools.write(write(), output);
        output.flush();
        return bytes.toByteArray();
    }

    public static TurntableSnapshot decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Oversize turntable payload");
        return read(CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),
                new NBTSizeTracker(MAX_BYTES * 4L)));
    }
}
