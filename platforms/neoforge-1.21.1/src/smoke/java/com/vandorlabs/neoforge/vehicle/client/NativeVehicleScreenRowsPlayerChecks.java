package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleScreenRowsFixture;
import com.vandorlabs.screen.ScreenControlLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeVehicleScreenRowsPlayerChecks {
    private static NativeGroundVehicle craft;
    private static int stage, ticks;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeVehicleScreenRowsFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) { intro.onClose(); return; }
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        var monitor = NativeVehicleScreenRowsFixture.MONITOR.subtract(NativeVehicleScreenRowsFixture.ROOT);
        int reading = craft == null ? 0 : craft.meterSurface(monitor, 0);
        NativeVehicleScreenRowsFixture.require(++ticks < 1200, "client stage=" + stage + " reading=" + reading
                + " rider=" + player.getVehicle() + " held=" + player.getMainHandItem() + " slot=" + player.getInventory().selected);
        switch (stage) {
            case 0 -> {
                if (!NativeVehicleFlightPlayerChecks.assemblyConfirmed()
                        || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || !player.getMainHandItem().isEmpty()) return;
                var found = mc.level.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(NativeVehicleScreenRowsFixture.ROOT).inflate(7));
                if (found.size() != 1 || !found.getFirst().admitted()) return;
                craft = found.getFirst();
                var seat = craft.pilotSeat();
                NativeVehicleFlightPlayerChecks.aim(mc, craft.position().add(seat.getX(), seat.getY() + .58, seat.getZ()));
                var hit = craft.hit(player);
                if (!seat.equals(hit) && !seat.above().equals(hit)) return;
                mc.gameMode.interact(player, craft, InteractionHand.MAIN_HAND);
                var appearance = craft.visual().cells().stream().filter(cell -> cell.pos().equals(monitor))
                        .findFirst().orElseThrow().appearance();
                var rows = appearance.getCompound("RedstonePrimary").getList("Rows", Tag.TAG_COMPOUND);
                for (var row : rows) {
                    var tag = (CompoundTag)row;
                    NativeVehicleScreenRowsFixture.require(!tag.contains("RedstoneChannels") && !tag.contains("PrivateMarker"),
                            "spawn appearance never includes the wired bank or private row fields");
                }
                stage++;
            }
            case 1 -> {
                if (player.getVehicle() != craft || !craft.onGround()) return;
                if (press(mc, 0, 75, true)) stage++;
            }
            case 2 -> { if ((reading & 15) == 15 && press(mc, 1, 75, true)) stage++; }
            case 3 -> { if ((reading >>> 4 & 15) == 15 && press(mc, 2, 97, true)) stage++; }
            case 4 -> {
                if ((reading >>> 4 & 15) != 0 || (reading >>> 8 & 15) != 10 || (reading >>> 12 & 15) != 10) return;
                if (press(mc, 3, 80, false)) {
                    mc.options.keyShift.setDown(true);
                    stage++;
                }
            }
            case 5 -> {
                if (player.getVehicle() != null) return;
                mc.options.keyShift.setDown(false);
                stage++;
            }
            case 6 -> {
                if (!craft.isRemoved() || mc.level.isEmptyBlock(NativeVehicleScreenRowsFixture.MONITOR)) return;
                System.out.println("[vandorlabs][vehicle-screen-rows-player] client PASS real carried mouse controls and read-only display");
                mc.stop();
                stage++;
            }
            default -> {}
        }
    }

    private static boolean press(Minecraft mc, int row, int column, boolean writable) {
        var at = NativeVehicleScreenRowsFixture.MONITOR.subtract(NativeVehicleScreenRowsFixture.ROOT);
        var player = mc.player;
        var vehicle = (NativeGroundVehicle)player.getVehicle();
        double line = ScreenControlLayout.FULL_ROW_TOP + row * ScreenControlLayout.ROW_HEIGHT + 4;
        NativeVehicleFlightPlayerChecks.aim(mc, vehicle.position().add(at.getX() + .5 - column / 128D,
                at.getY() + 1 - line / 128, at.getZ() - .5));
        if (!at.equals(vehicle.hit(player))) return false;
        NativeVehicleScreenRowsFixture.require(vehicle.pointsAtMountedControl(player) == writable, "screen row " + row + " writable=" + writable);
        NativeVehicleFlightPlayerChecks.rightClick(mc);
        return true;
    }

    private NativeVehicleScreenRowsPlayerChecks() {}
}
