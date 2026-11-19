package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.neoforge.copifier.NativeCopifierClipboard;
import com.vandorlabs.neoforge.copifier.NativeCopifierItem;
import com.vandorlabs.neoforge.copifier.NativeCopifierRegistrations;
import com.vandorlabs.ui.InventorySampleLayout;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import static com.vandorlabs.neoforge.armor.NativeArmorFixture.require;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeArmorClientCheck {
    private static int ticks, stage = 20, wait;
    private static volatile boolean captured;
    private static String capture;
    private static ArmorStand mounted;
    private static int standCase;
    private static final int PORTHOLE_ON = 78, PORTHOLE_OFF = 256;
    private static final double[][] HIT_HEIGHT = {
        {.25, .7, 1.3, 1.8}, {.2, .5, .7, .97}
    };

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NativeArmorFixture.enabled()) return;
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || game.gameMode == null || game.getOverlay() != null) return;
        require(++ticks < 1000, "timeout stage=" + stage + " mounted=" + standCase + " mode=" + game.gameMode.getPlayerMode()
            + " selected=" + player.getInventory().selected + " main=" + player.getMainHandItem()
            + " offhand=" + player.getOffhandItem());
        var offhand = player.getOffhandItem();
        switch (stage) {
            case 30 -> {
                if (ticks < wait + 8 || !NativeArmorCopy.supports(player.getMainHandItem()) || !offhand.isEmpty()) return;
                swapHands(game);
                stage++;
            }
            case 31 -> {
                if (!NativeArmorCopy.supports(offhand) || NativeArmorItem.choice(offhand) != NativeArmorFixture.SELECTED) return;
                selectHotbar(game, NativeArmorFixture.COPY_TOOL_SLOT);
                wait = ticks;
                stage++;
            }
            case 32 -> {
                if (ticks < wait + 8 || !player.getMainHandItem().is(NativeCopifierRegistrations.ITEM.get())) return;
                shift(game, true);
                game.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                stage++;
            }
            case 33 -> {
                var root = NativeCopifierItem.data(player.getMainHandItem());
                var clipboard = NativeCopifierClipboard.settings(root);
                if (clipboard == null || clipboard.getCompound("armorAppearance").getInt("choice") != NativeArmorFixture.SELECTED) return;
                require(clipboard.size() == 1 && root.getString("UnrelatedToolData").equals("retained"),
                    "real held clipboard contains appearance and keeps unrelated tool data");
                mounted = armorStand(game, false);
                if (mounted == null) return;
                shift(game, false);
                clickStand(game, mounted, 1.3);
                stage++;
            }
            case 34 -> {
                var chest = mounted.getItemBySlot(EquipmentSlot.CHEST);
                if (NativeArmorItem.choice(chest) != NativeArmorFixture.SHORT) return;
                require(chest.getDamageValue() == 11 && chest.hasFoil()
                    && chest.getHoverName().getString().equals(NativeArmorFixture.mountedName(0, EquipmentSlot.CHEST)),
                    "Open helmet maps to Short Sleeves while preserving mounted item");
                require(NativeArmorItem.choice(offhand) == NativeArmorFixture.SELECTED && offhand.getDamageValue() == 17,
                    "applying to the mounted chest keeps the held source");
                mounted = armorStand(game, true);
                if (mounted == null) return;
                shift(game, true);
                clickStand(game, mounted, .5);
                stage++;
            }
            case 35 -> {
                var clipboard = NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));
                if (clipboard == null
                    || clipboard.getCompound("armorAppearance").getInt("choice") != NativeArmorFixture.INDUSTRIAL_BLOCK) return;
                shift(game, false);
                game.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                stage++;
            }
            case 36 -> {
                if (!NativeArmorCopy.supports(offhand) || NativeArmorItem.choice(offhand) != NativeArmorFixture.INDUSTRIAL_BLOCK) return;
                require(game.screen == null && offhand.getDamageValue() == 17 && offhand.hasFoil()
                    && offhand.getHoverName().getString().equals(NativeArmorFixture.COPY_ARMOR_NAME),
                    "real mounted-to-offhand apply preserves armor metadata");
                System.out.println("[vandorlabs][armor] client PASS");
                game.stop();
                stage = 99;
            }
            case 20 -> {
                if (game.gameMode.getPlayerMode() != GameType.SURVIVAL || player.getInventory().selected != 0
                    || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
                    || !(offhand.getItem() instanceof NativeArmorItem) || player.distanceToSqr(.5, 79, .5) > .25
                    || !game.level.getBlockState(NativeArmorFixture.LOG).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) return;
                sampleFace(game, NativeArmorFixture.LOG, Direction.UP);
                stage++;
            }
            case 21 -> {
                if (!NativeArmorFixture.LOG_TOP.equals(NativeArmorItem.worldSample(offhand))) return;
                require(game.screen == null, "Configurator world-face click does not open a configuration menu");
                swapHands(game);
                stage++;
            }
            case 22 -> {
                var held = player.getMainHandItem();
                if (!(held.getItem() instanceof NativeArmorItem)
                    || !NativeArmorFixture.LOG_TOP.equals(NativeArmorItem.worldSample(held))) return;
                sampleFace(game, NativeArmorFixture.LOG, Direction.NORTH);
                stage++;
            }
            case 23 -> {
                var held = player.getMainHandItem();
                if (!NativeArmorFixture.LOG_SIDE.equals(NativeArmorItem.worldSample(held))) return;
                require(held.getDamageValue() == 24 && held.getHoverName().getString().equals(NativeArmorFixture.NAME),
                    "direct survival face sample preserves held metadata");
                swapHands(game);
                stage++;
            }
            case 24 -> {
                if (!player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
                    || !NativeArmorFixture.LOG_SIDE.equals(NativeArmorItem.worldSample(offhand))) return;
                if (!game.level.getBlockState(NativeArmorFixture.LIGHT_ON)
                        .is(com.vandorlabs.neoforge.light.NativeLightRegistrations.BLOCK.get())) return;
                sampleFace(game, NativeArmorFixture.LIGHT_ON, Direction.SOUTH);
                stage = 26;
            }
            case 26 -> {
                String expected = NativeArmorFixture.lightSprite(true);
                if (!expected.equals(NativeArmorItem.worldSample(offhand))) return;
                var model = game.getItemRenderer().getModel(offhand, game.level, player, 0);
                require(model.getParticleIcon().contents().name().toString().equals(expected),
                    "lit renderer-owned artwork reaches the fitted armor model");
                sampleFace(game, NativeArmorFixture.LIGHT_OFF, Direction.SOUTH);
                stage++;
            }
            case 27 -> {
                if (!NativeArmorFixture.lightSprite(false).equals(NativeArmorItem.worldSample(offhand))) return;
                sampleFace(game, NativeArmorFixture.LIGHT_ON, Direction.UP);
                stage++;
            }
            case 28 -> {
                if (!NativeArmorFixture.lightHousing().equals(NativeArmorItem.worldSample(offhand))) return;
                System.out.println("[vandorlabs][armor] rendered world Light On, Off and housing samples PASS");
                sampleFace(game, NativeArmorFixture.WORLD_DOOR.above(), Direction.NORTH);
                stage = 29;
            }
            case 29 -> {
                if (!NativeArmorFixture.doorSprite().equals(NativeArmorItem.worldSample(offhand))) return;
                sampleFace(game, NativeArmorFixture.WORLD_TRIGGER, Direction.SOUTH);
                stage = 37;
            }
            case 37 -> {
                if (!NativeArmorFixture.triggerSprite().equals(NativeArmorItem.worldSample(offhand))) return;
                System.out.println("[vandorlabs][armor] ordinary Door upper leaf and powered Trigger samples PASS");
                sampleFace(game, NativeArmorFixture.CUBE, Direction.NORTH);
                stage = 25;
            }
            case 25 -> {
                if (!NativeArmorFixture.CUBE_SPRITE.equals(NativeArmorItem.worldSample(offhand))) return;
                var baked = game.getItemRenderer().getModel(offhand, game.level, player, 0);
                require(baked.getParticleIcon().contents().name().toString().equals(NativeArmorFixture.CUBE_SPRITE),
                    "rotated configured face override reaches actual held model");
                var restored = net.minecraft.world.item.ItemStack.parseOptional(player.registryAccess(),
                    (net.minecraft.nbt.CompoundTag)offhand.save(player.registryAccess()));
                require(NativeArmorFixture.CUBE_SPRITE.equals(NativeArmorItem.worldSample(restored)),
                    "world sample survives native stack serialization");
                stage = 0;
            }
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
                require(NativeArmorFixture.CUBE_SPRITE.equals(NativeArmorItem.worldSample(screen.getMenu().editorStack())),
                    "picker opens with the sampled face");
                // The same name appears first under Sampled, then as the ordinary catalog option.
                if (!select(screen, "Industrial Block", 1)) return;
                stage++;
            }
            case 2 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || !(offhand.getItem() instanceof NativeArmorItem armor)
                    || NativeArmorItem.choice(offhand) != NativeArmorFixture.INDUSTRIAL_BLOCK) return;
                require(NativeArmorItem.worldSample(offhand) == null, "explicit catalog material replaces a world sample");
                var spriteName = ResourceLocation.parse(ModernFinishCatalog.option(NativeArmorFixture.INDUSTRIAL_BLOCK).texture());
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
                if (!clickInventory(screen, NativeArmorFixture.CUBE_SLOT)) return;
                stage = 10;
            }
            case 10 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || !NativeArmorItem.hasInventorySample(screen.getMenu().editorStack())
                    || NativeArmorItem.choice(offhand) != NativeArmorFixture.INDUSTRIAL_BLOCK) return;
                require(player.getInventory().getItem(NativeArmorFixture.CUBE_SLOT).getCount() == 2,
                    "configured block sample keeps source count");
                if (!clickInventory(screen, NativeArmorFixture.DOOR_SLOT)) return;
                stage = 11;
            }
            case 11 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || NativeArmorItem.choice(screen.getMenu().editorStack()) != NativeArmorFixture.CARGO_DOOR
                    || NativeArmorItem.choice(offhand) != NativeArmorFixture.CARGO_DOOR) return;
                var restored = net.minecraft.world.item.ItemStack.parseOptional(player.registryAccess(),
                    (net.minecraft.nbt.CompoundTag)offhand.save(player.registryAccess()));
                require(NativeArmorItem.hasInventorySample(restored)
                    && NativeArmorItem.choice(restored) == NativeArmorFixture.CARGO_DOOR,
                    "sample state survives native item serialization");
                var model = game.getItemRenderer().getModel(offhand, game.level, player, 0);
                require(model.getParticleIcon().contents().name().toString()
                    .equals(NativeDoorAssets.preview(NativeArmorFixture.CARGO_DOOR).texture()),
                    "configured door sample resolves full authored image");
                screen.onClose();
                wait = ticks;
                stage = 12;
            }
            case 12 -> {
                if (ticks < wait + 8 || game.screen != null || !NativeArmorItem.hasInventorySample(offhand)) return;
                shift(game, true);
                game.gameMode.useItem(player, InteractionHand.OFF_HAND);
                stage = 13;
            }
            case 13 -> {
                if (!(game.screen instanceof NativeArmorScreen screen) || !screen.getMenu().synchronizedStack) return;
                var saved = screen.getMenu().editorStack();
                require(NativeArmorItem.hasInventorySample(saved)
                    && NativeArmorItem.choice(saved) == NativeArmorFixture.CARGO_DOOR,
                    "sample appearance survives closing and reopening the picker");
                if (!select(screen, "Porthole Off")) return;
                stage = 14;
            }
            case 14 -> {
                if (!(game.screen instanceof NativeArmorScreen screen) || NativeArmorItem.choice(offhand) != PORTHOLE_OFF) return;
                lightTexture(game, offhand, PORTHOLE_OFF);
                if (!select(screen, "Porthole On")) return;
                stage = 15;
            }
            case 15 -> {
                if (!(game.screen instanceof NativeArmorScreen screen) || NativeArmorItem.choice(offhand) != PORTHOLE_ON) return;
                lightTexture(game, offhand, PORTHOLE_ON);
                if (!select(screen, "Civilian Staff Helmet (Open)")) return;
                stage = 3;
            }
            case 3 -> {
                if (!(game.screen instanceof NativeArmorScreen screen)
                    || !(offhand.getItem() instanceof NativeArmorItem)
                    || NativeArmorItem.design(offhand).choice() != NativeArmorFixture.SELECTED) return;
                require(!NativeArmorItem.hasInventorySample(offhand), "explicit design replaces inventory sample");
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
                if (standCase == 8) {
                    selectHotbar(game, NativeArmorFixture.COPY_ARMOR_SLOT);
                    wait = ticks;
                    stage = 30;
                    return;
                }
                if (game.screen != null || player.distanceToSqr(0.5, 79, 0.5) > 0.25
                    || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())) return;
                boolean small = standCase >= 4;
                mounted = armorStand(game, small);
                if (mounted == null) return;
                int slot = standCase % 4;
                clickStand(game, mounted, HIT_HEIGHT[standCase / 4][slot]);
                stage++;
            }
            case 8 -> {
                var slot = NativeArmorFixture.MOUNTED_SLOTS[standCase % 4];
                if (!(game.screen instanceof NativeArmorScreen screen) || !screen.getMenu().synchronizedStack) return;
                require(screen.getMenu().editorStack().getHoverName().getString()
                    .equals(NativeArmorFixture.mountedName(standCase / 4, slot)), "mounted picker title case " + standCase);
                if (!select(screen, "Industrial Block")) return;
                stage++;
            }
            case 9 -> {
                var slots = NativeArmorFixture.MOUNTED_SLOTS;
                var stack = mounted.getItemBySlot(slots[standCase % 4]);
                if (NativeArmorItem.choice(stack) != NativeArmorFixture.INDUSTRIAL_BLOCK) return;
                for (int i = 0; i < slots.length; i++) {
                    var existing = mounted.getItemBySlot(slots[i]);
                    int choice = i > standCase % 4
                        ? NativeArmorCatalog.defaultDesign(slots[i]).choice() : NativeArmorFixture.INDUSTRIAL_BLOCK;
                    require(NativeArmorItem.choice(existing) == choice && existing.getDamageValue() == 11 && existing.hasFoil(),
                        "only targeted mounted equipment changes case=" + standCase + " slot=" + slots[i]);
                }
                if (!(game.screen instanceof NativeArmorScreen screen)) return;
                screen.onClose();
                standCase++;
                stage = 7;
            }
        }
    }

    private static void lightTexture(Minecraft game, net.minecraft.world.item.ItemStack stack, int choice) {
        var expected = ModernFinishCatalog.option(choice).texture();
        var model = game.getItemRenderer().getModel(stack, game.level, game.player, 0);
        require(model.getParticleIcon().contents().name().toString().equals(expected),
            "static Light option uses its exact atlas sprite");
        var item = (NativeArmorItem)stack.getItem();
        var texture = item.getArmorTexture(stack, game.player, EquipmentSlot.HEAD,
            item.getMaterial().value().layers().getFirst(), false);
        require(game.getTextureManager().getTexture(texture) instanceof DynamicTexture, "static Light option produces a worn skin");
    }

    private static void selectHotbar(Minecraft game, int slot) {
        game.player.getInventory().selected = slot;
        game.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static ArmorStand armorStand(Minecraft game, boolean small) {
        var stands = game.level.getEntitiesOfClass(ArmorStand.class, game.player.getBoundingBox().inflate(3),
            entity -> entity.isSmall() == small);
        return stands.size() == 1 ? stands.getFirst() : null;
    }

    private static void clickStand(Minecraft game, ArmorStand stand, double height) {
        var hit = new EntityHitResult(stand, stand.position().add(0, height, 0));
        require(game.gameMode.interactAt(game.player, stand, hit, InteractionHand.MAIN_HAND).consumesAction(),
            "actual mounted tool interaction at height " + height);
    }

    private static void sampleFace(Minecraft game, BlockPos pos, Direction face) {
        shift(game, true);
        var point = Vec3.atCenterOf(pos).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
        var hit = new BlockHitResult(point, face, pos, false);
        require(game.gameMode.useItemOn(game.player, InteractionHand.MAIN_HAND, hit).consumesAction(),
            "normal world-face interaction " + pos + " " + face);
    }

    private static void swapHands(Minecraft game) {
        game.player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    private static boolean clickInventory(NativeArmorScreen screen, int slot) {
        var search = search(screen);
        if (search == null) return false;
        search.setValue("no matching armor texture");
        // With no matching rows, Custom is the only visible row.
        screen.mouseClicked(search.getX() + 12, search.getY() + 31, 0);
        require(!search.visible, "Custom opens the shared inventory sample grid");
        screen.mouseClicked(search.getX() - 2 + InventorySampleLayout.CELL * slot + 8,
            search.getY() - 2 + InventorySampleLayout.HEADER + 8, 0);
        return true;
    }

    private static EditBox search(NativeArmorScreen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance)
            .map(EditBox.class::cast).findFirst().orElse(null);
    }

    private static boolean select(NativeArmorScreen screen, String label) {
        return select(screen, label, 0);
    }

    private static boolean select(NativeArmorScreen screen, String label, int row) {
        var search = search(screen);
        if (search == null) return false;
        screen.mouseClicked(search.getX() + 10, search.getY() + 8, 0);
        search.setValue("");
        for (char c : label.toCharArray()) screen.charTyped(c, 0);
        require(search.getValue().equals(label), "native search input");
        screen.mouseClicked(search.getX() + 30, search.getY() + 31 + row * 22, 0);
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
