package com.vandorlabs.neoforge.legacylanding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeLegacyWheelInventoryClient {
    private static boolean observedFull;
    private static int ticks;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeLegacyWheelInventoryFixture.ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) {
            intro.onClose();
            return;
        }
        if (mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL) return;
        if (++ticks > 400) throw new AssertionError("Legacy wheel client did not observe full and converted inventory");
        var inventory = mc.player.getInventory();
        if (ItemStack.matches(inventory.getItem(0), NativeLegacyWheelInventoryFixture.source())
                && NativeLegacyWheelInventoryFixture.opaque(inventory) && inventory.getItem(4).getCount() == 64) observedFull = true;
        if (!observedFull || !NativeLegacyWheelInventoryFixture.converted(inventory)) return;
        System.out.println("[vandorlabs][legacy-wheel-inventory-player] client PASS full and converted inventory synchronization");
        mc.stop();
    }
}
