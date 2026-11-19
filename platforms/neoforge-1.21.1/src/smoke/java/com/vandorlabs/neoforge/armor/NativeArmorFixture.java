package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.NativeTriggerBlockEntity;
import com.vandorlabs.neoforge.door.NativeDoorLifecycle;
import com.vandorlabs.neoforge.door.NativeProgrammableDoorBlock;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.light.NativeLightArtwork;
import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.light.NativeLightEntity;
import com.vandorlabs.neoforge.light.NativeLightRegistrations;
import com.vandorlabs.neoforge.light.NativeLightService;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.neoforge.door.NativeDoorRegistrations;
import com.vandorlabs.neoforge.copifier.NativeCopifierOptions;
import com.vandorlabs.neoforge.copifier.NativeCopifierRegistrations;
import com.vandorlabs.persistence.HousingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeArmorFixture {
    static boolean enabled() { return Boolean.getBoolean("vandorlabs.armorClient"); }
    static final String NAME = "Fixture Helmet";
    static final int SELECTED = 268435498, SHORT = 268435499;
    static final int COPY_TOOL_SLOT = 5, COPY_ARMOR_SLOT = 6;
    static final String COPY_ARMOR_NAME = "Copy Fixture Uniform";
    static final int INDUSTRIAL_BLOCK = 9;
    static final int CARGO_DOOR = 106;
    static final int CUBE_SLOT = 1, DOOR_SLOT = 2, INVALID_SLOT = 3;
    static final BlockPos LOG = new BlockPos(2, 79, 0), CUBE = new BlockPos(2, 79, 1);
    static final String LOG_TOP = "minecraft:block/oak_log_top", LOG_SIDE = "minecraft:block/oak_log";
    static final BlockPos WORLD_DOOR = new BlockPos(0, 79, 2), WORLD_TRIGGER = new BlockPos(0, 79, -2);
    static final BlockPos LIGHT_ON = new BlockPos(1, 79, -2), LIGHT_OFF = new BlockPos(2, 79, -2);
    static final String CUBE_SPRITE = "vandorlabs:housing/industrial_block";
    static String doorSprite() { return ModernFinishCatalog.option(CARGO_DOOR).texture(); }
    static String triggerSprite() { return ModernFinishCatalog.option(12).texture(); }
    static String lightHousing() { return ModernFinishCatalog.option(8).texture(); }
    static String lightSprite(boolean on) {
        int material = NativeLightArtwork.defaultFace(0);
        return ModernFinishCatalog.option(on ? material : NativeLightArtwork.unlit(material)).texture();
    }

    static final EquipmentSlot[] MOUNTED_SLOTS = {
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };
    private static final ArmorStand[] STANDS = new ArmorStand[2];

    static String mountedName(int stand, EquipmentSlot slot) {
        return (stand == 0 ? "Full " : "Small ") + slot.getName();
    }


    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var helmet = NativeArmorBindings.IDS.get(ArmorItem.Type.HELMET);
        var item = BuiltInRegistries.ITEM.get(helmet);
        var stack = new ItemStack(item);
        require(stack.getMaxDamage() == 363 && ((ArmorItem)item).getDefense() == 3
            && ((ArmorItem)item).getToughness() == 2 && item.isValidRepairItem(stack, new ItemStack(Items.DIAMOND)),
            "diamond behavior");
        require(player.serverLevel().getRecipeManager().byKey(helmet).isPresent(), "registered helmet craft");
        stack.setDamageValue(24);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(0.5, 79, 0.5);
        var world = player.serverLevel();
        world.setBlock(player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(LOG, Blocks.OAK_LOG.defaultBlockState(), 3);
        world.setBlock(CUBE, VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState()
            .setValue(ProgrammableBlock.FACING, Direction.EAST), 3);
        var cube = (ProgrammableBlockEntity)world.getBlockEntity(CUBE);
        cube.apply(new ProgrammableBlockDialog.State(8, 1, true, new int[]{-1, -1, -1, -1, INDUSTRIAL_BLOCK, -1}, -1));
        world.setBlockAndUpdate(LIGHT_ON, NativeLightRegistrations.BLOCK.get().defaultBlockState()
                .setValue(NativeLightBlock.FACING, Direction.SOUTH));
        world.setBlockAndUpdate(LIGHT_OFF, NativeLightRegistrations.FRAME.get().defaultBlockState()
                .setValue(NativeLightBlock.FACING, Direction.SOUTH));
        setLight(world, LIGHT_ON, true);
        setLight(world, LIGHT_OFF, false);
        placeDoor(world);
        world.setBlockAndUpdate(WORLD_TRIGGER, VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState()
                .setValue(ProgrammableBlock.FACING, Direction.SOUTH));
        var trigger = (NativeTriggerBlockEntity) world.getBlockEntity(WORLD_TRIGGER);
        var previous = trigger.triggerState();
        require(trigger.configure(0, new TriggerState(12, false, -1, 12, 12, false, 12,
                previous.artworkModes(), previous.artworkSpeeds()), ChannelList.EMPTY), "Trigger fixture configured");
        world.setBlockAndUpdate(WORLD_TRIGGER.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        trigger.tickLocalInput();
        require(trigger.signalLevel() > 0, "visible Trigger band selected by physical power");
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        var sampleCube = new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get(), 2);
        CustomData.update(DataComponents.CUSTOM_DATA, sampleCube, tag -> tag.putInt(HousingSettings.MAIN, INDUSTRIAL_BLOCK));
        var sampleDoor = new ItemStack(NativeDoorRegistrations.ITEM.get());
        var choices = new CompoundTag();
        choices.putInt("SpaceDesign", 7);
        choices.putInt("SpaceDetail", 1);
        CustomData.update(DataComponents.CUSTOM_DATA, sampleDoor, tag -> tag.put("SpaceDoorSettings", choices));
        player.getInventory().setItem(CUBE_SLOT, sampleCube);
        player.getInventory().setItem(DOOR_SLOT, sampleDoor);
        player.getInventory().setItem(INVALID_SLOT, new ItemStack(Items.DIAMOND));
        checkWorldAuthorization(player, stack);
        var inventoryEditor = new NativeArmorMenu(97, player.getInventory(), InteractionHand.OFF_HAND);
        inventoryEditor.sampleInventory(player, INVALID_SLOT);
        inventoryEditor.sampleInventory(player, 36);
        inventoryEditor.sampleInventory(player, -1);
        require(!NativeArmorItem.hasInventorySample(stack), "refuses non-block and out-of-range inventory samples");
        require(NativeArmorInventorySources.choice(sampleCube) == INDUSTRIAL_BLOCK
            && NativeArmorInventorySources.choice(sampleDoor) == CARGO_DOOR, "configured inventory choices");
        var tool = new ItemStack(NativeCopifierRegistrations.ITEM.get());
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("UnrelatedToolData", "retained"));
        player.getInventory().setItem(COPY_TOOL_SLOT, tool);
        var carried = new ItemStack(item);
        require(NativeArmorItem.configure(carried, SELECTED), "configure Open role source for held copy");
        carried.setDamageValue(17);
        carried.set(DataComponents.CUSTOM_NAME, Component.literal(COPY_ARMOR_NAME));
        carried.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 1);
        CustomData.update(DataComponents.CUSTOM_DATA, carried, tag -> tag.putString("UnrelatedArmorData", "retained"));
        player.getInventory().setItem(COPY_ARMOR_SLOT, carried);
        checkAppearanceCopy(carried);
        var chestId = NativeArmorBindings.IDS.get(ArmorItem.Type.CHESTPLATE);
        var chest = new ItemStack(BuiltInRegistries.ITEM.get(chestId));
        require(NativeArmorItem.configure(chest, INDUSTRIAL_BLOCK), "material fixture chestplate");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        populateStand(player, 0);
        populateStand(player, 1);
        checkStandAuthorization(player);
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var equipped = player.getItemBySlot(EquipmentSlot.HEAD);
        require(equipped.getItem() instanceof NativeArmorItem && NativeArmorItem.design(equipped).choice() == SELECTED,
            "server equipped edited offhand stack");
        var restored = ItemStack.parseOptional(player.registryAccess(), (CompoundTag)equipped.save(player.registryAccess()));
        require(ItemStack.matches(restored, equipped) && restored.getDamageValue() == 24
            && restored.getHoverName().getString().equals(NAME), "saved armor selection, damage and name");
        require(player.getInventory().getItem(CUBE_SLOT).getCount() == 2
            && NativeArmorInventorySources.choice(player.getInventory().getItem(CUBE_SLOT)) == INDUSTRIAL_BLOCK
            && player.getInventory().getItem(DOOR_SLOT).getCount() == 1
            && NativeArmorInventorySources.choice(player.getInventory().getItem(DOOR_SLOT)) == CARGO_DOOR,
            "inventory sample sources preserved");
        for (int index = 0; index < STANDS.length; index++) {
            var stand = STANDS[index];
            for (var slot : MOUNTED_SLOTS) {
                var mounted = stand.getItemBySlot(slot);
                int choice = index == 0 && slot == EquipmentSlot.CHEST ? SHORT : INDUSTRIAL_BLOCK;
                require(NativeArmorItem.choice(mounted) == choice && mounted.getDamageValue() == 11
                    && mounted.hasFoil() && mounted.getHoverName().getString().equals(mountedName(index, slot)),
                    "mounted armor preserves other data index=" + index + " slot=" + slot);
            }
        }
        var copyTarget = player.getOffhandItem();
        require(NativeArmorCopy.supports(copyTarget) && NativeArmorItem.choice(copyTarget) == INDUSTRIAL_BLOCK
            && copyTarget.getDamageValue() == 17 && copyTarget.hasFoil()
            && copyTarget.getHoverName().getString().equals(COPY_ARMOR_NAME)
            && copyTarget.get(DataComponents.CUSTOM_DATA).copyTag().getString("UnrelatedArmorData").equals("retained"),
            "mounted-to-offhand transfer preserves target metadata");
        System.out.println("[vandorlabs][armor] server PASS");
    }

    private static void checkAppearanceCopy(ItemStack source) {
        var target = source.copy();
        var mutable = source.copy();
        NativeArmorItem.applyWorldSample(mutable, LOG_TOP);
        require(NativeArmorCopy.apply(target, NativeArmorCopy.capture(mutable))
            && LOG_TOP.equals(NativeArmorItem.worldSample(target)), "world sprite appearance round trip");
        NativeArmorItem.applyInventorySample(mutable, CARGO_DOOR);
        var captured = NativeArmorCopy.capture(mutable);
        require(NativeArmorCopy.apply(target, captured) && NativeArmorItem.hasInventorySample(target)
            && NativeArmorItem.choice(target) == CARGO_DOOR, "inventory sample appearance round trip");
        var disabled = NativeCopifierOptions.maskWords(null);
        var options = com.vandorlabs.items.DuplifierOptionCatalog.options();
        for (int i = 0; i < options.length; i++) {
            if (options[i].key.equals("armorAppearance")) disabled[i >>> 6] &= ~(1L << (i & 63));
        }
        require(!NativeArmorCopy.apply(target, NativeCopifierOptions.selected(captured, disabled)),
            "existing Copifier option mask disables armor application");
        for (var design : NativeArmorCatalog.designs()) {
            for (var slot : MOUNTED_SLOTS) {
                var alternate = NativeArmorCatalog.matchingSlot(design.choice(), slot);
                String variant = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? design.variant() : "full";
                require(alternate != null && alternate.slot() == slot && alternate.role().equals(design.role())
                    && alternate.variant().equals(variant), "every authored role and applicable variant maps to every slot");
            }
        }
    }

    private static void checkWorldAuthorization(ServerPlayer player, ItemStack stack) {
        require(!NativeArmorWorldSampling.complete(player, new NativeArmorWorldSampling.Result(1, LOG, Direction.UP, LOG_TOP)),
            "world sprite cannot be submitted without an actual click");
        player.setShiftKeyDown(true);
        var log = new BlockHitResult(Vec3.atCenterOf(LOG).add(0, .5, 0), Direction.UP, LOG, false);
        var malformed = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, log);
        require(malformed != null && !NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(malformed.id(), LOG, Direction.UP, "minecraft:block/../invalid")),
            "malformed sprite cannot use a valid ticket");
        var old = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, log);
        var latest = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, log);
        require(old != null && latest != null && !NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(old.id(), LOG, Direction.UP, LOG_TOP)), "superseded ticket");
        require(!NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(latest.id(), LOG, Direction.NORTH, LOG_SIDE)), "wrong face");
        var replaced = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, log);
        player.setItemInHand(InteractionHand.OFF_HAND, stack.copy());
        require(replaced != null && !NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(replaced.id(), LOG, Direction.UP, LOG_TOP)), "replacement stack rejected");
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        var cube = new BlockHitResult(Vec3.atCenterOf(CUBE).add(0, 0, -.5), Direction.NORTH, CUBE, false);
        var forged = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, cube);
        require(forged != null && !NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(forged.id(), CUBE, Direction.NORTH, LOG_SIDE)), "cube sprite mismatch rejected");
        var lightHit = new BlockHitResult(Vec3.atCenterOf(LIGHT_ON).add(0, 0, .5), Direction.SOUTH, LIGHT_ON, false);
        var light = NativeArmorWorldSampling.begin(player, InteractionHand.MAIN_HAND, lightHit);
        setLight(player.serverLevel(), LIGHT_ON, false);
        require(light != null && !NativeArmorWorldSampling.complete(player,
            new NativeArmorWorldSampling.Result(light.id(), LIGHT_ON, Direction.SOUTH, lightSprite(true))),
            "Light changed activity while the ticket was pending");
        setLight(player.serverLevel(), LIGHT_ON, true);
        require(NativeArmorItem.worldSample(stack) == null, "negative claims preserve original armor");
        player.setShiftKeyDown(false);
    }

    private static void placeDoor(ServerLevel world) {
        world.setBlockAndUpdate(WORLD_DOOR.below(), Blocks.STONE.defaultBlockState());
        var state = NativeDoorRegistrations.ORDINARY.get().defaultBlockState()
                .setValue(NativeProgrammableDoorBlock.FACING, Direction.NORTH);
        world.setBlock(WORLD_DOOR, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
        world.setBlock(WORLD_DOOR.above(), state.setValue(NativeProgrammableDoorBlock.HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
        var door = NativeDoorLifecycle.root(world, WORLD_DOOR.above());
        require(door != null, "ordinary Door root available from upper part");
        var data = door.saveWithFullMetadata(world.registryAccess());
        data.putInt("SpaceDesign", 7);
        data.putInt("SpaceDetail", 1);
        door.loadWithComponents(data, world.registryAccess());
        door.setChanged();
    }

    private static void setLight(ServerLevel world, BlockPos pos, boolean lit) {
        var tile = (NativeLightEntity) world.getBlockEntity(pos);
        var data = tile.saveWithFullMetadata(world.registryAccess());
        data.putBoolean("LightOn", lit);
        data.putInt(HousingSettings.MAIN, 8);
        tile.loadWithComponents(data, world.registryAccess());
        NativeLightService.refresh(tile);
        require(tile.visual().on() == lit, "Light fixture mode settled");
    }

    private static void checkStandAuthorization(ServerPlayer player) {
        var stand = STANDS[0];
        var original = stand.getItemBySlot(EquipmentSlot.HEAD);
        var menu = new NativeArmorMenu(98, player.getInventory(), stand, EquipmentSlot.HEAD);
        require(menu.stillValid(player), "valid mounted session");
        var tool = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(!menu.stillValid(player), "missing Configurizer");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        stand.setItemSlot(EquipmentSlot.HEAD, original.copy());
        require(!menu.stillValid(player), "replaced mounted owner");
        stand.setItemSlot(EquipmentSlot.HEAD, original);
        var position = player.position();
        player.setPos(position.x + 20, position.y, position.z);
        require(!menu.stillValid(player), "out-of-range mounted owner");
        player.setPos(position.x, position.y, position.z);
        require(menu.stillValid(player), "restored mounted session");
    }

    private static void populateStand(ServerPlayer player, int index) {
        var world = player.serverLevel();
        var stand = new ArmorStand(world, 0.5, 79, index == 0 ? 2.5 : -1.5);
        if (index != 0) {
            var tag = stand.saveWithoutId(new CompoundTag());
            tag.putBoolean("Small", true);
            stand.load(tag);
        }
        stand.setNoGravity(true);
        var protection = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        for (var entry : NativeArmorBindings.IDS.entrySet()) {
            var slot = entry.getKey().getSlot();
            var item = new ItemStack(BuiltInRegistries.ITEM.get(entry.getValue()));
            item.setDamageValue(11);
            item.set(DataComponents.CUSTOM_NAME, Component.literal(mountedName(index, slot)));
            item.enchant(protection, 1);
            stand.setItemSlot(slot, item);
        }
        require(world.addFreshEntity(stand), "spawn mounted armor fixture index=" + index);
        STANDS[index] = stand;
    }

    static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Native armor: " + description);
    }
}
