package com.vandorlabs.neoforge.redstone;

/** Identity-local proof that a cropped pair came from the exact original 3x3 center rule. */
public interface TransmitterCraftingContext {
    int vandorlabs$copySource();
    void vandorlabs$copySource(int encodedIndex);
}
