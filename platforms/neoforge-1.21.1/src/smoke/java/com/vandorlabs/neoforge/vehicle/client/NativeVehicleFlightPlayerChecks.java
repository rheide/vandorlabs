package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleFlightFixture;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.client.CameraType;
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
        if (NativeVehicleFlightFixture.enabled()) {
            System.out.println("[vandorlabs][vehicle-flight-player] stage=" + stage + " message=" + event.getMessage().getString());
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeVehicleFlightFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) { intro.onClose(); return; }
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
                rightClick(mc);
                System.out.println("[vandorlabs][vehicle-flight-player] used actual mouse dispatch on carried Status");
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
                        player.setYRot(-90);
                        player.setXRot(38);
                        if (++cameraTicks < 8 || cameraTicks % 8 != 0 || !NativeCruiseSky.ready(craft)) return;
                        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                            int terrainPixels = 0;
                            for (int y = image.getHeight() / 3; y < image.getHeight(); y += 3) {
                                for (int x = 0; x < image.getWidth(); x += 3) {
                                    int color = image.getPixelRGBA(x, y);
                                    if (Math.abs((color & 255) - 66) <= 10 && Math.abs((color >>> 8 & 255) - 116) <= 10
                                            && Math.abs((color >>> 16 & 255) - 64) <= 10) terrainPixels++;
                                }
                            }
                            if (terrainPixels < 64 && cameraTicks < 64) return;
                            image.writeToFile(mc.gameDirectory.toPath().toAbsolutePath().getParent().resolve("maxwell-flight-cruise.png"));
                            NativeVehicleFlightFixture.require(terrainPixels >= 64, "camera can see seeded terrain, pixels=" + terrainPixels);
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
                System.out.println("[vandorlabs][vehicle-flight-player] client PASS actual Flight controls and seeded horizon with mounted landing");
                mc.stop();
                stage++;
            }
            default -> {}
        }
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
