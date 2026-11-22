package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleFlightFixture;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.neoforge.vehicle.NativeVehicleScreenRowsFixture;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeVehicleFlightPlayerChecks {
    private static final int[] TERRAIN_COLORS = {0x427440, 0x366640, 0x28659b, 0xc7b77c, 0xcbdfe2, 0x7b8281, 0xb77550};
    private static int stage, ticks, cameraTicks, boardingAim;
    private static double runwayAltitude;
    private static NativeGroundVehicle craft;
    private static boolean captured, becameHigh, assembled;
    private static final java.lang.reflect.Method USE_ITEM = useItem();

    private static java.lang.reflect.Method useItem() {
        try {
            var method = Minecraft.class.getDeclaredMethod("startUseItem");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @SubscribeEvent public static void chat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        var message = event.getMessage().getString();
        if ((NativeVehicleFlightFixture.enabled() || NativeVehicleScreenRowsFixture.enabled()) && message.equals("Vehicle assembled")) {
            assembled = true;
        }
        if (NativeVehicleFlightFixture.enabled()) {
            System.out.println("[vandorlabs][vehicle-flight-player] stage=" + stage + " message=" + message);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeVehicleFlightFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) { intro.onClose(); return; }
        if (com.vandorlabs.neoforge.vehicle.NativeVehicleEngineMenuFixture.enabled()) return;
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        NativeVehicleFlightFixture.require(++ticks < 1500, "client stage " + stage + " pos=" + player.position()
                + " mode=" + mc.gameMode.getPlayerMode() + " hand=" + player.getMainHandItem()
                + " slot=" + player.getInventory().selected + " craft=" + craft);
        if (NativeVehicleFlightFixture.restartPhase().equals("read")) {
            if (!(player.getVehicle() instanceof NativeGroundVehicle restored) || !restored.highFlight()
                    || restored.flightAltitude() <= 1000) return;
            NativeVehicleFlightFixture.require(restored.admitted() && restored.flightReadout().fuel() == 1
                    && Math.hypot(restored.routeX() - restored.getX(), restored.routeZ() - restored.getZ()) > 180,
                    "cold client receives geographic route and attached Pilot Seat");
            System.out.println("[vandorlabs][vehicle-flight-read-player] client PASS watched the original mounted aircraft resume");
            mc.stop();
            return;
        }
        if (stage >= 4 && player.getVehicle() instanceof NativeGroundVehicle active && active.getUUID().equals(craft.getUUID())) craft = active;
        switch (stage) {
            case 0 -> {
                if (!assembled || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || !player.getMainHandItem().isEmpty()) return;
                var found = mc.level.getEntitiesOfClass(NativeGroundVehicle.class, player.getBoundingBox().inflate(10));
                if (found.size() != 1 || !found.getFirst().admitted() || found.getFirst().statusPanels().size() != 1) return;
                craft = found.getFirst();
                if (player.getVehicle() == craft) {
                    stage++;
                    return;
                }
                var seat = craft.pilotSeat();
                aim(mc, craft.position().add(seat.getX(), seat.getY() + .58, seat.getZ()));
                var hit = craft.hit(player);
                if (!seat.equals(hit) && !seat.above().equals(hit)) {
                    boardingAim = 0;
                    return;
                }
                if (++boardingAim % 8 == 0) mc.gameMode.interact(player, craft, InteractionHand.MAIN_HAND);
            }
            case 1 -> {
                if (player.getVehicle() != craft || !craft.onGround()) return;
                var at = craft.statusPanels().getFirst().pos();
                double column = NativeVehicleStatus.buttonLeft(NativeGroundVehicle.FLIGHT) + NativeVehicleStatus.BUTTON_WIDTH / 2D;
                double row = (NativeVehicleStatus.BUTTON_START + NativeVehicleStatus.BUTTON_END) / 2D;
                aim(mc, craft.position().add(at.getX() + .5 - column / 128, at.getY() + 1 - row / 128, at.getZ() - .5));
                if (!craft.pointsAtMountedControl(player)) {
                    if (ticks % 40 == 0) System.out.println("[vandorlabs][vehicle-flight-player] Status ray hit=" + craft.hit(player)
                            + " target=" + at + " eye=" + player.getEyePosition() + " pose=" + craft.position());
                    return;
                }
                runwayAltitude = craft.flightAltitude();
                rightClick(mc);
                System.out.println("[vandorlabs][vehicle-flight-player] used actual mouse dispatch on carried Status");
                stage++;
            }
            case 2 -> {
                if (craft.mode() != NativeGroundVehicle.FLIGHT || craft.driveSpeed() < FlightEnvelope.TAKEOFF_SPEED - .002) return;
                NativeVehicleFlightFixture.require(craft.hullPitch() == 0 && craft.flightAltitude() <= runwayAltitude + .51,
                        "runway reaches takeoff speed without lifting until pilot pitch-up, initial="
                                + runwayAltitude + " current=" + craft.flightAltitude());
                mc.options.keyDown.setDown(true);
                stage++;
            }
            case 3 -> {
                String impact = NativeVehicleFlightFixture.impact();
                if (!impact.isEmpty()) {
                    int expected = impact.equals("hover") ? NativeGroundVehicle.HOVER : NativeGroundVehicle.TAXI;
                    if (craft.mode() != expected) return;
                    // Release pitch-up before asking the recovered Taxi or Hover mode to settle.
                    mc.options.keyDown.setDown(false);
                    if (Math.abs(craft.driveSpeed()) > .005) return;
                    NativeVehicleFlightFixture.require(player.getVehicle() == craft && craft.flightReadout().fuel() == 1
                            && !craft.highFlight(), "connected pilot observes powered terrain-impact recovery");
                    System.out.println("[vandorlabs][vehicle-flight-impact-" + impact + "-player] client PASS seated with matching recovery mode");
                    mc.stop();
                    stage = 6;
                    return;
                }
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
                    if (NativeVehicleFlightFixture.restartPhase().equals("write") && craft.flightAltitude() > 1000
                            && Math.hypot(craft.routeX() - craft.getX(), craft.routeZ() - craft.getZ()) > 260) {
                        System.out.println("[vandorlabs][vehicle-flight-write-player] client PASS powered aircraft still mounted at save");
                        mc.stop();
                        stage++;
                        return;
                    }
                    if (!captured && craft.flightAltitude() > 1000) {
                        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                        mc.options.hideGui = true;
                        player.setYRot(NativeVehicleFlightFixture.normalTerrain() ? -55 : -90);
                        player.setXRot(38);
                        if (++cameraTicks < 8 || cameraTicks % 8 != 0 || !NativeCruiseSky.ready(craft)) return;
                        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                            int[] terrainPixels = new int[TERRAIN_COLORS.length];
                            for (int y = image.getHeight() / 3; y < image.getHeight(); y += 3) {
                                for (int x = 0; x < image.getWidth(); x += 3) {
                                    int color = image.getPixelRGBA(x, y);
                                    for (int index = 0; index < TERRAIN_COLORS.length; index++) {
                                        if (matchesColor(color, TERRAIN_COLORS[index])) {
                                            terrainPixels[index]++;
                                            break;
                                        }
                                    }
                                }
                            }
                            int bandCount = 0;
                            for (int count : terrainPixels) if (count >= 64) bandCount++;
                            boolean terrainVisible = NativeVehicleFlightFixture.normalTerrain() ? bandCount >= 2 : terrainPixels[0] >= 64;
                            boolean exhaustVisible = hasCarriedAfterburner(mc, craft);
                            if ((!terrainVisible || !exhaustVisible) && cameraTicks < 64) return;
                            image.writeToFile(mc.gameDirectory.toPath().toAbsolutePath().getParent().resolve("maxwell-flight-cruise.png"));
                            NativeVehicleFlightFixture.require(terrainVisible,
                                    "camera can see seeded terrain, pixels=" + java.util.Arrays.toString(terrainPixels));
                            NativeVehicleFlightFixture.require(exhaustVisible, "afterburner particles originate from the carried Maxwell at altitude");
                        } catch (java.io.IOException error) {
                            throw new IllegalStateException("Native high-flight capture", error);
                        }
                        captured = true;
                        mc.options.setCameraType(CameraType.FIRST_PERSON);
                        mc.options.hideGui = false;
                    }
                    return;
                }
                if (!becameHigh || craft.mode() != NativeGroundVehicle.TAXI) return;
                NativeVehicleFlightFixture.require(player.getVehicle() == craft && captured
                        && craft.flightReadout().fuel() == 0, "client observed terrain projection and mounted safe landing");
                System.out.println("[vandorlabs][vehicle-flight-player] client PASS actual Flight controls, carried afterburner and seeded horizon with mounted landing");
                mc.stop();
                stage++;
            }
            default -> {}
        }
    }

    static boolean assemblyConfirmed() { return assembled; }

    private static boolean hasCarriedAfterburner(Minecraft client, NativeGroundVehicle craft) {
        boolean[] found = {false};
        var origin = craft.position();
        client.particleEngine.iterateParticles(particle -> {
            if (particle.getClass().getSimpleName().equals("NativePropulsionParticle")
                    && particle.getRenderBoundingBox(1).distanceToSqr(origin) <= 16 * 16) found[0] = true;
        });
        return found[0];
    }

    private static boolean matchesColor(int rgba, int rgb) {
        return Math.abs((rgba & 255) - (rgb >>> 16 & 255)) <= 9
                && Math.abs((rgba >>> 8 & 255) - (rgb >>> 8 & 255)) <= 9
                && Math.abs((rgba >>> 16 & 255) - (rgb & 255)) <= 9;
    }

    static void rightClick(Minecraft mc) {
        try {
            USE_ITEM.invoke(mc);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Invoke ordinary right-click", failure);
        }
    }

    static void aim(Minecraft mc, Vec3 target) {
        var player = mc.player;
        var delta = target.subtract(player.getEyePosition());
        player.setYRot((float)(Math.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG));
        player.setXRot((float)(-Math.atan2(delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG));
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround()));
    }

    private NativeVehicleFlightPlayerChecks() {}
}
