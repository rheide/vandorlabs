package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import static com.vandorlabs.neoforge.armor.NativeArmorFixture.require;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeArmorClientCheck {
    private static int ticks, stage, wait;
    private static volatile boolean captured;
    private static String capture;
    private static final int INDUSTRIAL_BLOCK = 9;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeArmorFixture.enabled()) return;
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || game.gameMode == null || game.getOverlay() != null) return;
        require(++ticks < 1000, "timeout stage=" + stage + " mode=" + game.gameMode.getPlayerMode()
            + " selected=" + player.getInventory().selected + " main=" + player.getMainHandItem()
            + " offhand=" + player.getOffhandItem());
        var offhand = player.getOffhandItem();
        switch (stage) {
            case 0 -> {
                if (game.gameMode.getPlayerMode() != GameType.SURVIVAL || player.getInventory().selected != 0
                    || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
                    || !(offhand.getItem() instanceof NativeArmorItem) || player.distanceToSqr(0.5, 79, 0.5) > 0.25) return;
                shift(game, true);
                game.gameMode.useItem(player, InteractionHand.OFF_HAND);
                stage++;
            }
            case 1 -> {
                if (!(game.screen instanceof NativeArmorScreen screen) || !screen.getMenu().synchronizedStack) return;
                if (!select(screen, "Industrial Block")) return;
                stage++;
            }
            case 2 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || !(offhand.getItem() instanceof NativeArmorItem armor)
                    || NativeArmorItem.choice(offhand) != INDUSTRIAL_BLOCK) return;
                var spriteName = ResourceLocation.parse(ModernFinishCatalog.option(INDUSTRIAL_BLOCK).texture());
                var expectedSprite = game.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(spriteName);
                var model = game.getItemRenderer().getModel(offhand, game.level, player, 0);
                require(model.getParticleIcon() == expectedSprite
                    && model.getQuads(null, null, net.minecraft.util.RandomSource.create()).stream()
                        .allMatch(quad -> quad.getSprite() == expectedSprite), "material geometry uses selected atlas sprite");
                var layers = armor.getMaterial().value().layers();
                var location = armor.getArmorTexture(offhand, player, EquipmentSlot.HEAD, layers.getFirst(), false);
                require(game.getTextureManager().getTexture(location) instanceof DynamicTexture dynamic
                    && dynamic.getPixels().getWidth() == 256 && dynamic.getPixels().getHeight() == 128
                    && (dynamic.getPixels().getPixelRGBA(0, 0) >>> 24) == 0, "derived armor skin allocates and preserves transparency");
                if (!select(screen, "Civilian Staff Helmet (Open)")) return;
                stage++;
            }
            case 3 -> {
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
                capture = "native-armor-picker.png";
                stage++;
            }
            case 4 -> {
                if (!captured || !(game.screen instanceof NativeArmorScreen screen)) return;
                screen.onClose();
                shift(game, false);
                wait = ticks;
                stage++;
            }
            case 5 -> {
                if (game.screen != null || ticks < wait + 12) return;
                game.gameMode.useItem(player, InteractionHand.OFF_HAND);
                wait = ticks;
                stage++;
            }
            case 6 -> {
                if (ticks < wait + 12 || !(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NativeArmorItem)) return;
                require(NativeArmorItem.design(player.getItemBySlot(EquipmentSlot.HEAD)).choice() == NativeArmorFixture.SELECTED,
                    "normal right-click equips edited offhand piece");
                game.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                captured = false;
                capture = "native-armor-equipped.png";
                stage++;
            }
            case 7 -> {
                if (!captured) return;
                System.out.println("[vandorlabs][armor] client PASS");
                game.stop();
                stage++;
            }
        }
    }

    private static boolean select(NativeArmorScreen screen, String label) {
        var search = screen.children().stream().filter(EditBox.class::isInstance)
            .map(EditBox.class::cast).findFirst().orElse(null);
        if (search == null) return false;
        screen.mouseClicked(search.getX() + 10, search.getY() + 8, 0);
        search.setValue("");
        for (char c : label.toCharArray()) screen.charTyped(c, 0);
        require(search.getValue().equals(label), "native search input");
        screen.mouseClicked(search.getX() + 30, search.getY() + 31, 0);
        return true;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var game = Minecraft.getInstance();
        String name = capture;
        capture = null;
        Screenshot.grab(game.gameDirectory, name, game.getMainRenderTarget(), message -> captured = true);
    }

    private static void shift(Minecraft game, boolean pressed) {
        game.player.input.shiftKeyDown = pressed;
        game.player.setShiftKeyDown(pressed);
        game.player.connection.send(new ServerboundPlayerCommandPacket(game.player, pressed
            ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
}
