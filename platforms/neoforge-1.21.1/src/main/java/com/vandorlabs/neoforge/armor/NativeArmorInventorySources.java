package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.NativeMaterialRegistry;
import com.vandorlabs.neoforge.door.NativeDoorItem;
import com.vandorlabs.neoforge.door.NativeDoorSettings;
import com.vandorlabs.neoforge.door.NativeLargeDoorItem;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The chosen inventory slot supplies artwork only, leaving the source item and all its other data alone. */
final class NativeArmorInventorySources {
    private static final Map<ModernFinishCatalog.DoorArtwork, Integer> DOORS = doors();

    static int choice(ItemStack source) {
        if (source.isEmpty()) return -1;
        if (source.getItem() instanceof NativeDoorItem || source.getItem() instanceof NativeLargeDoorItem) {
            return doorChoice(source);
        }
        int choice = NativeMaterialRegistry.sampleItem(source);
        return valid(choice) ? choice : -1;
    }

    private static int doorChoice(ItemStack source) {
        var data = source.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        if (data.contains("SpaceDoorSettings") && !data.contains("SpaceDoorSettings", Tag.TAG_COMPOUND)) return -1;
        var tag = data.getCompound("SpaceDoorSettings");
        if (tag.contains("DoorFaceTexture")) {
            if (!tag.contains("DoorFaceTexture", Tag.TAG_INT)) return -1;
            int face = tag.getInt("DoorFaceTexture");
            if (face >= 0) return valid(face) ? face : -1;
        }
        boolean large = source.getItem() instanceof NativeLargeDoorItem;
        var design = tag.isEmpty() ? NativeDoorSettings.fresh(large, false).door() : SpaceDoorData.read(new ModernHousingTags(tag));
        return DOORS.getOrDefault(new ModernFinishCatalog.DoorArtwork(design.design, design.detail), -1);
    }

    static boolean valid(int choice) {
        if (choice >= 0 && choice < ModernFinishCatalog.ENTRIES.size()) return ModernFinishCatalog.option(choice).visible();
        if (!MaterialChoicePolicy.isCustom(choice)) return false;
        var block = NativeMaterialRegistry.snapshot().states().get(choice);
        return block != null && !block.isAir() && !block.liquid();
    }

    private static Map<ModernFinishCatalog.DoorArtwork, Integer> doors() {
        var choices = new HashMap<ModernFinishCatalog.DoorArtwork, Integer>();
        for (int choice = 0; choice < ModernFinishCatalog.ENTRIES.size(); choice++) {
            var entry = ModernFinishCatalog.option(choice);
            if (entry.visible() && entry.door() != null) choices.putIfAbsent(entry.door(), choice);
        }
        return Map.copyOf(choices);
    }

    private NativeArmorInventorySources() {}
}
