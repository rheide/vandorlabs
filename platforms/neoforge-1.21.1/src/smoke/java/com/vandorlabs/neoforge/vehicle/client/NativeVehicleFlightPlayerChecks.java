package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleFlightFixture;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeVehicleFlightPlayerChecks {
    private static int stage, ticks, cameraTicks;
    private static NativeGroundVehicle craft;
    private static boolean captured, becameHigh;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeVehicleFlightFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) { intro.onClose(); return; }
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        NativeVehicleFlightFixture.require(++ticks < 1500, "client stage " + stage + " pos=" + player.position()
                + " mode=" + mc.gameMode.getPlayerMode() + " hand=" + player.getMainHandItem()
                + " slot=" + player.getInventory().selected + " craft=" + craft);
        switch (stage) {
            case 0 -> {
                if (mc.gameMode.getPlayerMode() != GameType.SURVIVAL || !player.getMainHandItem().isEmpty()) return;
                var found = mc.level.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(NativeVehicleFlightFixture.ROOT).inflate(12));
                if (found.size() != 1 || !found.getFirst().admitted() || found.getFirst().statusPanels().size() != 1) return;
                craft = found.getFirst();
                var seat = craft.pilotSeat();
                aim(mc, craft.position().add(seat.getX(), seat.getY() + .58, seat.getZ()));
                var hit = craft.hit(player);
                if (!seat.equals(hit) && !seat.above().equals(hit)) return;
                mc.gameMode.interact(player, craft, InteractionHand.MAIN_HAND);
                stage++;
            }
            case 1 -> {
                if (player.getVehicle() != craft) return;
                var at = craft.statusPanels().getFirst().pos();
                double column = NativeVehicleStatus.buttonLeft(NativeGroundVehicle.FLIGHT) + NativeVehicleStatus.BUTTON_WIDTH / 2D;
                double row = (NativeVehicleStatus.BUTTON_START + NativeVehicleStatus.BUTTON_END) / 2D;
                aim(mc, craft.position().add(at.getX() + .5 - column / 128, at.getY() + 1 - row / 128, at.getZ() - .5));
                if (!craft.pointsAtMountedControl(player)) return;
                KeyMapping.click(mc.options.keyUse.getKey());
                stage++;
            }
            case 2 -> {
                if (craft.mode() != NativeGroundVehicle.FLIGHT || craft.driveSpeed() < FlightEnvelope.TAKEOFF_SPEED - .002) return;
                NativeVehicleFlightFixture.require(craft.hullPitch() == 0 && craft.flightAltitude() < NativeVehicleFlightFixture.ROOT.getY()+.1,
                        "runway reaches takeoff speed without lifting until pilot pitch-up");
                mc.options.keyDown.setDown(true);
                stage++;
            }
            case 3 -> {
                if (!craft.highFlight()) return;
                becameHigh = true;
                mc.options.keyDown.setDown(false);
                mc.options.keyUp.setDown(true);
                stage++;
            }
            case 4 -> {
                if (craft.hullPitch() > 1) return;
                mc.options.keyUp.setDown(false);
                stage++;
            }
            case 5 -> {
                if (craft.highFlight()) {
                    if (!captured && craft.flightAltitude() > 1000) {
                        player.setXRot(70);
                        if (++cameraTicks < 8 || !NativeCruiseSky.ready(craft)) return;
                        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                            image.writeToFile(mc.gameDirectory.toPath().toAbsolutePath().getParent().resolve("maxwell-flight-cruise.png"));
                        } catch (java.io.IOException error) {
                            throw new IllegalStateException("Native high-flight capture", error);
                        }
                        captured = true;
                    }
                    return;
                }
                if (!becameHigh || craft.mode() != NativeGroundVehicle.TAXI) return;
                NativeVehicleFlightFixture.require(player.getVehicle() == craft && captured
                        && craft.flightReadout().fuel() == 0, "client observed terrain projection and mounted safe landing");
                System.out.println("[vandorlabs][vehicle-flight-player] client PASS actual Flight controls and seeded horizon with mounted landing");
                mc.stop();
                stage++;
            }
            default -> {}
        }
    }

    private static void aim(Minecraft mc, Vec3 target) {
        var player = mc.player;
        var delta = target.subtract(player.getEyePosition());
        player.setYRot((float)(Math.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG));
        player.setXRot((float)(-Math.atan2(delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG));
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround()));
    }

    private NativeVehicleFlightPlayerChecks() {}
}
