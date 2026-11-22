package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Finite Energy Cubes retain an exact long Joule owner and their installed relative side policy. */
public final class NativeVehicleMekanismCube {
    private static final String TYPE = "mekanism.common.tile.TileEntityEnergyCube";
    private static final String ENERGY = "energy_containers";
    static boolean supports(BlockState state) { return NativeVehicleMekanismEscrow.cube(state); }

    static boolean supports(BlockEntity tile) { return NativeVehicleMekanismEscrow.cube(tile); }

    enum Control { DISABLED, HIGH, LOW }

    record Description(long joules, long capacity, long output, int inputFaces, int outputFaces,
                       boolean ejecting, Control control) {
        boolean accepts(Direction side) { return (inputFaces & 1 << side.get3DDataValue()) != 0; }
        boolean emits(Direction side) { return (outputFaces & 1 << side.get3DDataValue()) != 0; }
        boolean automatic(int local) {
            return ejecting && switch (control) {
                case DISABLED -> true;
                case HIGH -> local > 0;
                case LOW -> local == 0;
            };
        }
    }

    record Quantum(long forge, long joules) {
        static final Quantum ONE = new Quantum(1, 1);
    }

    static Description read(NativeRawCell cell, HolderLookup.Provider registries) {
        var raw = cell.tile();
        long amount = contents(raw);
        var worldTile = ((EntityBlock)cell.state.getBlock()).newBlockEntity(cell.pos, cell.state);
        if (worldTile == null || !supports(worldTile)) throw new IllegalArgumentException("Missing finite Mekanism Energy Cube factory");
        // Configuration is installed on a detached native tile. The mod resolves RelativeSide itself.
        worldTile.loadWithComponents(raw, registries);
        var api = Api.INSTALLED;
        if (api == null) throw new IllegalArgumentException("Cannot read native Mekanism Energy Cube configuration");
        try {
            var energy = api.energyContainer.invoke(worldTile);
            long capacity = (long)api.capacity.invoke(energy);
            long output = (long)api.output.invoke(api.tier.invoke(worldTile));
            if (capacity <= 0 || output <= 0 || amount > capacity || amount != (long)api.energy.invoke(energy)) {
                throw new IllegalArgumentException("Cannot preserve the Mekanism Energy Cube tier and contents");
            }
            var config = api.config.invoke(worldTile);
            var bank = api.getConfig.invoke(config, api.energyType);
            if (bank == null) throw new IllegalArgumentException("Missing Mekanism Energy Cube side settings");
            int inputs = 0, outputs = 0;
            for (var side : Direction.values()) {
                var slot = api.getSlotInfo.invoke(config, api.energyType, side);
                if (slot == null) continue;
                int bit = 1 << side.get3DDataValue();
                if ((boolean)api.canInput.invoke(slot)) inputs |= bit;
                if ((boolean)api.canOutput.invoke(slot)) outputs |= bit;
            }
            var control = Control.valueOf(((Enum<?>)api.control.invoke(worldTile)).name());
            return new Description(amount, capacity, output, inputs, outputs, (boolean)api.ejecting.invoke(bank), control);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Cannot read the installed Mekanism Energy Cube", failure);
        }
    }

    static long contents(CompoundTag raw) {
        if (raw == null || raw.contains("items") && (!(raw.get("items") instanceof ListTag items) || !items.isEmpty())) {
            throw new IllegalArgumentException("Empty both Mekanism Energy Cube charging slots before assembly");
        }
        if (!raw.contains(ENERGY)) return 0;
        if (!(raw.get(ENERGY) instanceof ListTag rows) || rows.size() != 1
            || !(rows.getFirst() instanceof CompoundTag row) || !row.getAllKeys().equals(Set.of("container", "stored"))
            || !row.contains("container", Tag.TAG_BYTE) || row.getByte("container") != 0
            || !row.contains("stored", Tag.TAG_LONG) || row.getLong("stored") <= 0) {
            throw new IllegalArgumentException("Unknown Mekanism Energy Cube storage");
        }
        return row.getLong("stored");
    }

    static void write(CompoundTag raw, long joules) {
        if (joules == 0) {
            raw.remove(ENERGY);
            return;
        }
        var row = new CompoundTag();
        row.putByte("container", (byte)0);
        row.putLong("stored", joules);
        var rows = new ListTag();
        rows.add(row);
        raw.put(ENERGY, rows);
    }

    static Quantum forgeQuantum() {
        var api = Api.INSTALLED;
        if (api == null) return null;
        try {
            var conversion = api.feUnit.invoke(api.converter);
            if (!(boolean)api.enabled.invoke(conversion)) return null;
            double ratio = (double)api.ratio.invoke(conversion);
            if (!Double.isFinite(ratio) || ratio <= 0) return null;
            var decimal = BigDecimal.valueOf(ratio).stripTrailingZeros();
            var joules = decimal.movePointRight(Math.max(0, decimal.scale())).toBigIntegerExact();
            var forge = BigInteger.TEN.pow(Math.max(0, decimal.scale()));
            var gcd = joules.gcd(forge);
            var reducedJoules = joules.divide(gcd);
            var reducedForge = forge.divide(gcd);
            if (reducedJoules.bitLength() > 63 || reducedForge.bitLength() > 31) return null;
            return new Quantum(reducedForge.longValueExact(), reducedJoules.longValueExact());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Cannot read configured Mekanism Forge Energy conversion", failure);
        }
    }

    private static final class Api {
        static final Api INSTALLED = lookup();
        final Method energyContainer, energy, capacity, tier, output, config, getConfig, getSlotInfo,
            canInput, canOutput, ejecting, control, feUnit, enabled, ratio;
        final Object energyType, converter;

        Api() throws ReflectiveOperationException {
            var cube = Class.forName(TYPE);
            energyContainer = cube.getMethod("getEnergyContainer");
            var container = Class.forName("mekanism.api.energy.IEnergyContainer");
            energy = container.getMethod("getEnergy");
            capacity = container.getMethod("getMaxEnergy");
            tier = cube.getMethod("getTier");
            output = tier.getReturnType().getMethod("getOutput");
            config = cube.getMethod("getConfig");
            var type = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            energyType = type.getField("ENERGY").get(null);
            getConfig = config.getReturnType().getMethod("getConfig", type);
            getSlotInfo = config.getReturnType().getMethod("getSlotInfo", type, Direction.class);
            canInput = getSlotInfo.getReturnType().getMethod("canInput");
            canOutput = getSlotInfo.getReturnType().getMethod("canOutput");
            ejecting = getConfig.getReturnType().getMethod("isEjecting");
            control = cube.getMethod("getControlType");
            var helper = Class.forName("mekanism.api.energy.IEnergyConversionHelper");
            converter = helper.getField("INSTANCE").get(null);
            feUnit = helper.getMethod("feConversion");
            enabled = feUnit.getReturnType().getMethod("isEnabled");
            ratio = feUnit.getReturnType().getMethod("getConversion");
        }

        private static Api lookup() {
            try {
                return new Api();
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }

    private NativeVehicleMekanismCube() {}
}
