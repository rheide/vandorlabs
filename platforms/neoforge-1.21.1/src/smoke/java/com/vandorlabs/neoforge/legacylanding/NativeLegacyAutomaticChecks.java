package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.neoforge.gear.NativeGearDefinition;
import com.vandorlabs.neoforge.gear.NativeGearFormation;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeLegacyAutomaticChecks {
    private static final String PHASE = System.getProperty("vandorlabs.legacyAutomaticPhase", "");
    private static final BlockPos SOURCE = new BlockPos(15, 112, 8);
    private static final BlockPos OPAQUE = SOURCE.east(8);
    private static final BlockPos FRESH = SOURCE.west(8);
    private static final ChannelList CHANNELS = ChannelList.of(92891, 92893);
    private static ServerLevel world;
    private static int ticks;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (PHASE.isEmpty()) return;
        world = event.getServer().overworld();
        world.noSave = PHASE.equals("convert");
        world.setChunkForced(0, 0, true);
        world.setChunkForced(1, 0, true);
        world.getChunkAt(SOURCE);
        world.getChunkAt(SOURCE.east());
        if (PHASE.equals("seed")) {
            place(SOURCE, true, false);
            place(OPAQUE, true, true);
            place(FRESH, false, false);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (world == null) return;
        if (++ticks > 300) throw new AssertionError("Automatic wheel conversion did not finish");
        if (PHASE.equals("seed")) {
            if (ticks < 12) return;
            require(legacy(SOURCE).legacyProxy() && legacy(OPAQUE).legacyProxy() && !legacy(FRESH).legacyProxy(),
                    "legacy metadata survives native fixture creation");
            finish();
            return;
        }
        if (PHASE.equals("convert") && ticks <= 10) {
            require(legacy(SOURCE) != null && NativeLegacyMigration.pending(legacy(SOURCE)) == null,
                    "the imported world owner stays usable until saving is enabled");
            if (ticks == 10) world.noSave = false;
            return;
        }
        var member = NativeGearFormation.at(world, SOURCE.east());
        var root = member == null ? null : member.controllerEntity();
        if (root == null || root.assembly() == null || NativeTransferHolds.active(world)) return;
        require(root.definition() == NativeGearDefinition.LARGE_EXTENDER && root.progress() == 0
                        && root.assembly().columnHeight() == 2 && root.assembly().feet().size() == 4
                        && root.settings().legacyAnyChannels() && root.settings().channels().equals(CHANNELS),
                "automatic typed conversion retains the complete cross-chunk stowed mechanism and channel bank");
        require(world.getBlockState(SOURCE).isAir() && legacy(FRESH) != null && !legacy(FRESH).legacyProxy(),
                "only the imported wheel was retired");
        require(legacy(OPAQUE) != null && !legacy(OPAQUE).pristine()
                        && legacy(OPAQUE).getPersistentData().getString("WheelAttachment").equals("preserve"),
                "opaque source data prevented migration and survived");
        require(world.getEntitiesOfClass(ItemEntity.class, new AABB(SOURCE).inflate(16)).isEmpty(),
                "no loose duplicate item owner");
        finish();
    }

    private static NativeLegacyLandingEntity legacy(BlockPos pos) {
        return NativeLegacyLandingBlock.owner(world, pos);
    }

    private static void place(BlockPos pos, boolean imported, boolean opaque) {
        world.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos.east().above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos, NativeLegacyLandingBindings.BLOCKS.get(NativeLegacyLandingKind.WHEEL)
                .defaultBlockState().setValue(NativeLegacyLandingBlock.FACING, Direction.EAST));
        var wheel = legacy(pos);
        require(wheel != null, "legacy wheel fixture installed");
        if (imported) {
            var raw = wheel.saveWithFullMetadata(world.registryAccess());
            raw.remove("NativeLegacyLandingSchema");
            raw.remove("NativeLegacyLandingIdentity");
            raw.remove("NativeLegacyLandingRevision");
            NativeLegacyLandingData.configuration(raw, new NativeLegacyLandingData.Configuration(0, CHANNELS, 32, 3));
            wheel.loadWithComponents(raw, world.registryAccess());
        }
        if (opaque) wheel.getPersistentData().putString("WheelAttachment", "preserve");
        wheel.setChanged();
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError("Automatic wheel " + PHASE + " tick=" + ticks + ": " + detail);
    }

    private static void finish() {
        var server = world.getServer();
        server.saveEverything(true, true, true);
        world = null;
        System.out.println("[vandorlabs][legacy-automatic] " + PHASE + " PASS");
        server.halt(false);
    }

    private NativeLegacyAutomaticChecks() {}
}
