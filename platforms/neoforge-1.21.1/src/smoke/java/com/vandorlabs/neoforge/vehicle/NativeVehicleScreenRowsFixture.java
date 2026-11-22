package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.ports.NativePortBindings;
import com.vandorlabs.neoforge.ports.NativePortBlock;
import com.vandorlabs.neoforge.ports.NativePortData;
import com.vandorlabs.neoforge.ports.NativePortKind;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.screen.NativeScreenRegistrations;
import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeVehicleScreenRowsFixture {
    public static final BlockPos ROOT = new BlockPos(8, 68, 8);
    public static final BlockPos MONITOR = ROOT.east().south(2).above();
    private static final BlockPos PILOT = ROOT.east(), SOURCE = ROOT.south(), SINK = SOURCE.south();
    private static final int TOGGLE = 71401, PULSE = 71402, ANALOG = 71403;
    private static final int START = 300, FLOW = 7;

    private static ServerPlayer actor;
    private static NativeGroundVehicle craft;
    private static int ticks, expectedPulseEnergy;
    private static long firstPulse, lastPulse;
    private static boolean ready, sawPulse, saved, parking, finished;

    public static boolean enabled() { return Boolean.getBoolean("vandorlabs.vehicleScreenRowsChecks"); }

    @SubscribeEvent public static void prepare(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        actor = player;
        var world = actor.serverLevel();
        var slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = -4; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            world.setBlockAndUpdate(ROOT.offset(x, -2, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= 3; z++) {
            world.setBlockAndUpdate(ROOT.offset(x, -1, z), slab);
        }
        world.setBlockAndUpdate(ROOT, NativeVehicleBindings.CONTROLLER.defaultBlockState());
        ((NativeVehicleControllerEntity)world.getBlockEntity(ROOT)).placedBy(player.getUUID());
        var seat = NativeSeatBindings.BLOCKS.get(NativeSeatKind.PILOT).defaultBlockState()
                .setValue(NativeSeatBlock.FACING, Direction.SOUTH);
        world.setBlockAndUpdate(PILOT, seat);
        world.setBlockAndUpdate(PILOT.above(), seat.setValue(NativeSeatBlock.UPPER, true));
        world.setBlockAndUpdate(MONITOR.below(), Blocks.STONE.defaultBlockState());
        var block = NativeScreenRegistrations.BLOCKS.get(ScreenFamily.SCREEN).get();
        world.setBlockAndUpdate(MONITOR, block.state(new ScreenFamily.State(Direction.NORTH.get3DDataValue(), false, false, false)));
        var screen = (NativeScreenEntity)world.getBlockEntity(MONITOR);
        var screenTag = screen.saveWithFullMetadata(world.registryAccess());
        var data = new CompoundTag();
        var rows = new ListTag();
        rows.add(row("Toggle", TOGGLE, ""));
        var expired = row("Pulse", PULSE, "Pulse");
        expired.putLong("PulseUntil", world.getGameTime());
        expired.putBoolean("Active", true);
        rows.add(expired);
        rows.add(row("Slider", ANALOG, "Slider"));
        rows.add(row("Meter", ANALOG, "Status"));
        rows.getCompound(0).putString("PrivateMarker", "owner-only");
        data.putBoolean("Enabled", true);
        data.putString("Title", "Carried controls");
        data.put("Rows", rows);
        screenTag.put("RedstonePrimary", data);
        var transmission = new CompoundTag();
        transmission.putBoolean("Local", true);
        transmission.putInt("Range", 16);
        var slots = new CompoundTag();
        slots.put("main", transmission);
        var forge = new CompoundTag();
        forge.put(TransmissionSettings.SETTINGS, slots);
        screenTag.put("ForgeData", forge);
        screen.loadWithComponents(screenTag, world.registryAccess());
        screen.setChanged();

        port(world, SOURCE, true);
        port(world, SINK, false);
        actor.setGameMode(GameType.SURVIVAL);
        actor.getInventory().clearContent();
        actor.teleportTo(PILOT.getX() + .5, ROOT.getY() - 1, ROOT.getZ() - 1.5);
        NativeVehicleTransfer.assemble(actor, ROOT);
    }

    private static CompoundTag row(String label, int channel, String kind) {
        var row = new CompoundTag();
        row.putString("Label", label);
        row.putIntArray("RedstoneChannels", new int[]{channel});
        if (!kind.isEmpty()) row.putBoolean(kind, true);
        return row;
    }

    private static void port(ServerLevel world, BlockPos pos, boolean source) {
        var block = NativePortBindings.BLOCKS.get(NativePortKind.POWER);
        world.setBlockAndUpdate(pos, block.defaultBlockState().setValue(NativePortBlock.FACING, Direction.NORTH));
        var tile = world.getBlockEntity(pos);
        var raw = tile.saveWithFullMetadata(world.registryAccess());
        var north = source ? ResourcePortSettings.Face.DISABLED : ResourcePortSettings.Face.INPUT;
        var south = source ? ResourcePortSettings.Face.OUTPUT : ResourcePortSettings.Face.DISABLED;
        var settings = ResourcePortSettings.placed(Direction.NORTH.get3DDataValue())
                .withFace(Direction.NORTH.get3DDataValue(), north).withFace(Direction.SOUTH.get3DDataValue(), south)
                .withTransfer(FLOW, false, ResourcePortSettings.OnTexture.TRANSFERRING);
        NativePortData.write(raw, new NativePortData.Configuration(settings, source ? 1 : 0,
                source ? ChannelList.of(PULSE) : ChannelList.EMPTY));
        if (source) raw.putInt("PortEnergy", START);
        tile.loadWithComponents(raw, world.registryAccess());
        tile.setChanged();
    }

    @SubscribeEvent public static void watch(ServerTickEvent.Post event) {
        if (actor == null || finished) return;
        var world = actor.serverLevel();
        require(++ticks < 1200, "server timeout toggled=" + saved + " pulsed=" + sawPulse + " parking=" + parking
                + " craft=" + craft + " rider=" + actor.getVehicle() + " pos=" + actor.position() + " hand=" + actor.getMainHandItem());
        if (craft == null) {
            var found = world.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(ROOT).inflate(7));
            if (found.size() == 1 && found.getFirst().admitted() && !NativeVehicleTransfer.escrowed(found.getFirst())) craft = found.getFirst();
            return;
        }
        if (parking) {
            if (!craft.isRemoved()) return;
            if (!(world.getBlockEntity(MONITOR) instanceof NativeScreenEntity restored)) return;
            var rows = restored.controls(0).rows();
            require(rows.get(0).latchedLevel(TOGGLE) == 15 && rows.get(1).pulseUntil() == 0
                    && rows.get(2).latchedLevel(ANALOG) == 10 && rows.get(3).status(),
                    "park restored all row kinds, including expired pulse and exact slider latch");
            require(world.getBlockEntity(SINK).saveWithoutMetadata(world.registryAccess()).getInt("PortEnergy") == expectedPulseEnergy,
                    "park retained the exact transferred RF");
            finished = true;
            System.out.println("[vandorlabs][vehicle-screen-rows-player] server PASS toggle, pulse expiry, analog, read-only feedback, owned save and park");
            return;
        }
        var resources = craft.resources();
        var localMonitor = MONITOR.subtract(ROOT);
        if (!ready) {
            var cells = craft.snapshot().cells();
            int energy = cells.stream().filter(cell -> cell.pos.equals(SINK.subtract(ROOT)))
                    .findFirst().orElseThrow().tile().getInt("PortEnergy");
            require(energy == 0, "expired pulse admitted without carrying stale RF signal");
            ready = true;
        }
        int pulse = resources.channelLevel(craft, SINK.subtract(ROOT), ChannelList.of(PULSE));
        if (pulse > 0) {
            if (!sawPulse) firstPulse = world.getGameTime();
            sawPulse = true;
            lastPulse = world.getGameTime();
        }
        if (!saved && sawPulse && pulse == 0 && resources.channelLevel(craft, localMonitor, ChannelList.of(ANALOG)) == 10) {
            var tag = craft.saveWithoutId(new CompoundTag());
            var receipt = NativeGroundVehicle.readSavedVehicle(tag, world.registryAccess());
            var data = receipt.snapshot().cells().stream().filter(cell -> cell.pos.equals(localMonitor)).findFirst().orElseThrow().tile();
            var rows = data.getCompound("RedstonePrimary").getList("Rows", Tag.TAG_COMPOUND);
            require(rows.getCompound(0).getString("PrivateMarker").equals("owner-only"), "private row state remains with disk owner");
            require(rows.getCompound(1).getLong("PulseUntil") == 0, "expired pulse checkpoint");
            require(resources.localLevel(localMonitor.above()) == 15, "configured local screen output reaches its adjacent block");
            int energy = receipt.snapshot().cells().stream().filter(cell -> cell.pos.equals(SINK.subtract(ROOT)))
                    .findFirst().orElseThrow().tile().getInt("PortEnergy");
            long assertedTicks = lastPulse - firstPulse + 1;
            expectedPulseEnergy = FLOW * (int)assertedTicks;
            require(assertedTicks >= 19 && assertedTicks <= 20 && energy == expectedPulseEnergy,
                    "pulse energized actual gated Power Port through its deadline, ticks=" + assertedTicks + " RF=" + energy);
            saved = true;
        }
        if (saved && actor.getVehicle() != craft && craft.canPark()) {
            parking = true;
            NativeVehicleTransfer.park(actor, craft);
        }
    }

    public static void require(boolean value, String detail) {
        if (!value) throw new IllegalStateException("Native carried screen rows: " + detail);
    }

    private NativeVehicleScreenRowsFixture() {}
}
