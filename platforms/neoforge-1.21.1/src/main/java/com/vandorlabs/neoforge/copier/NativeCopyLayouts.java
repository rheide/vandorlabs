package com.vandorlabs.neoforge.copier;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Copy Controller receipt identities, including legacy two-long UUIDs. */
final class NativeCopyLayouts {
    static UUID uuid(CompoundTag tag,String key,boolean required){if(tag.hasUUID(key))return tag.getUUID(key);if(tag.contains(key)||tag.contains(key+"Most")||tag.contains(key+"Least")){if(!tag.contains(key+"Most",Tag.TAG_LONG)||!tag.contains(key+"Least",Tag.TAG_LONG))throw new IllegalArgumentException("Saved controller UUID");return new UUID(tag.getLong(key+"Most"),tag.getLong(key+"Least"));}if(required)throw new IllegalArgumentException("Missing controller UUID");return null;}
    private NativeCopyLayouts(){}
}
