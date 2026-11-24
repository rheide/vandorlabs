package com.vandorlabs.rack;

/** Four bounded physical module slots, shared by both engine adapters. */
public final class ModuleRackPolicy {
    public static final int EMPTY = 0, BLANK = 1, AUTOPILOT = 2, BATTERY = 3, SPEED = 4;
    public static final String BLANK_ID = "vandorlabs:blank_module";
    public static final String AUTOPILOT_ID = "vandorlabs:autopilot_module";
    public static final String SPEED_ID = "vandorlabs:speed_autopilot_module";
    public static final String BATTERY_ID = "vandorlabs:battery_module";
    private ModuleRackPolicy() { }
    public static int kind(String itemId) {
        return BLANK_ID.equals(itemId) ? BLANK : AUTOPILOT_ID.equals(itemId) ? AUTOPILOT : BATTERY_ID.equals(itemId) ? BATTERY : EMPTY;
    }
    public static int slotKind(int packed, int slot) {
        return ModuleRackGeometry.validSlot(slot) ? packed >>> (slot*2)&3 : EMPTY;
    }
    /** Forge flight racks append one speed-module flag per bay above the legacy kind byte. */
    public static int flightSlotKind(int packed,int slot){
        if (!ModuleRackGeometry.validSlot(slot)) return EMPTY;
        return (packed & (1 << (8+slot))) != 0 ? SPEED : packed >>> (slot*2) & 3;
    }
    public static boolean validKinds(int packed) {
        if ((packed & ~255) != 0) return false;
        return true;
    }
    public static int blankKinds(int occupiedMask) {
        int result=0; for(int slot=0;slot<ModuleRackGeometry.SLOTS;slot++) if((occupiedMask & (1<<slot))!=0) result |= BLANK << (slot*2); return result;
    }
    public static int occupied(int packed) {
        int result = 0;
        for (int slot = 0; slot < ModuleRackGeometry.SLOTS; slot++) if (slotKind(packed, slot) != EMPTY) result |= 1 << slot;
        return result;
    }
    public static int autopilots(int packed) {
        int result = 0;
        for (int slot = 0; slot < ModuleRackGeometry.SLOTS; slot++) if (slotKind(packed, slot) == AUTOPILOT) result++;
        return result;
    }
    /** Nearest representable level: 0, 4, 8, 11, 15 for zero through four occupied bays. */
    public static int signalLevel(int occupiedMask) {
        return (Integer.bitCount(occupiedMask & ModuleRackGeometry.ALL_SLOTS) * 15 + 2) / ModuleRackGeometry.SLOTS;
    }
}
