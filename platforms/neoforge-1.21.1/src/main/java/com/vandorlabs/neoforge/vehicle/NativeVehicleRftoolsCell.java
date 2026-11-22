package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.ModConfigSpec;

/** RFTools Power's finite unlinked Dimensional Cell is the sole owner of its FE attachment. */
public final class NativeVehicleRftoolsCell {
    private static final String PACKAGE = "mcjty.rftoolspower.modules.dimensionalcell.";
    private static final String BLOCK = PACKAGE + "blocks.DimensionalCellBlock";
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String CELL = "rftoolspower:dimensional_cell_data";
    private static final Set<String> FIELDS = Set.of("energy", "totalInserted", "totalExtracted", "networkId");

    static boolean supports(BlockState state) {
        return tier(state) != null && state.getBlock().getClass().getName().equals(BLOCK);
    }

    private static String tier(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("rftoolspower")) return null;
        return switch (id.getPath()) {
            case "dimensionalcell_simple" -> "Simple";
            case "dimensionalcell" -> "Normal";
            case "dimensionalcell_advanced" -> "Advanced";
            default -> null;
        };
    }

    static boolean supports(BlockEntity tile) {
        return supports(tile.getBlockState()) && tile.getClass().getName()
            .equals(PACKAGE + "blocks.DimensionalCell" + tier(tile.getBlockState()) + "TileEntity");
    }

    /** Apply to game and cached optional handlers, never to detached snapshot serialization. */
    public static boolean blocksMutation(BlockEntity tile) {
        if (!supports(tile)) return false;
        if (tile.isRemoved()) return true;
        if (!(tile.getLevel() instanceof ServerLevel world)) return false;
        if (!world.getServer().isSameThread()) return true;
        var pos = tile.getBlockPos();
        if (NativeVehicleTransfer.holdsTerrain(world, pos)) return true;
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null || chunk.getBlockEntities().get(pos) != tile;
    }

    static void requireAccess(BlockEntity tile, UUID actor) {
        if (tile == null || !supports(tile) || Api.INSTANCE == null) throw new IllegalArgumentException("Missing standalone RFTools Cell");
        try {
            var api = Api.INSTANCE;
            if ((int)api.network.invoke(tile) != -1) refuseNetwork();
            var owner = (UUID)api.owner.invoke(tile);
            if ((int)api.channel.invoke(tile) != -1 || owner != null && !owner.equals(actor)) {
                throw new IllegalArgumentException("RFTools Cell belongs to a different owner or security channel");
            }
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalArgumentException("Cannot verify the installed RFTools Cell", unavailable);
        }
    }

    record Storage(int energy, int capacity, int rate) {}

    static Storage storage(NativeRawCell cell) {
        int energy = contents(cell.tile());
        var api = Api.INSTANCE;
        if (api == null) throw new IllegalArgumentException("Cannot read installed RFTools Cell limits");
        int simple = api.simple.get(), advanced = api.advanced.get();
        int factor = switch (tier(cell.state)) {
            case "Simple" -> 1;
            case "Advanced" -> Math.multiplyExact(simple, advanced);
            default -> simple;
        };
        if (simple <= 0) throw new IllegalArgumentException("RFTools Cell simple factor must be positive");
        int capacity = Math.multiplyExact(api.capacity.get(), factor) / simple;
        int perSide = Math.multiplyExact(api.rate.get(), factor) / simple;
        int infused = cell.tile().getInt("infusable"), maxInfuse = api.maxInfuse.get();
        if (infused < 0 || maxInfuse <= 0 || infused > maxInfuse || energy > capacity) {
            throw new IllegalArgumentException("RFTools Cell contents or infusion exceed the installed limits");
        }
        int rate = (int)(perSide * (infused / (float)maxInfuse * .5D + 1D));
        return new Storage(energy, capacity, rate);
    }

    static int contents(CompoundTag raw) {
        if (raw == null) throw new IllegalArgumentException("Missing RFTools Cell data");
        if (raw.contains("items") && (!(raw.get("items") instanceof CompoundTag items)
            || items.contains("Items") && (!(items.get("Items") instanceof ListTag stacks) || !stacks.isEmpty()))) {
            throw new IllegalArgumentException("Empty the RFTools Cell's card and charging slots before assembly");
        }
        if (raw.contains("infusable") && !raw.contains("infusable", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid RFTools Cell infusion");
        }
        if (!raw.contains(ATTACHMENTS)) return 0;
        if (!(raw.get(ATTACHMENTS) instanceof CompoundTag attachments)) {
            throw new IllegalArgumentException("Invalid RFTools Cell attachments");
        }
        if (!attachments.contains(CELL)) return 0;
        if (!(attachments.get(CELL) instanceof CompoundTag data) || !data.getAllKeys().equals(FIELDS)
            || !data.contains("energy", Tag.TAG_INT) || data.getInt("energy") < 0
            || !data.contains("totalInserted", Tag.TAG_LONG) || !data.contains("totalExtracted", Tag.TAG_LONG)
            || !data.contains("networkId", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid RFTools Cell energy receipt");
        }
        if (data.getInt("networkId") != -1) refuseNetwork();
        return data.getInt("energy");
    }

    static void write(CompoundTag raw, int amount) {
        var attachments = raw.getCompound(ATTACHMENTS);
        var data = attachments.getCompound(CELL);
        if (data.isEmpty()) {
            data.putInt("networkId", -1);
            data.putLong("totalInserted", 0);
            data.putLong("totalExtracted", 0);
        }
        data.putInt("energy", amount);
        attachments.put(CELL, data);
        raw.put(ATTACHMENTS, attachments);
    }

    static String mode(BlockState state, Direction side) {
        var property = state.getBlock().getStateDefinition().getProperty(side.getName());
        if (property == null) throw new IllegalArgumentException("Missing RFTools Cell face");
        return mode(state, property);
    }

    private static <T extends Comparable<T>> String mode(BlockState state, Property<T> property) {
        var mode = property.getName(state.getValue(property));
        if (!Set.of("none", "input", "output").contains(mode)) throw new IllegalArgumentException("Invalid RFTools Cell face");
        return mode;
    }

    static BlockState rotate(BlockState state, Rotation rotation) {
        var rotated = state;
        for (var side : Direction.values()) {
            var property = state.getBlock().getStateDefinition().getProperty(rotation.rotate(side).getName());
            rotated = withMode(rotated, property, mode(state, side));
        }
        return rotated;
    }

    private static <T extends Comparable<T>> BlockState withMode(BlockState state, Property<T> property, String mode) {
        return state.setValue(property, property.getValue(mode).orElseThrow());
    }

    private static void refuseNetwork() {
        throw new IllegalArgumentException("Unlink the RFTools Dimensional Cell before assembly");
    }

    private record Api(Method network, Method owner, Method channel, ModConfigSpec.IntValue simple,
                       ModConfigSpec.IntValue advanced, ModConfigSpec.IntValue capacity, ModConfigSpec.IntValue rate,
                       ModConfigSpec.IntValue maxInfuse) {
        static final Api INSTANCE = lookup();

        private static Api lookup() {
            try {
                var tile = Class.forName(PACKAGE + "blocks.DimensionalCellTileEntity");
                var config = Class.forName(PACKAGE + "DimensionalCellConfiguration");
                return new Api(tile.getMethod("getNetworkId"), tile.getMethod("getOwnerUUID"), tile.getMethod("getSecurityChannel"),
                    value(config, "simpleFactor"), value(config, "advancedFactor"), value(config, "rfPerNormalCell"),
                    value(config, "rfPerTick"), value(Class.forName("mcjty.lib.base.GeneralConfig"), "maxInfuse"));
            } catch (ReflectiveOperationException | LinkageError absent) {
                return null;
            }
        }

        private static ModConfigSpec.IntValue value(Class<?> config, String name) throws ReflectiveOperationException {
            return (ModConfigSpec.IntValue)config.getField(name).get(null);
        }
    }

    private NativeVehicleRftoolsCell() {}
}
