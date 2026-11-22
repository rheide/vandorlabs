package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.client.NativeDriveScreen;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleEngineMenuFixture;
import com.vandorlabs.neoforge.vehicle.NativeVehicleFlightFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="vandorlabs", value=Dist.CLIENT)
public final class NativeVehicleEngineMenuPlayer {
    private static int stage, ticks, lastUse = -20;
    private static boolean done;

    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if (NativeVehicleEngineMenuFixture.enabled() && event.getMessage().getString().equals("Carried Maxwell settings verified")) done = true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeVehicleEngineMenuFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        var actor = mc.player;
        if (actor == null || mc.gameMode == null || mc.level == null) return;
        if (++ticks > 900) throw new AssertionError("Engine menu stage " + stage + ", mode=" + mc.gameMode.getPlayerMode()
            + ", held=" + actor.getMainHandItem() + ", slot=" + actor.getInventory().selected + ", position=" + actor.position() + ", screen=" + mc.screen);
        if (done && stage == 4) {
            System.out.println("[vandorlabs][vehicle-engine-player] client PASS empty-hand and Configurizer use on carried intake, throttle, channels and purge");
            mc.stop();
            return;
        }
        if (stage == 0 || stage == 2) {
            if (mc.screen == null && mc.gameMode.getPlayerMode() == GameType.SURVIVAL
                    && (stage == 0 ? actor.getMainHandItem().isEmpty() : actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))
                    && ticks - lastUse >= 12) {
                var found = mc.level.getEntitiesOfClass(NativeGroundVehicle.class, new AABB(NativeVehicleFlightFixture.ROOT).inflate(12));
                if (found.size() != 1 || !found.getFirst().admitted()) return;
                var craft = found.getFirst();
                var target = NativeVehicleEngineMenuFixture.TARGET;
                if (Math.abs(actor.getX() - craft.getX() - target.getX()) > .05 || Math.abs(actor.getZ() - craft.getZ() + 2) > .05) return;
                NativeVehicleFlightPlayerChecks.aim(mc, craft.position().add(target.getX(), target.getY()+.5, -.48));
                if (!target.equals(craft.hit(actor))) return;
                mc.gameMode.interact(actor, craft, InteractionHand.MAIN_HAND);
                lastUse = ticks;
            } else if (mc.screen instanceof NativeDriveScreen screen) {
                if (!screen.getMenu().root().equals(NativeVehicleEngineMenuFixture.CORE) || screen.getMenu().fuel() != 1) return;
                if (stage == 0) {
                    click(screen, "Redstone");
                    click(screen, "Activation Automatic");
                    click(screen, "Throttle Digital");
                    var field = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    field.setValue("931");
                    screen.onClose();
                    stage = 2;
                } else {
                    if (!screen.getMenu().settings().levels() || !screen.getMenu().channels().contains(931)) throw new AssertionError("Reopened carried engine settings");
                    click(screen, "Purge Tank");
                    stage = 3;
                }
            }
        } else if (stage == 3 && mc.screen instanceof NativeDriveScreen screen && screen.getMenu().fuel() == 0) {
            screen.onClose();
            stage = 4;
        }
    }

    private static void click(NativeDriveScreen screen, String label) {
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing " + label));
        if (!screen.mouseClicked(button.getX()+button.getWidth()*.5, button.getY()+8, 0)) throw new AssertionError("Click " + label);
    }

    private NativeVehicleEngineMenuPlayer() {}
}
