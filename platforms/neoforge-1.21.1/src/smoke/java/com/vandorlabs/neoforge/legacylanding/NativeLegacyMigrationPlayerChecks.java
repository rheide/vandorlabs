package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.neoforge.gear.NativeGearEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.vandorlabs.neoforge.legacylanding.NativeLegacyMigrationPlayerFixture.require;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeLegacyMigrationPlayerChecks {
    private static int stage, ticks;
    private static boolean obstructed, refusal, queued, complete;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent.System event) {
        if (!NativeLegacyMigrationPlayerFixture.enabled()) return;
        String message = event.getMessage().getString();
        if (message.equals("Clear the complete modular destination")) obstructed = true;
        if (message.startsWith("Conversion could not be queued.")) refusal = true;
        if (message.equals("Stationary landing gear conversion queued.")) queued = true;
        if (message.startsWith("Stationary landing gear conversion complete at ")) complete = true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeLegacyMigrationPlayerFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) {
            intro.onClose();
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        var root = NativeLegacyMigrationPlayerFixture.ROOT;
        var player = mc.player;
        require(++ticks < 1000, "timeout at stage " + stage + ", mode " + mc.gameMode.getPlayerMode()
            + ", slot " + player.getInventory().selected + ", held " + player.getMainHandItem()
            + ", pos " + player.position() + ", permitted " + player.hasPermissions(2)
            + ", obstructed " + obstructed + ", refusal " + refusal + ", queued " + queued + ", complete " + complete);
        switch (stage) {
            case 0 -> {
                if (!player.hasPermissions(2) || mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                    || player.getInventory().selected != 0 || !player.getMainHandItem().isEmpty()
                    || player.position().distanceToSqr(8.5, 96, 5.5) > .01
                    || !(mc.level.getBlockEntity(root) instanceof NativeLegacyLandingEntity)) return;
                player.connection.sendCommand(NativeLegacyMigrationPlayerFixture.COMMAND);
                stage = 1;
            }
            case 1 -> {
                if (!obstructed || !mc.level.getBlockState(root.below()).isAir()) return;
                require(mc.level.getBlockEntity(root) instanceof NativeLegacyLandingEntity && !queued && !complete,
                    "live obstruction refusal keeps legacy owner until the player retries");
                player.connection.sendCommand(NativeLegacyMigrationPlayerFixture.COMMAND);
                stage = 2;
            }
            case 2 -> {
                if (!refusal) return;
                require(mc.level.getBlockEntity(root) instanceof NativeLegacyLandingEntity && !queued && !complete,
                    "live refusal leaves legacy mechanism and never reports success");
                player.connection.sendCommand(NativeLegacyMigrationPlayerFixture.COMMAND);
                stage = 3;
            }
            case 3 -> {
                if (!queued || !complete || !(mc.level.getBlockEntity(root) instanceof NativeGearEntity)) return;
                System.out.println(NativeLegacyMigrationPlayerFixture.MARKER + " client PASS actualCommandPackets/refusal/retry/durableFeedback/updatedOwner");
                stage = 4;
                mc.stop();
            }
            default -> {}
        }
    }

    private NativeLegacyMigrationPlayerChecks() {}
}
