package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.screen.NativeScreenBlock;
import com.vandorlabs.neoforge.screen.NativeScreenCatalog;
import com.vandorlabs.neoforge.screen.NativeScreenSettings;
import com.vandorlabs.neoforge.screen.NativeYokeScreenBlock;
import com.vandorlabs.neoforge.transfer.NativeTransferSnapshot;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.screen.ScreenControlLayout;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.screen.VehicleStatusLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** One surface geometry and button grid for both native input dispatch and drawing. */
public final class NativeVehicleStatus {
    public static final int SURFACE = -3;

    public record Panel(BlockPos pos, int facing, ScreenControlLayout.Projection projection) {
        int action(Vec3 from, Vec3 to) {
            Vec3 base = Vec3.atLowerCornerOf(pos).add(-.5, 0, -.5);
            var start = from.subtract(base);
            var end = to.subtract(base);
            var sample = projection.sample(start.x, start.y, start.z, end.x, end.y, end.z);
            return VehicleStatusLayout.forHeight(projection.height).target(sample.column, sample.line);
        }
    }

    public static boolean supports(BlockState state) {
        return state.getBlock() instanceof NativeScreenBlock && !(state.getBlock() instanceof NativeYokeScreenBlock);
    }

    public static CompoundTag appearance(BlockState state, CompoundTag raw) {
        var result = new CompoundTag();
        var block = (NativeScreenBlock)state.getBlock();
        var settings = NativeScreenSettings.read(raw, NativeScreenCatalog.INSTANCE);
        settings.write(result);
        for (String key : List.of("DisplayActivation", "RedstoneChannels", "RedstoneInverted", "ChannelLevel",
                SaveSchema.Redstone.CHANNEL, SaveSchema.Redstone.SIGNAL)) result.remove(key);
        var catalog = NativeScreenCatalog.INSTANCE;
        FrameSettings.read(new ModernHousingTags(raw), catalog::validFrame, catalog::validFrameSurface, catalog::validMaterial)
                .write(new ModernHousingTags(result));
        for (int slot = 0; slot < 2; slot++) {
            String tag = slot == 0 ? "PrimarySurfaceTexture" : "SecondarySurfaceTexture";
            int selected = result.getInt(tag);
            String rows=slot==0?"RedstonePrimary":"RedstoneSecondary";
            if(block.family().supportsSlot(slot)&&raw.getCompound(rows).getBoolean("Enabled")){
                NativeVehicleMeters.project(raw,result,rows,block.family().standaloneControlRows(slot));
                result.putInt(tag,-2);
            } else if (selected < -1 && !selected(block.family(), settings, raw, slot)) result.putInt(tag, -1);
        }
        return result;
    }

    private static boolean selected(ScreenFamily family, NativeScreenSettings settings, CompoundTag raw, int slot) {
        String key = slot == 0 ? "RedstonePrimary" : "RedstoneSecondary";
        if (!family.supportsSlot(slot) || raw.getCompound(key).getBoolean("Enabled")) return false;
        return (slot == 0 ? settings.primarySurface() : settings.secondarySurface()) == SURFACE;
    }

    public static List<Panel> panels(BlockPos pos, BlockState state, CompoundTag raw) {
        if (!supports(state) || !raw.contains("PrimarySurfaceTexture", Tag.TAG_INT)
                && !raw.contains("SecondarySurfaceTexture", Tag.TAG_INT)) return List.of();
        var block = (NativeScreenBlock)state.getBlock();
        var settings = NativeScreenSettings.read(raw, NativeScreenCatalog.INSTANCE);
        var data = settings.scalar();
        var shape = block.screenState(state);
        var result = new ArrayList<Panel>(2);
        for (int slot = 0; slot < 2; slot++) {
            if (!selected(block.family(), settings, raw, slot)) continue;
            var projection = ScreenControlLayout.of(block.family(), shape, slot, data.smallInput, data.wallPosition,
                    settings.ceilingMounted(), settings.ceilingPosition(), false);
            result.add(new Panel(pos, shape.facing, projection));
        }
        return List.copyOf(result);
    }

    static List<Panel> panels(NativeTransferSnapshot snapshot) {
        var result = new ArrayList<Panel>();
        for (var cell : snapshot.cells()) {
            if (supports(cell.state) && cell.hasTile()) result.addAll(panels(cell.pos, cell.state, cell.tile()));
        }
        return List.copyOf(result);
    }

    private NativeVehicleStatus() {}
}
