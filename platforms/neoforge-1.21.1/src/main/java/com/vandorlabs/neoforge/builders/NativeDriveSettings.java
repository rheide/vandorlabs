package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.tiles.HousingCategoryPolicy;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Player-owned decorative settings. Signal level is derived separately from the loaded world. */
public record NativeDriveSettings(boolean redstone, boolean inverted, boolean levels, int particles, int threshold,
                                  int display, int keyboard, boolean animated, int speed) {
    private static final Set<String> FIELDS = Set.of("Redstone", "Inverted", "Levels", "Particles", "Threshold",
            "Display", "Keyboard", "Animated", "Speed");

    public NativeDriveSettings {
        if (particles < 0 || particles > 3 || threshold < 0 || threshold > 15 || !material(display)
                || !material(keyboard) || speed < 0 || speed > 2) throw new IllegalArgumentException("Drive controls");
    }

    public static NativeDriveSettings defaults(boolean exhaust) {
        return new NativeDriveSettings(false, false, false, exhaust ? 1 : 0, 8, -2, -2, true, 1);
    }

    public static NativeDriveSettings maxwell(boolean redstone, boolean inverted, boolean analog) {
        return new NativeDriveSettings(redstone, inverted, analog, 0, 8, -2, -2, true, 1);
    }

    public static NativeDriveSettings readMaxwell(CompoundTag owner) {
        if (owner.contains("Drive") && !owner.contains("Drive", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid Maxwell Drive controls");
        return owner.contains("Drive") ? read(owner.getCompound("Drive"), true) : defaults(false);
    }

    public static boolean material(int choice) {
        if (choice == -2 || choice == -1) return true;
        if (choice < 0 || !ModernFinishCatalog.validChoice(choice)) return false;
        var entry = ModernFinishCatalog.option(choice);
        String category = entry.category();
        return entry.general() || !HousingCategoryPolicy.door(category) && !HousingCategoryPolicy.doubleDoor(category)
                && !category.equals("Lights") && (!category.equals("Screens") || entry.displaySelectable());
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Redstone", redstone);
        tag.putBoolean("Inverted", inverted);
        tag.putBoolean("Levels", levels);
        tag.putInt("Particles", particles);
        tag.putInt("Threshold", threshold);
        tag.putInt("Display", display);
        tag.putInt("Keyboard", keyboard);
        tag.putBoolean("Animated", animated);
        tag.putInt("Speed", speed);
        return tag;
    }

    public static NativeDriveSettings read(CompoundTag tag, boolean exhaust) {
        if (!tag.getAllKeys().equals(FIELDS)) throw new IllegalArgumentException("Drive settings fields");
        for (String key : FIELDS) {
            boolean flag = key.equals("Redstone") || key.equals("Inverted") || key.equals("Levels") || key.equals("Animated");
            if (!tag.contains(key, flag ? Tag.TAG_BYTE : Tag.TAG_INT) || flag && tag.getByte(key) != 0 && tag.getByte(key) != 1)
                throw new IllegalArgumentException("Drive settings type " + key);
        }
        int particles = tag.getInt("Particles");
        if (!exhaust && particles != 0) throw new IllegalArgumentException("Unsupported drive exhaust");
        return new NativeDriveSettings(tag.getBoolean("Redstone"), tag.getBoolean("Inverted"), tag.getBoolean("Levels"),
                particles, tag.getInt("Threshold"), tag.getInt("Display"), tag.getInt("Keyboard"),
                tag.getBoolean("Animated"), tag.getInt("Speed"));
    }
}
