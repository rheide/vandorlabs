package com.vandorlabs.persistence;

/** Small tag API implemented by each Minecraft version's NBT adapter. */
public interface PrimitiveData {
    boolean contains(String key);
    String getString(String key);
    int getInt(String key);
    long getLong(String key);
    double getDouble(String key);
    boolean getBoolean(String key);
    void putString(String key, String value);
    void putInt(String key, int value);
    void putLong(String key, long value);
    void putDouble(String key, double value);
    void putBoolean(String key, boolean value);
}
