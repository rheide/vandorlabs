package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.Collections;
import java.util.function.Consumer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.*;

/** Packet deduplication must retain every visual dependency and tile replacement boundary. */
public final class GlassPacketUpdateChecks {
    private static final BlockPos POS = new BlockPos(8, 80, 8);
    private static BlockProgrammableGlass glass;
    private static int frame, screen;
    private static final CountingProxy PROXY = new CountingProxy();
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int lights;
        World() { super(true); }
        @Override public boolean checkLight(BlockPos pos) { lights++; return true; }
        void reset() { renderUpdates = updates = dirty = lights = 0; PROXY.frames = 0; }
    }
    private static final class CountingProxy extends CommonProxy {
        int frames;
        @Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile) {
            net.minecraft.world.World physical = tile.getWorld();
            while (physical instanceof VehicleWorld) physical = ((VehicleWorld) physical).parent;
            if (physical == null || !physical.isRemote) return;
            frames++;
            GlassSurfaceGroups.changed(tile);
        }
        @Override public void glassLightRefreshNeeded(TileEntityProgrammableGlass tile) {
            if (tile.getWorld().isRemote) GlassLightUpdates.schedule(tile);
            else super.glassLightRefreshNeeded(tile);
        }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static NBTTagCompound defaults(BlockPos pos) {
        TileEntityProgrammableGlass tile = new TileEntityProgrammableGlass();
        tile.setPos(pos);
        return tile.getUpdateTag();
    }
    private static TileEntityProgrammableGlass install(World world, BlockPos pos) {
        TileEntityProgrammableGlass tile = new TileEntityProgrammableGlass();
        tile.setWorld(world); tile.setPos(pos);
        world.states.put(pos, glass.getDefaultState()); world.tiles.put(pos, tile);
        return tile;
    }
    private static void packet(TileEntityProgrammableGlass tile, NBTTagCompound tag) {
        tile.onDataPacket(null, new SPacketUpdateTileEntity(tile.getPos(), 0, tag.copy()));
    }
    /** Previous unconditional packet policy, retained as a work-count negative control. */
    private static void unconditionalPacket(TileEntityProgrammableGlass tile, NBTTagCompound tag) {
        tile.readFromNBT(tag.copy());
        tile.getWorld().markBlockRangeForRenderUpdate(tile.getPos().add(-1, -1, -1), tile.getPos().add(1, 1, 1));
        tile.refreshPendingLight();
    }
    private static void duplicates() {
        World world = new World(); TileEntityProgrammableGlass tile = install(world, POS);
        NBTTagCompound tag = defaults(POS);
        try {
            world.reset(); packet(tile, tag);
            need(PROXY.frames == 1 && world.renderUpdates == 1, "first default-valued packet was suppressed");
            GlassSurfaceGroups.flatCorner(tile, glass.getDefaultState());
            GlassSurfaceGroups.Layout layout = GlassSurfaceGroups.layout(tile);
            long mutation = GlassSurfaceGroups.mutation();
            world.reset();
            for (int i = 0; i < 1000; i++) {
                if ((i & 1) == 0) packet(tile, tag); else tile.handleUpdateTag(tag.copy());
            }
            need(PROXY.frames == 0 && world.renderUpdates == 0 && world.lights == 0, "duplicate update did render/light work");
            need(GlassSurfaceGroups.mutation() == mutation && GlassSurfaceGroups.layout(tile) == layout
                    && GlassSurfaceGroups.flatCached(tile, glass.getDefaultState()), "duplicate expired retained topology/layout");
            // Different raw tags with identical decoded settings remain equivalent.
            NBTTagCompound equivalent = tag.copy(); equivalent.removeTag("GlassTextureLayout");
            equivalent.setInteger("GlassShade", -1); equivalent.setInteger("FrameFront", -999);
            equivalent.setByte("GlassPlaybackMode", (byte) 99);
            packet(tile, equivalent);
            need(PROXY.frames == 0 && world.renderUpdates == 0, "normalized equivalent settings invalidated geometry");
        } finally { GlassSurfaceGroups.unload(world); }
        World oldWorld = new World(); TileEntityProgrammableGlass old = install(oldWorld, POS);
        try {
            packet(old, tag); oldWorld.reset();
            for (int i = 0; i < 1000; i++) unconditionalPacket(old, tag);
            need(PROXY.frames == 1000 && oldWorld.renderUpdates == 1000, "unconditional negative control did not reproduce cost");
            System.out.println("glass-packet-duplicates 1000 updates: topology notifications before=" + PROXY.frames
                    + " after=0; neighbor render ranges before=" + oldWorld.renderUpdates + " after=0");
        } finally { GlassSurfaceGroups.unload(oldWorld); }
    }
    private static void changedField(String name, Consumer<NBTTagCompound> change) {
        World world = new World(); TileEntityProgrammableGlass tile = install(world, POS);
        NBTTagCompound tag = defaults(POS);
        try {
            packet(tile, tag); world.reset(); change.accept(tag); packet(tile, tag);
            need(PROXY.frames == 1 && world.renderUpdates >= 1, "changed field was suppressed: " + name);
            world.reset(); packet(tile, tag);
            need(PROXY.frames == 0 && world.renderUpdates == 0 && world.lights == 0, "repeat changed field invalidated again: " + name);
        } finally { GlassSurfaceGroups.unload(world); }
    }
    private static void visualFields() {
        String[] ints = {"GlassShade", "GlassMaterial", "GlassTextureLayout", "GlassTint", "GlassSize", "GlassPanelFacing",
                "DisplayArtworkVersion", "FrameFront", "FrameInterior", "FrameExterior", "DisplaySplitTop", "DisplaySplitBottom"};
        int[] values = {1, screen, 0, 2, 0, EnumFacing.NORTH.getHorizontalIndex(), 1, frame, frame, frame, frame, frame};
        for (int i = 0; i < ints.length; i++) { final int value = values[i]; final String key = ints[i]; changedField(key, tag -> tag.setInteger(key, value)); }
        for (String key : new String[]{"GlassStretch", "GlassFramed", "GlassJoin", "FrameTile", "DisplayFit", "DisplaySplit", "FrameBare"})
            changedField(key, tag -> tag.setBoolean(key, !tag.getBoolean(key)));
        changedField("GlassPlaybackMode", tag -> tag.setByte("GlassPlaybackMode", (byte) 0));
        changedField("GlassPlaybackSpeed", tag -> tag.setByte("GlassPlaybackSpeed", (byte) 2));
        changedField("legacy size migration", tag -> tag.removeTag("GlassSize"));
    }
    private static void playbackAndFluid() {
        World world = new World(); TileEntityProgrammableGlass left = install(world, POS), right = install(world, POS.east());
        NBTTagCompound leftTag = defaults(POS), rightTag = defaults(POS.east());
        leftTag.setInteger("GlassMaterial", screen); rightTag.setInteger("GlassMaterial", screen);
        try {
            packet(left, leftTag); packet(right, rightTag);
            need(GlassSurfaceGroups.layout(left).columns == 2, "joined screen-material fixture");
            rightTag.setByte("GlassPlaybackMode", (byte) 0); packet(right, rightTag);
            need(GlassSurfaceGroups.layout(left).columns == 1, "playback change left incompatible panes joined");
            GlassSurfaceGroups.Layout split = GlassSurfaceGroups.layout(left); world.reset(); packet(right, rightTag);
            need(PROXY.frames == 0 && world.renderUpdates == 0 && GlassSurfaceGroups.layout(left) == split, "repeated playback packet expired layout");
            FluidStack water = new FluidStack(FluidRegistry.WATER, 1000); water.tag = new NBTTagCompound();
            water.tag.setInteger("StoredColor", 0xff123456);
            NBTTagCompound contents = new NBTTagCompound(); contents.setTag("Side0", water.writeToNBT(new NBTTagCompound()));
            leftTag.setTag("ContainedFluids", contents); world.reset(); packet(left, leftTag);
            need(PROXY.frames == 1 && world.renderUpdates > 0 && left.fluids.get(0).isFluidStackIdentical(water), "fluid-only update lost data/invalidation");
            world.reset(); packet(left, leftTag);
            need(PROXY.frames == 0 && world.renderUpdates == 0, "duplicate full fluid NBT invalidated geometry");
            contents.getCompoundTag("Side0").getCompoundTag("Tag").setInteger("StoredColor", 0xffabcdef);
            world.reset(); packet(left, leftTag);
            need(PROXY.frames == 1 && world.renderUpdates > 0, "fluid NBT-only change was suppressed");
            leftTag.removeTag("ContainedFluids"); world.reset(); packet(left, leftTag);
            need(!left.fluids.any() && PROXY.frames == 1 && world.renderUpdates > 0, "fluid removal was suppressed");
        } finally { GlassSurfaceGroups.unload(world); }
    }
    private static void pendingLight() {
        World world = new World(); TileEntityProgrammableGlass tile = install(world, POS);
        NBTTagCompound tag = defaults(POS); packet(tile, tag);
        try {
            tag.setInteger("FrameFront", frame); world.reset(); tile.handleUpdateTag(tag.copy());
            need(world.lights == 0, "chunk-tag light propagation was eager");
            world.reset(); packet(tile, tag);
            need(PROXY.frames == 0 && world.renderUpdates == 0 && world.lights == 1, "duplicate packet discarded pending light settlement");
            GlassLightUpdates.drain(); need(world.lights == 1, "queued light settled twice");
            tag.setBoolean("GlassFramed", false); world.reset(); packet(tile, tag);
            need(world.lights == 1, "frame-off packet did not refresh light");
        } finally { GlassSurfaceGroups.unload(world); }
    }
    private static void lifecycle() {
        World world = new World(); TileEntityProgrammableGlass tile = install(world, POS); NBTTagCompound tag = defaults(POS);
        try {
            world.reset(); tile.handleUpdateTag(tag.copy());
            need(PROXY.frames == 1 && world.renderUpdates == 1, "initial chunk tag was suppressed");
            world.reset(); world.tileLoadGuard = true;
            try { int reads = world.stateReads; tile.onLoad(); need(world.stateReads == reads, "onLoad read world state"); }
            finally { world.tileLoadGuard = false; }
            need(PROXY.frames == 1, "onLoad lost forced local invalidation");
            world.reset(); packet(tile, tag); need(PROXY.frames == 1, "onLoad reused previous update lifecycle");
            world.reset(); tile.readFromNBT(tag.copy()); need(PROXY.frames == 1, "generic NBT/copy replacement was deduplicated");
            TileEntityProgrammableGlass replacement = install(world, POS); world.reset(); packet(replacement, tag);
            need(PROXY.frames == 1 && world.renderUpdates == 1, "replacement tile inherited old packet baseline");
            replacement.onChunkUnload(); replacement.onLoad(); world.reset(); packet(replacement, tag);
            need(PROXY.frames == 1, "chunk unload/reload retained packet baseline");
            replacement.invalidate(); replacement.validate(); world.reset(); packet(replacement, tag);
            need(PROXY.frames == 1, "invalidate/reinstall retained packet baseline");
            BlockPos moved = POS.up(); world.tiles.remove(POS); world.tiles.put(moved, replacement);
            world.states.put(moved, glass.getDefaultState()); replacement.setPos(moved);
            NBTTagCompound movedTag = defaults(moved); world.reset(); packet(replacement, movedTag);
            need(PROXY.frames == 1, "position replacement retained packet baseline");
            World other = new World(); replacement.setWorld(other); replacement.setPos(POS);
            other.states.put(POS, glass.getDefaultState()); other.tiles.put(POS, replacement); other.reset(); packet(replacement, tag);
            need(PROXY.frames == 1, "world replacement retained packet baseline"); GlassSurfaceGroups.unload(other);
        } finally { GlassSurfaceGroups.unload(world); }
        for (boolean remote : new boolean[]{false, true}) {
            NonRenderingChecks.MemoryWorld parent = new NonRenderingChecks.MemoryWorld(remote);
            VehicleWorld view = new VehicleWorld(parent, VehicleStructure.display(Collections.singletonList(
                    new VehicleStructure.Cell(POS, glass.getDefaultState(), null))));
            TileEntityProgrammableGlass carried = new TileEntityProgrammableGlass(); carried.setWorld(view); carried.setPos(POS); view.tiles.put(POS, carried);
            PROXY.frames = 0; carried.readFromNBT(tag.copy());
            need(PROXY.frames == (remote ? 1 : 0), "carried replacement invalidation used view.isRemote as physical authority");
            packet(carried, tag); PROXY.frames = 0; packet(carried, tag);
            need(PROXY.frames == 0 && parent.updates == 0 && parent.renderUpdates == 0, "carried duplicate touched parent state");
            GlassSurfaceGroups.unload(view);
        }
    }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register(); VandorLabs.proxy = PROXY;
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,
                new ResourceLocation("minecraft", "glass_packet_update_checks"));
        glass = new BlockProgrammableGlass("glass_packet_update_checks"); frame = screen = -1;
        for (int i = 0; i < ScreenHousingTextures.IDS.length; i++) {
            FrameAppearance appearance = new FrameAppearance(); appearance.front = i;
            if (frame < 0 && FrameAppearance.valid(i) && appearance.light() > 0) frame = i;
            if (screen < 0 && ScreenHousingTextures.validChoice(i) && "Screens".equals(ScreenHousingTextures.category(i))) screen = i;
        }
        need(frame >= 0 && screen >= 0, "missing luminous-frame/screen-material fixtures");
        duplicates(); visualFields(); playbackAndFluid(); pendingLight(); lifecycle();
        System.out.println("glass-packet-updates PASS: exact semantic equality, complete visual dependencies, fluids/playback/light and load/copy/carried lifecycle");
    }
    private GlassPacketUpdateChecks() { }
}
