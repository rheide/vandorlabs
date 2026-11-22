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
    public static final int WIDTH = 128, BUTTON_WIDTH = 36, BUTTON_LEFT = 7, BUTTON_SPACING = 39;
    public static final int BUTTON_START = 16, BUTTON_END = 28;
    public static final int EMERGENCY_PARK = 3, EMERGENCY_START = 53, EMERGENCY_END = 64;
    public static final List<String> MODES = List.of("Taxi", "Hover", "Flight");

    public record Panel(BlockPos pos, int facing, ScreenControlLayout.Projection projection) {
        int action(Vec3 from, Vec3 to, boolean emergency) {
            Vec3 base = Vec3.atLowerCornerOf(pos).add(-.5, 0, -.5);
            var start = from.subtract(base);
            var end = to.subtract(base);
            var sample = projection.sample(start.x, start.y, start.z, end.x, end.y, end.z);
            if (emergency && sample.line >= EMERGENCY_START && sample.line < EMERGENCY_END
                    && sample.column >= 7 && sample.column < 121) return EMERGENCY_PARK;
            if (sample.line < BUTTON_START || sample.line >= BUTTON_END) return -1;
            for (int mode = 0; mode < MODES.size(); mode++) {
                int left = buttonLeft(mode);
                if (sample.column >= left && sample.column < left + BUTTON_WIDTH) return mode;
            }
            return -1;
        }
    }

    public static int buttonLeft(int mode) { return BUTTON_LEFT + mode * BUTTON_SPACING; }

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
            boolean chipDeck = slot == 0 && block.family() == ScreenFamily.HALF_CONSOLE && settings.secondarySurface() == -4;
            var projection = ScreenControlLayout.of(block.family(), shape, slot, data.smallInput, data.wallPosition,
                    settings.ceilingMounted(), settings.ceilingPosition(), chipDeck);
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
