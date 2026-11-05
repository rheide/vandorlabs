package com.vandorlabs.tiles;

/** Saved artwork namespaces remain valid even when external artwork is unavailable. */
public final class MaterialChoicePolicy {
    public static final int CUSTOM_BASE = 0x20000000;
    public static final int FILESYSTEM_BASE = 0x40000000;

    private MaterialChoicePolicy() { }

    public static boolean isCustom(int choice) {
        return choice >= CUSTOM_BASE && choice < FILESYSTEM_BASE;
    }

    public static boolean valid(int choice, int builtinCount) {
        return choice >= 0 && (choice < builtinCount || choice >= CUSTOM_BASE);
    }
}
