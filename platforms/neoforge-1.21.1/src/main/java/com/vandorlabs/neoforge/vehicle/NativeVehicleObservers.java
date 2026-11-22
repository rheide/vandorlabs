package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.navigation.NavigationRange;
import com.vandorlabs.navigation.ObserverPower;
import com.vandorlabs.navigation.ObserverScale;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.transfer.NativeConstructorFrame;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Observer power is short-lived server state, while each formed frame retains one saved owner. */
public final class NativeVehicleObservers {
    @FunctionalInterface
    public interface Visitor {
        void source(Vec3 center, int radius, ChannelList channels, int range, int radar, boolean sentinel);
    }

    public static void visit(ServerLevel level, AABB area, boolean powered, Visitor visitor) {
        for (var craft : level.getEntitiesOfClass(NativeGroundVehicle.class, area)) {
            var resources = craft.resources();
            if (resources == null || !craft.admitted() || NativeVehicleTransfer.escrowed(craft)) continue;
            for (var observer : resources.observers()) {
                if (observer.power.powered(level.getGameTime()) != powered) continue;
                var center = worldPoint(craft, observer.core);
                int radius = NavigationRange.radius(observer.design.maximum(), center.y,
                    ObserverScale.linearScale(observer.design.id(), observer.design.width()));
                int radar = NavigationRange.radarRadius(observer.design.id());
                visitor.source(center, radius, observer.channels, observer.range,
                    powered && observer.requested ? radar : 0, radar == NavigationRange.SENTINEL_RADAR_RADIUS);
            }
        }
    }

    static boolean block(BlockState state) {
        return state.getBlock() instanceof NativeObserverCoreBlock || state.getBlock() instanceof NativeObserverPartBlock;
    }

    static List<Receiver> build(Collection<NativeRawCell> cells, Map<BlockPos, NativeRawCell> hull) {
        var result = new ArrayList<Receiver>();
        var parts = new HashSet<BlockPos>();
        var owners = new HashSet<UUID>();
        for (var cell : cells) if (cell.state.getBlock() instanceof NativeObserverPartBlock) parts.add(cell.pos);
        for (var cell : cells) {
            if (!(cell.state.getBlock() instanceof NativeObserverCoreBlock block)) continue;
            var saved = cell.tile();
            if (!cell.state.getValue(NativeObserverCoreBlock.FORMED) || !saved.hasUUID("ObserverKey")
                || !saved.contains("ConstructorCoreX", Tag.TAG_INT) || !saved.contains("ConstructorCoreZ", Tag.TAG_INT)
                || !saved.contains("ObserverInverted", Tag.TAG_BYTE) || !saved.contains("TransmittedChannels", Tag.TAG_INT_ARRAY)) {
                throw new IllegalArgumentException("Vehicle observer must be a complete formed native frame");
            }
            var owner = saved.getUUID("ObserverKey");
            if (!owners.add(owner)) throw new IllegalArgumentException("Duplicate vehicle observer owner");
            var design = NativeObserverDesigns.scaled(block.design.id(), saved.getInt("ObserverWidth"));
            var layout = new CoreLayout(design.width(), design.height(), design.depth(),
                cell.state.getValue(NativeObserverCoreBlock.FACING).get3DDataValue(),
                saved.getInt("ConstructorCoreX"), saved.getInt("ConstructorCoreZ"));
            if (!layout.canonicalCorner()) throw new IllegalArgumentException("Unknown vehicle observer corner");
            boolean inverted = saved.getBoolean("ObserverInverted"), localSignal = false;
            var frame = new ArrayList<BlockPos>();
            for (int x = 0; x < layout.width; x++) for (int y = 0; y < layout.height; y++) for (int z = 0; z < layout.depth; z++) {
                var pos = member(cell.pos, layout, inverted, x, y, z);
                if (!pos.equals(cell.pos)) {
                    var part = hull.get(pos);
                    if (!parts.remove(pos) || part == null) throw new IllegalArgumentException("Vehicle observer frame is incomplete");
                    var raw = part.tile();
                    NativeObserverTransactions.requirePart(raw, cell.pos.subtract(pos), owner);
                    NativeConstructorFrame.validatePart(raw, layout.frame(x, y, z));
                }
                if (layout.frame(x, y, z)) {
                    frame.add(pos);
                    if (!localSignal) for (var side : Direction.values()) {
                        var adjacent = hull.get(pos.relative(side));
                        if (adjacent != null && adjacent.state.is(Blocks.REDSTONE_BLOCK)) { localSignal = true; break; }
                    }
                }
            }
            int range = saved.getInt("TransmissionRange");
            if (!NativeObserverEntity.validRange(range)) throw new IllegalArgumentException("Invalid vehicle observer transmission range");
            var inlet = member(cell.pos, layout, inverted, layout.width / 2, inverted ? layout.height - 1 : 0, layout.depth / 2);
            result.add(new Receiver(cell.pos, inlet, design, ChannelList.of(saved.getIntArray("TransmittedChannels")),
                range, !saved.contains("ObserverRadar") || saved.getBoolean("ObserverRadar"),
                saved.getBoolean("ObserverRedstone"), localSignal, List.copyOf(frame)));
        }
        if (!parts.isEmpty()) throw new IllegalArgumentException("Capture every observer part with its own formed native core");
        return List.copyOf(result);
    }

    static Vec3 worldPoint(NativeGroundVehicle craft, BlockPos local) {
        var point = craft.toWorld(local);
        return craft.highFlight() ? point.add(craft.routeX() - craft.getX(),
            craft.flightAltitude() - craft.getY(), craft.routeZ() - craft.getZ()) : point;
    }

    private static BlockPos member(BlockPos core, CoreLayout layout, boolean inverted, int x, int y, int z) {
        return core.offset(layout.x(x, z), inverted ? -y : y, layout.z(x, z));
    }

    static final class Receiver {
        final BlockPos core, inlet;
        final NativeObserverDesigns.Design design;
        final ChannelList channels;
        final int range;
        final boolean radar, gated, localSignal;
        final List<BlockPos> frame;
        final ObserverPower power = new ObserverPower();
        boolean requested;

        Receiver(BlockPos core, BlockPos inlet, NativeObserverDesigns.Design design, ChannelList channels,
                 int range, boolean radar, boolean gated, boolean localSignal, List<BlockPos> frame) {
            this.core = core;
            this.inlet = inlet;
            this.design = design;
            this.channels = channels;
            this.range = range;
            this.radar = radar;
            this.gated = gated;
            this.localSignal = localSignal;
            this.frame = frame;
        }

        void sample(NativeGroundVehicle craft, NativeVehicleResources resources) {
            requested = radar && (!gated || localSignal || resources.channelLevel(craft, core, channels) > 0);
            if (radar && !requested && resources.hasLocalOutputs()) for (var cell : frame) {
                if (resources.localLevel(cell) > 0) {
                    requested = true;
                    break;
                }
            }
        }
    }

    private NativeVehicleObservers() {}
}
