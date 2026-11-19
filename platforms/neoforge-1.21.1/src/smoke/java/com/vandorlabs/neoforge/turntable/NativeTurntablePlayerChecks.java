package com.vandorlabs.neoforge.turntable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeTurntablePlayerChecks {
    private static int stage;
    private static int ticks;
    private static int window;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!NativeTurntablePlayerFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) {
            intro.onClose();
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        var root = NativeTurntablePlayerFixture.ROOT;
        var screen = mc.screen instanceof NativeTurntableScreen panel ? panel : null;
        var menu = screen == null ? null : screen.getMenu();
        if (++ticks >= 1000) {
            throw new AssertionError("Turntable stage=" + stage + " mode=" + mc.gameMode.getPlayerMode()
                    + " slot=" + mc.player.getInventory().selected + " held=" + mc.player.getMainHandItem()
                    + " pos=" + mc.player.position() + " screen=" + screen + " status=" + (menu == null ? "" : menu.status()));
        }
        switch (stage) {
            case 0 -> {
                if (mc.screen != null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                        || !mc.player.getMainHandItem().isEmpty() || mc.player.getInventory().selected != 0
                        || mc.player.position().distanceToSqr(NativeTurntablePlayerFixture.ACTOR) > .02
                        || !(mc.level.getBlockEntity(root) instanceof NativeTurntableEntity)) return;
                use(mc);
                stage++;
            }
            case 1 -> {
                if (menu == null || menu.busy()) return;
                window = menu.containerId;
                click(screen, "Height:", .07);
                click(screen, "RPM:", .998);
                stage++;
            }
            case 2 -> {
                if (menu == null || menu.height() != 2 || menu.rpm() != 10) return;
                click(screen, "Scan", .5);
                stage++;
            }
            case 3 -> {
                if (menu == null || !menu.captured() || menu.busy() || !mc.level.getBlockState(NativeTurntablePlayerFixture.SOURCE).isAir()) return;
                if (menu.containerId != window) throw new AssertionError("Native Turntable window dropped its physical identity");
                checkCapturedRender(mc);
                click(screen, "Target angle:", .333);
                click(screen, "Turn", .5);
                stage++;
            }
            case 4 -> {
                if (menu == null || menu.moving() || !menu.status().equals("Turntable stopped at 90 degrees")) return;
                var owner = (NativeTurntableEntity) mc.level.getBlockEntity(root);
                if (owner.visualAngle() != 90) throw new AssertionError("Client has the wrong authoritative angle");
                owner.readAngle(owner.key(), Long.MIN_VALUE, 20);
                if (owner.visualAngle() != 90) throw new AssertionError("Older pose packet replaced the current angle");
                checkCapturedRender(mc);
                click(screen, "Restore", .5);
                stage++;
            }
            case 5 -> {
                if (menu == null || menu.captured() || menu.busy()
                        || !(mc.level.getBlockEntity(NativeTurntablePlayerFixture.TARGET) instanceof SignBlockEntity sign)
                        || !sign.getFrontText().getMessage(0, false).getString().equals("Turntable player sign")) return;
                click(screen, "Done", .5);
                mc.player.getInventory().selected = 1;
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(1));
                stage++;
            }
            case 6 -> {
                if (mc.screen != null || !mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())) return;
                use(mc);
                stage++;
            }
            case 7 -> {
                if (menu == null || menu.containerId == window || menu.height() != 2) return;
                System.out.println("[vandorlabs][turntable-player] client PASS ordinary/height/Scan/Turn/Restore/Configurator");
                stage++;
                mc.stop();
            }
            default -> {}
        }
    }

    private static void checkCapturedRender(Minecraft mc) {
        var root = (NativeTurntableEntity) mc.level.getBlockEntity(NativeTurntablePlayerFixture.ROOT);
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(root);
        if (!(renderer instanceof NativeTurntableRenderer turntable)
                || !turntable.getRenderBoundingBox(root).contains(NativeTurntablePlayerFixture.SOURCE.getCenter())) {
            throw new AssertionError("Captured body is missing from renderer bounds");
        }
        int[] vertices = {0};
        VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("addVertex")) vertices[0]++;
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        turntable.render(root, 0, new PoseStack(), layer -> sink, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (vertices[0] < 24) throw new AssertionError("Captured Sign produced only " + vertices[0] + " vertices");
        System.out.println("[vandorlabs][turntable-player] captured geometry PASS vertices=" + vertices[0]);
    }

    private static void use(Minecraft mc) {
        var root = NativeTurntablePlayerFixture.ROOT;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(root).add(0, 0, -.5), Direction.NORTH, root, false));
    }

    private static void click(NativeTurntableScreen screen, String label, double horizontal) {
        var control = screen.children().stream().filter(entry -> entry instanceof AbstractWidget widget
                && (widget.getMessage().getString().equals(label) || label.endsWith(":")
                && widget.getMessage().getString().startsWith(label))).map(AbstractWidget.class::cast)
                .findFirst().orElseThrow();
        double x = control.getX() + horizontal * control.getWidth();
        double y = control.getY() + 10;
        if (!screen.mouseClicked(x, y, 0)) throw new AssertionError("Turntable widget click refused: " + label);
        screen.mouseReleased(x, y, 0);
    }
}
