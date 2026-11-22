package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.coupler.NativeCouplerBindings;
import com.vandorlabs.neoforge.coupler.client.NativeCouplerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeCouplerPlayerChecks {
    private static int stage, ticks;
    private static String capture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!NativeCouplerPlayerFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) {
            intro.onClose();
            return;
        }
        if (stage == 12 && mc.level == null) {
            System.out.println("[vandorlabs][builder-client] client PASS Coupler Square/Slab Survival/unified dialog/Engage");
            advance();
            mc.stop();
            return;
        }
        need(!(mc.screen instanceof DisconnectedScreen), "unexpected disconnect");
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        need(++ticks < 360, "timeout mode=" + mc.gameMode.getPlayerMode() + " slot=" + mc.player.getInventory().selected
                + " held=" + mc.player.getMainHandItem() + " position=" + mc.player.position() + " screen=" + mc.screen);
        var square = NativeCouplerPlayerFixture.SQUARE;
        var slab = NativeCouplerPlayerFixture.SLAB;
        var dialog = mc.screen instanceof NativeCouplerScreen screen ? screen : null;
        switch (stage) {
            case 0 -> useWhenReady(mc, square, NativeCouplerBindings.SQUARE.asItem(), true);
            case 1 -> {
                if (mc.level.getBlockState(square).is(NativeCouplerBindings.SQUARE) && mc.player.getMainHandItem().isEmpty())
                    acknowledge(0);
            }
            case 2 -> {
                if (acknowledged(mc, 0)) useWhenReady(mc, slab, NativeCouplerBindings.SLAB.asItem(), true);
            }
            case 3 -> {
                if (mc.level.getBlockState(slab).is(NativeCouplerBindings.SLAB) && mc.player.getMainHandItem().isEmpty())
                    acknowledge(1);
            }
            case 4 -> {
                if (acknowledged(mc, 1)) useWhenReady(mc, square, null, false);
            }
            case 5 -> {
                if (dialog == null) return;
                var field = dialog.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .findFirst().orElseThrow();
                field.setValue(NativeCouplerPlayerFixture.BANK.toString());
                click(dialog, "Lock when signal is on", 0);
                click(dialog, "Lock when signal is off", 1);
                click(dialog, "Engage Coupler", 0);
                advance();
            }
            case 6 -> {
                if (dialog == null || !dialog.getMenu().owner.engaged()) return;
                dialog.onClose();
                acknowledge(2);
            }
            case 7 -> {
                if (acknowledged(mc, 2)) useWhenReady(mc, square, VandorLabsBootstrap.CONFIGURIZER.get(), false);
            }
            case 8 -> {
                if (dialog == null) return;
                var owner = dialog.getMenu().owner;
                need(owner.getBlockPos().equals(square) && owner.engaged()
                        && owner.getRedstoneChannels().equals(NativeCouplerPlayerFixture.BANK),
                        "Configurizer opens the same engaged owner and displays the committed settings");
                capture = "native-coupler-menu";
                acknowledge(3);
            }
            case 9 -> {
                if (capture != null || !acknowledged(mc, 3) || dialog == null) return;
                dialog.onClose();
                advance();
            }
            case 10 -> {
                if (mc.screen != null) return;
                capture = "native-coupler-world";
                advance();
            }
            case 11 -> {
                if (capture != null) return;
                advance();
                mc.level.disconnect();
                mc.disconnect();
            }
            default -> {}
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        var path = mc.gameDirectory.toPath().resolve("screenshots");
        java.nio.file.Files.createDirectories(path);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(path.resolve(capture + ".png"));
        }
        capture = null;
    }

    private static void useWhenReady(Minecraft mc, BlockPos pos, Item item, boolean placing) {
        var player = mc.player;
        var held = player.getMainHandItem();
        var look = NativeCouplerPlayerFixture.target(pos, placing).subtract(player.getEyePosition()).normalize();
        if (mc.screen != null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                || player.getInventory().selected != 0 || (item == null ? !held.isEmpty() : !held.is(item))
                || player.position().distanceToSqr(NativeCouplerPlayerFixture.position(pos)) > .01
                || player.getLookAngle().dot(look) < .999) return;
        var picked = player.pick(player.blockInteractionRange(), 1, false);
        need(picked instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(placing ? pos.south() : pos), "expected physical aim, saw " + picked);
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, (BlockHitResult)picked);
        advance();
    }

    private static void click(NativeCouplerScreen dialog, String label, int button) {
        var widget = dialog.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow();
        need(dialog.mouseClicked(widget.getX() + widget.getWidth() / 2D, widget.getY() + 7, button),
                "real dialog click " + label);
    }

    private static void acknowledge(int index) {
        PacketDistributor.sendToServer(new NativeBuilderClientFixture.Mode(index));
        advance();
    }
    private static boolean acknowledged(Minecraft mc, int index) {
        return mc.level.getBlockState(NativeCouplerPlayerFixture.ack(index)).is(Blocks.EMERALD_BLOCK);
    }
    private static void advance() { stage++; ticks = 0; }
    private static void need(boolean ok, String why) {
        if (!ok) throw new AssertionError("Coupler client stage " + stage + ": " + why);
    }

    private NativeCouplerPlayerChecks() {}
}
