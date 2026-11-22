package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeDualMaxwellFixture;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleStatus;
import com.vandorlabs.screen.ScreenControlLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeDualMaxwellPlayerChecks {
    private static String action = "";
    private static boolean assembled, finished;
    private static int ticks;

    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event) {
        if (!NativeDualMaxwellFixture.enabled()) return;
        var message = event.getMessage().getString();
        if (message.equals("Vehicle assembled")) assembled = true;
        if (message.startsWith(NativeDualMaxwellFixture.PREFIX)) {
            action = message.substring(NativeDualMaxwellFixture.PREFIX.length());
            System.out.println("[vandorlabs][dual-maxwell-player] client action " + action);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeDualMaxwellFixture.enabled() || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) { intro.onClose(); return; }
        var player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        if (++ticks >= 1600) throw new IllegalStateException("Dual Maxwell client timeout action=" + action
                + " assembled=" + assembled + " mode=" + mc.gameMode.getPlayerMode() + " held=" + player.getMainHandItem()
                + " rider=" + player.getVehicle() + " slot=" + player.getInventory().selected + " pos=" + player.position());
        if (!(player.getVehicle() instanceof NativeGroundVehicle craft)) {
            if (!assembled || !action.equals("power") || mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                    || !player.getMainHandItem().isEmpty()) return;
            var found = mc.level.getEntitiesOfClass(NativeGroundVehicle.class, player.getBoundingBox().inflate(10));
            if (found.size() != 1 || !found.getFirst().admitted()) return;
            var candidate = found.getFirst();
            var seat = candidate.pilotSeat();
            NativeVehicleFlightPlayerChecks.aim(mc, candidate.position().add(seat.getX(), seat.getY() + .58, seat.getZ()));
            var hit = candidate.hit(player);
            if (ticks % 8 == 0 && (seat.equals(hit) || seat.above().equals(hit))) {
                mc.gameMode.interact(player, candidate, InteractionHand.MAIN_HAND);
            }
            return;
        }
        if (!craft.admitted() || craft.statusPanels().size() != 1) return;
        mc.options.keyDown.setDown(action.equals("raise") && !craft.highFlight());
        mc.options.keyUp.setDown(action.equals("level") && craft.hullPitch() > .5F);
        switch (action) {
            case "power" -> slider(mc, craft, 3, 15);
            case "hover" -> mode(mc, craft, NativeGroundVehicle.HOVER);
            case "flight" -> mode(mc, craft, NativeGroundVehicle.FLIGHT);
            case "reduce" -> slider(mc, craft, 1, 5);
            case "stop" -> slider(mc, craft, 0, 0);
            case "passed" -> {
                if (craft.mode() != NativeGroundVehicle.HOVER || craft.highFlight() || craft.flightReadout().drives() != 2) return;
                finished = true;
                System.out.println("[vandorlabs][dual-maxwell-player] client PASS real craft mode and analog throttle clicks with mounted Hover recovery");
                mc.stop();
            }
            default -> {}
        }
    }

    private static void slider(Minecraft mc, NativeGroundVehicle craft, int detent, int level) {
        var at = NativeDualMaxwellFixture.THROTTLE;
        if ((craft.meterSurface(at, 0) & 15) == level) return;
        double step = (ScreenControlLayout.SLIDER_RIGHT - ScreenControlLayout.SLIDER_LEFT) / (double)ScreenControlLayout.SLIDER_STEPS;
        press(mc, craft, at, ScreenControlLayout.SLIDER_LEFT + (detent + .5) * step, ScreenControlLayout.FULL_ROW_TOP + 4);
    }

    private static void mode(Minecraft mc, NativeGroundVehicle craft, int wanted) {
        if (craft.mode() == wanted) return;
        press(mc, craft, craft.statusPanels().getFirst().pos(),
                NativeVehicleStatus.buttonLeft(wanted) + NativeVehicleStatus.BUTTON_WIDTH / 2D,
                (NativeVehicleStatus.BUTTON_START + NativeVehicleStatus.BUTTON_END) / 2D);
    }

    private static void press(Minecraft mc, NativeGroundVehicle craft, BlockPos at, double column, double line) {
        var point = new Vec3(at.getX() + .5 - column / 128, at.getY() + 1 - line / 128, at.getZ() - .5);
        NativeVehicleFlightPlayerChecks.aim(mc, craft.position().add(craft.rotateLocalDirection(point, craft.getYRot(), craft.hullPitch())));
        if (ticks % 8 == 0 && at.equals(craft.hit(mc.player)) && craft.pointsAtMountedControl(mc.player)) {
            NativeVehicleFlightPlayerChecks.rightClick(mc);
        }
    }

    private NativeDualMaxwellPlayerChecks() {}
}
