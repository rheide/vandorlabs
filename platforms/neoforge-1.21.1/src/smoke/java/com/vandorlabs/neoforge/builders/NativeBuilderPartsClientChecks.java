package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.builders.client.NativeBuilderScreen;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeBuilderPartsClientChecks {
    private static int operation, ticks;
    private static boolean sent, disconnected;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeBuilderClientFixture.enabled() || !NativeBuilderClientFixture.partsOnly()) return;
        var mc = Minecraft.getInstance();
        try {
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro) {
                intro.onClose();
                return;
            }
            if (disconnected && mc.level == null) {
                System.out.println("[vandorlabs][builder-client] client PASS physicalPressGantry/mounts/oldHitNegative/bedPickup/oneOwner");
                mc.stop();
                return;
            }
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            if (++ticks > 800) throw new AssertionError("Physical builder client operation " + operation
                    + " game mode " + mc.gameMode.getPlayerMode() + " slot " + mc.player.getInventory().selected
                    + " held " + mc.player.getMainHandItem() + " position " + mc.player.position() + " screen " + mc.screen);
            if (operation > 0 && !mc.level.getBlockState(NativeBuilderPartsClientFixture.ack(operation - 1)).is(Blocks.EMERALD_BLOCK)) return;
            if (operation == 8) {
                disconnected = true;
                mc.level.disconnect();
                mc.disconnect();
                return;
            }
            int machine = operation / 4, action = operation % 4;
            var raw = mc.level.getBlockEntity(NativeBuilderPartsClientFixture.root(machine));
            if (!(raw instanceof NativeBuilderEntity tile)) return;
            if (!sent) {
                if (!ready(mc, NativeBuilderPartsClientFixture.aim(machine, action), action == 1)) return;
                if (action == 2 && tile.getItem(1).isEmpty()) return;
                var hit = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
                if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                        || NativeBuilderBindings.ownerAt(mc.level, block.getBlockPos()) != tile) {
                    throw new AssertionError("Authored ray misses owned frame operation=" + operation + " hit=" + hit);
                }
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, block);
                sent = true;
                return;
            }
            if (action == 0 && (!tile.formed() || tile.transactionBlocked())) return;
            if (action == 1 && (tile.batteryEnergy(0) != 10000 || !mc.player.getMainHandItem().isEmpty())) return;
            if (action == 2) {
                if (!(mc.screen instanceof NativeBuilderScreen screen) || screen.getMenu().contents().getItem(1).isEmpty()) return;
                screen.onClose();
            }
            if (action == 3 && (!tile.getItem(1).isEmpty() || mc.player.getInventory().items.stream().noneMatch(
                    stack -> stack.is(NativeBuilderPartsClientFixture.product(machine).getItem())))) return;
            PacketDistributor.sendToServer(new NativeBuilderClientFixture.Mode(operation++));
            sent = false;
            ticks = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("[vandorlabs][builder-client] FAIL " + failure);
            mc.stop();
        }
    }

    private static boolean ready(Minecraft mc, Vec3 target, boolean battery) {
        if (mc.screen != null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getInventory().selected != 0) return false;
        var held = mc.player.getMainHandItem();
        if (battery ? NativeBatteryModule.energy(held) != 10000 : !held.isEmpty()) return false;
        if (mc.player.position().distanceToSqr(target.x, 96, -2.5) > .01) return false;
        return mc.player.getLookAngle().dot(target.subtract(mc.player.getEyePosition()).normalize()) > .9999;
    }

    private NativeBuilderPartsClientChecks() {}
}
