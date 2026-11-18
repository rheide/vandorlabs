package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.vandorlabs.neoforge.armor.NativeArmorFixture.require;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeArmorClientCheck {
    private static int ticks, stage, wait;
    private static volatile boolean captured;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeArmorFixture.enabled()) return;
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || game.gameMode == null) return;
        require(++ticks < 1000, "timeout stage=" + stage + " mode=" + game.gameMode.getPlayerMode()
            + " selected=" + player.getInventory().selected + " main=" + player.getMainHandItem()
            + " offhand=" + player.getOffhandItem());
        var offhand = player.getOffhandItem();
        switch (stage) {
            case 0 -> {
                if (game.gameMode.getPlayerMode() != GameType.SURVIVAL || player.getInventory().selected != 0
                    || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
                    || !(offhand.getItem() instanceof NativeArmorItem) || player.getY() < 77) return;
                shift(game, true);
                game.gameMode.useItem(player, InteractionHand.OFF_HAND);
                stage++;
            }
            case 1 -> {
                if (!(game.screen instanceof NativeArmorScreen screen) || !screen.getMenu().synchronizedStack) return;
                var search = screen.children().stream().filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast).findFirst().orElse(null);
                if (search == null) return;
                screen.mouseClicked(search.getX() + 10, search.getY() + 8, 0);
                for (char c : "Civilian Staff Helmet (Open)".toCharArray()) screen.charTyped(c, 0);
                require(search.getValue().equals("Civilian Staff Helmet (Open)"), "native search input");
                screen.mouseClicked(search.getX() + 30, search.getY() + 31, 0);
                stage++;
            }
            case 2 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || !(offhand.getItem() instanceof NativeArmorItem)
                    || NativeArmorItem.design(offhand).choice() != NativeArmorFixture.SELECTED) return;
                var armor = (NativeArmorItem)offhand.getItem();
                var selected = NativeArmorItem.design(offhand);
                require(armor.getArmorTexture(offhand, player, EquipmentSlot.HEAD, null, false).equals(selected.worn())
                    && game.getResourceManager().getResource(selected.worn()).isPresent(), "equipped texture resource");
                var baked = game.getItemRenderer().getModel(offhand, game.level, player, 0);
                require(baked.getParticleIcon().contents().name().getPath().endsWith("civilian_staff_helmet_open"),
                    "selected authored inventory icon");
                Screenshot.grab(game.gameDirectory, "native-armor-picker.png", game.getMainRenderTarget(),
                    message -> captured = true);
                stage++;
            }
            case 3 -> {
                if (!captured || !(game.screen instanceof NativeArmorScreen screen)) return;
                screen.onClose();
                shift(game, false);
                wait = ticks;
                stage++;
            }
            case 4 -> {
                if (game.screen != null || ticks < wait + 12) return;
                game.gameMode.useItem(player, InteractionHand.OFF_HAND);
                wait = ticks;
                stage++;
            }
            case 5 -> {
                if (ticks < wait + 12 || !(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NativeArmorItem)) return;
                require(NativeArmorItem.design(player.getItemBySlot(EquipmentSlot.HEAD)).choice() == NativeArmorFixture.SELECTED,
                    "normal right-click equips edited offhand piece");
                System.out.println("[vandorlabs][armor] client PASS");
                game.stop();
                stage++;
            }
        }
    }

    private static void shift(Minecraft game, boolean pressed) {
        game.player.input.shiftKeyDown = pressed;
        game.player.setShiftKeyDown(pressed);
        game.player.connection.send(new ServerboundPlayerCommandPacket(game.player, pressed
            ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
}
