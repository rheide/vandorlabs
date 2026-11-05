package com.vandorlabs.persistence;

/** Typed tag operations needed by the housing save contract. */
public interface HousingTags extends PrimitiveData {
    boolean containsInt(String key);
    boolean containsBoolean(String key);
    int[] getIntArray(String key);
    void putIntArray(String key, int[] value);
}
