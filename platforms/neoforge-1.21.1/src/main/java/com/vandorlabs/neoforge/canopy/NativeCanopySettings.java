package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.CanopySettingsData;
import net.minecraft.nbt.CompoundTag;

/** World-free canopy settings. Failed or unknown owner NBT stays outside this render-safe value. */
public record NativeCanopySettings(CanopySettingsData data) {
    public NativeCanopySettings { java.util.Objects.requireNonNull(data); }
    public static NativeCanopySettings fresh() { return new NativeCanopySettings(CanopySettingsData.fresh()); }
    public static NativeCanopySettings read(CompoundTag tag) {
        return new NativeCanopySettings(CanopySettingsData.read(new ModernHousingTags(tag)));
    }
    public void write(CompoundTag tag) { data.write(new ModernHousingTags(tag)); }
}
