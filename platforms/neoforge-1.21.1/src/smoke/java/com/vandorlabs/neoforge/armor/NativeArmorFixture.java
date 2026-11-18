package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeArmorFixture {
    static boolean enabled() { return Boolean.getBoolean("vandorlabs.armorClient"); }
    static final String NAME = "Fixture Helmet";
    static final int SELECTED = 268435498;

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var helmet = NativeArmorBindings.IDS.get(ArmorItem.Type.HELMET);
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(helmet);
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
        System.out.println("[vandorlabs][armor] server PASS");
    }

    static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Native armor: " + description);
    }
}
