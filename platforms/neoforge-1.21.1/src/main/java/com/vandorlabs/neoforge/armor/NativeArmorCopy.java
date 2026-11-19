package com.vandorlabs.neoforge.armor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Capture and apply appearance without selecting or copying other item components. */
public final class NativeArmorCopy {
    private static final String KEY = "armorAppearance";
    private static final String SAMPLE = "sample", INVENTORY = "inventoryChoice", CHOICE = "choice";

    public static boolean supports(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NativeArmorItem;
    }

    public static CompoundTag capture(ItemStack stack) {
        if (!supports(stack)) return null;
        var armor = (NativeArmorItem)stack.getItem();
        var appearance = new CompoundTag();
        String sprite = NativeArmorItem.worldSample(stack);
        int choice = NativeArmorItem.choice(stack);
        if (sprite != null) {
            if (NativeArmorWorldSampling.sprite(sprite) == null) return null;
            appearance.putString(SAMPLE, sprite);
        } else if (NativeArmorItem.hasInventorySample(stack)) {
            if (!NativeArmorInventorySources.valid(choice)) return null;
            appearance.putInt(INVENTORY, choice);
        } else {
            if (NativeArmorCatalog.design(choice, armor.getEquipmentSlot()) == null
                && !NativeArmorItem.listedMaterial(choice)) return null;
            appearance.putInt(CHOICE, choice);
        }
        var clipboard = new CompoundTag();
        clipboard.put(KEY, appearance);
        return clipboard;
    }

    public static boolean apply(ItemStack stack, CompoundTag clipboard) {
        if (!supports(stack) || clipboard == null || !clipboard.contains(KEY, Tag.TAG_COMPOUND)) return false;
        var appearance = clipboard.getCompound(KEY);
        if (appearance.size() != 1) return false;

        if (appearance.contains(SAMPLE, Tag.TAG_STRING)) {
            String sprite = appearance.getString(SAMPLE);
            if (NativeArmorWorldSampling.sprite(sprite) == null) return false;
            NativeArmorItem.applyWorldSample(stack, sprite);
            return true;
        }
        if (appearance.contains(INVENTORY, Tag.TAG_INT)) return applyInventory(stack, appearance.getInt(INVENTORY));
        if (!appearance.contains(CHOICE, Tag.TAG_INT)) return false;
        int choice = appearance.getInt(CHOICE);
        var armor = (NativeArmorItem)stack.getItem();
        var matching = NativeArmorCatalog.matchingSlot(choice, armor.getEquipmentSlot());
        if (matching != null) return NativeArmorItem.configure(stack, matching.choice());
        if (NativeArmorItem.listedMaterial(choice)) return NativeArmorItem.configure(stack, choice);
        return applyInventory(stack, choice);
    }

    private static boolean applyInventory(ItemStack stack, int choice) {
        if (!NativeArmorInventorySources.valid(choice)) return false;
        NativeArmorItem.applyInventorySample(stack, choice);
        return true;
    }

    public static void synchronizeHeld(ServerPlayer player, ItemStack stack) {
        if (stack == player.getOffhandItem()) NativeArmorItem.synchronizeHeld(player, InteractionHand.OFF_HAND, stack);
    }

    private NativeArmorCopy() {}
}
