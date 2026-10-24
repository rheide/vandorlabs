package com.vandorlabs.neoforge.persistence;

import com.vandorlabs.persistence.PrimitiveData;
import net.minecraft.nbt.CompoundTag;

/** Native modern tag adapter for the same primitive saved schemas compiled by Forge. */
public final class NbtPrimitiveData implements PrimitiveData {
    private final CompoundTag tag;
    public NbtPrimitiveData(CompoundTag tag) { this.tag = java.util.Objects.requireNonNull(tag); }
    @Override public boolean contains(String key) { return tag.contains(key); }
    @Override public boolean containsInt(String key) { return tag.contains(key, 3); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public int getInt(String key) { return tag.getInt(key); }
    @Override public long getLong(String key) { return tag.getLong(key); }
    @Override public double getDouble(String key) { return tag.getDouble(key); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key); }
    @Override public void putString(String key, String value) { tag.putString(key, value); }
    @Override public void putInt(String key, int value) { tag.putInt(key, value); }
    @Override public void putLong(String key, long value) { tag.putLong(key, value); }
    @Override public void putDouble(String key, double value) { tag.putDouble(key, value); }
    @Override public void putBoolean(String key, boolean value) { tag.putBoolean(key, value); }
}
