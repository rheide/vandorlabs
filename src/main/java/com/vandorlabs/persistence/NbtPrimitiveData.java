package com.vandorlabs.persistence;

import net.minecraft.nbt.NBTTagCompound;

/** Forge 1.12.2 adapter for the portable primitive save codec. */
public final class NbtPrimitiveData implements PrimitiveData {
    private final NBTTagCompound tag;

    public NbtPrimitiveData(NBTTagCompound tag) {
        if (tag == null) throw new NullPointerException("tag");
        this.tag = tag;
    }

    @Override public boolean contains(String key) { return tag.hasKey(key); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public int getInt(String key) { return tag.getInteger(key); }
    @Override public long getLong(String key) { return tag.getLong(key); }
    @Override public double getDouble(String key) { return tag.getDouble(key); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key); }
    @Override public void putString(String key, String value) { tag.setString(key, value); }
    @Override public void putInt(String key, int value) { tag.setInteger(key, value); }
    @Override public void putLong(String key, long value) { tag.setLong(key, value); }
    @Override public void putDouble(String key, double value) { tag.setDouble(key, value); }
    @Override public void putBoolean(String key, boolean value) { tag.setBoolean(key, value); }
}
