package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.gear.core.LegacyLandingSettings;
import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.neoforge.gear.NativeGearDefinition;
import com.vandorlabs.neoforge.gear.NativeGearItem;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Converts the stack within one player inventory, where source and products share a save owner. */
@EventBusSubscriber(modid = "vandorlabs")
final class NativeLegacyWheelInventory {
    private static final Set<String> CHOICES = Set.of("id", "ExtensionPixels", "GearSize", "RedstoneMode",
            "RedstoneChannel", "RedstoneChannels");

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!NativeLegacyAutomaticConversion.ENABLED || !(event.getEntity() instanceof ServerPlayer actor)
                || !actor.isAlive() || actor.isSpectator() || actor.tickCount % 20 != 0
                || actor.serverLevel().noSave || actor.containerMenu != actor.inventoryMenu) return;
        var inventory = actor.getInventory();
        var legacy = NativeLegacyLandingBindings.ITEMS.get(NativeLegacyLandingKind.WHEEL);
        if (legacy == null) return;
        boolean changed = false;
        for (int i = 0; i < inventory.items.size(); i++) {
            var current = inventory.getItem(i);
            if (current.is(legacy)) changed |= convert(inventory, i, current);
        }
        int offhand = inventory.getContainerSize() - 1;
        var held = inventory.getItem(offhand);
        if (held.is(legacy)) changed |= convert(inventory, offhand, held);
        if (changed) {
            inventory.setChanged();
            actor.inventoryMenu.broadcastChanges();
        }
    }

    private static boolean convert(Inventory inventory, int sourceSlot, ItemStack source) {
        final NativeLegacyLandingData.Configuration choices;
        try {
            choices = choices(source);
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
        if (choices == null) return false;
        boolean large = LegacyLandingSettings.usesLargeModularParts(choices.size());
        var extender = NativeGearBindings.ITEMS.get((large ? NativeGearDefinition.LARGE_EXTENDER : NativeGearDefinition.EXTENDER).id);
        var wheel = NativeGearBindings.ITEMS.get((large ? NativeGearDefinition.LARGE_WHEEL : NativeGearDefinition.WHEEL).id);
        if (extender == null || wheel == null) return false;

        var arm = new ItemStack(extender);
        var settings = new CompoundTag();
        settings.putInt("Trigger", choices.mode());
        settings.putInt("Direction", 0);
        settings.putString("Channels", choices.channels().toString());
        settings.putBoolean("Deployed", true);
        settings.putBoolean("LegacyAnyChannels", true);
        var data = new CompoundTag();
        data.put(NativeGearItem.SETTINGS, settings);
        arm.set(DataComponents.CUSTOM_DATA, CustomData.of(data));

        var draft = inventory.items.toArray(ItemStack[]::new);
        if (sourceSlot < draft.length) draft[sourceSlot] = ItemStack.EMPTY;
        if (!insert(draft, arm, source.getCount() * LegacyLandingSettings.modularExtenders(choices.pixels()))
                || !insert(draft, new ItemStack(wheel), source.getCount())) return false;
        for (int i = 0; i < draft.length; i++) {
            if (draft[i] != inventory.getItem(i)) inventory.setItem(i, draft[i]);
        }
        if (sourceSlot >= draft.length) inventory.setItem(sourceSlot, ItemStack.EMPTY);
        return true;
    }

    private static boolean insert(ItemStack[] draft, ItemStack prototype, int remaining) {
        int max = prototype.getMaxStackSize();
        for (int i = 0; i < draft.length && remaining > 0; i++) {
            var stack = draft[i];
            if (!ItemStack.isSameItemSameComponents(stack, prototype) || stack.getCount() >= max) continue;
            int added = Math.min(remaining, max - stack.getCount());
            draft[i] = stack.copyWithCount(stack.getCount() + added);
            remaining -= added;
        }
        for (int i = 0; i < draft.length && remaining > 0; i++) {
            if (!draft[i].isEmpty()) continue;
            int added = Math.min(max, remaining);
            draft[i] = prototype.copyWithCount(added);
            remaining -= added;
        }
        return remaining == 0;
    }

    private static NativeLegacyLandingData.Configuration choices(ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        var block = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (custom != null && block != null || stack.getCount() > stack.getMaxStackSize()) return null;
        for (var component : stack.getComponentsPatch().entrySet()) {
            if (component.getValue().isEmpty() || component.getKey() != DataComponents.CUSTOM_DATA
                    && component.getKey() != DataComponents.BLOCK_ENTITY_DATA) return null;
        }
        CompoundTag raw;
        if (custom != null) {
            if (custom.getUnsafe().sizeInBytes() > 4096) return null;
            var tag = custom.getUnsafe();
            String root = tag.contains("LegacyLandingSettings") ? "LegacyLandingSettings" : "BlockEntityTag";
            if (!tag.getAllKeys().equals(Set.of(root)) || !tag.contains(root, Tag.TAG_COMPOUND)) return null;
            raw = tag.getCompound(root);
        } else if (block != null) {
            raw = block.getUnsafe();
        } else {
            return NativeLegacyLandingData.defaults();
        }
        if (raw.sizeInBytes() > 4096 || !CHOICES.containsAll(raw.getAllKeys())
                || raw.contains("id") && (!raw.contains("id", Tag.TAG_STRING)
                || !raw.getString("id").equals("vandorlabs:landing_gear"))) return null;
        var next = NativeLegacyLandingData.read(raw).configuration();
        if (raw.contains("RedstoneMode") && raw.getInt("RedstoneMode") != next.mode()
                || raw.contains("ExtensionPixels") && raw.getInt("ExtensionPixels") != next.pixels()
                || raw.contains("GearSize") && raw.getInt("GearSize") != next.size()) return null;
        return next;
    }

    private NativeLegacyWheelInventory() {}
}
