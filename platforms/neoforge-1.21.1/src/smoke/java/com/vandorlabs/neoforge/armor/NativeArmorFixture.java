package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.door.NativeDoorRegistrations;
import com.vandorlabs.persistence.HousingSettings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeArmorFixture {
    static boolean enabled() { return Boolean.getBoolean("vandorlabs.armorClient"); }
    static final String NAME = "Fixture Helmet";
    static final int SELECTED = 268435498;
    static final int INDUSTRIAL_BLOCK = 9;
    static final int CARGO_DOOR = 106;
    static final int CUBE_SLOT = 1, DOOR_SLOT = 2, INVALID_SLOT = 3;
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
        player.serverLevel().setBlock(player.blockPosition().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
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
        var inventoryEditor = new NativeArmorMenu(97, player.getInventory(), InteractionHand.OFF_HAND);
        inventoryEditor.sampleInventory(player, INVALID_SLOT);
        inventoryEditor.sampleInventory(player, 36);
        inventoryEditor.sampleInventory(player, -1);
        require(!NativeArmorItem.hasInventorySample(stack), "refuses non-block and out-of-range inventory samples");
        require(NativeArmorInventorySources.choice(sampleCube) == INDUSTRIAL_BLOCK
            && NativeArmorInventorySources.choice(sampleDoor) == CARGO_DOOR, "configured inventory choices");
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
                require(NativeArmorItem.choice(mounted) == INDUSTRIAL_BLOCK && mounted.getDamageValue() == 11
                    && mounted.hasFoil() && mounted.getHoverName().getString().equals(mountedName(index, slot)),
                    "mounted armor preserves other data index=" + index + " slot=" + slot);
            }
        }
        System.out.println("[vandorlabs][armor] server PASS");
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
