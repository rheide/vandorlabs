package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.copier.client.NativeCopyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.vandorlabs.neoforge.copier.NativeCopyPlayerFixture.require;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeCopyPlayerChecks {
    private static int stage, ticks, ordinaryWindow;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeCopyPlayerFixture.enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen intro) {
            intro.onClose();
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        var player = mc.player;
        var root = NativeCopyPlayerFixture.ROOT;
        var screen = mc.screen instanceof NativeCopyScreen copy ? copy : null;
        var tile = screen == null ? null : screen.getMenu().tile;
        require(++ticks < 1000, "timeout at stage " + stage + ", mode " + mc.gameMode.getPlayerMode()
            + ", slot " + player.getInventory().selected + ", held " + player.getMainHandItem()
            + ", position " + player.position() + ", screen " + mc.screen
            + (tile == null ? "" : ", status " + tile.status() + ", error " + tile.error()));
        switch (stage) {
            case 0 -> {
                if (mc.screen != null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                    || player.getInventory().selected != 0 || !player.getMainHandItem().isEmpty()
                    || player.position().distanceToSqr(8.5, 96, 6.5) > .01
                    || !(mc.level.getBlockEntity(root) instanceof NativeCopyControllerEntity)) return;
                CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), false, mc.level.registryAccess());
                var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse("vandorlabs:vandorlabs"));
                var item = NativeCopyBindings.BLOCK.asItem();
                require(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1, "one public creative entry");
                require(CreativeModeTabs.searchTab().getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1,
                    "one public search entry");
                use(mc);
                stage = 1;
            }
            case 1 -> {
                if (screen == null || !tile.status().startsWith("Ready:")) return;
                ordinaryWindow = screen.getMenu().containerId;
                click(screen, 0, 1);
                stage = 2;
            }
            case 2 -> {
                if (screen == null || tile.settings().activation != 2) return;
                var height = screen.heightSlider();
                require(screen.mouseClicked(height.getX() + 5, height.getY() + 10, 0), "actual height slider click");
                screen.mouseReleased(height.getX() + 5, height.getY() + 10, 0);
                stage = 3;
            }
            case 3 -> {
                if (screen == null || tile.settings().height != 1) return;
                click(screen, 3, 0);
                stage = 4;
            }
            case 4 -> {
                if (mc.screen != null || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())) return;
                use(mc);
                stage = 5;
            }
            case 5 -> {
                if (screen == null) return;
                require(screen.getMenu().containerId != ordinaryWindow && tile.getBlockPos().equals(root)
                    && screen.heightSlider().getIntValue() == 1, "same controller and saved settings on tool route");
                click(screen, 2, 0);
                click(screen, 1, 0);
                stage = 6;
            }
            case 6 -> {
                if (screen == null || !tile.status().equals("Copy complete")
                    || !mc.level.getBlockState(NativeCopyPlayerFixture.OUTPUT.above()).is(Blocks.STONE)) return;
                require(!tile.busy() && tile.error().isEmpty(), "completed Copy clears busy and error");
                System.out.println("[vandorlabs][copy-player] client PASS publicCatalog/ordinary/Configurizer/previousCycle/height/copy");
                stage = 7;
                mc.stop();
            }
            default -> {}
        }
    }

    private static void use(Minecraft mc) {
        var root = NativeCopyPlayerFixture.ROOT;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(root).add(0, 0, -.5), Direction.NORTH, root, false));
    }

    private static void click(NativeCopyScreen screen, int id, int button) {
        var control = screen.control(id);
        require(screen.mouseClicked(control.getX() + control.getWidth() / 2D, control.getY() + 10, button),
            "actual dialog click for control " + id);
    }

    private NativeCopyPlayerChecks() {}
}
