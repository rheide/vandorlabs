package com.vandorlabs.gear;

/** Parts register through the mod's normal block/item registry. */
public final class GearRegistry {
    public static final GearBlock LANDING_PLATE=new GearBlock("landing_plate",GearBlock.Kind.PLATE);
    public static final GearBlock LANDING_SKI=new GearBlock("landing_ski",GearBlock.Kind.SKI);
    public static final GearBlock GEAR_EXTENDER=new GearBlock("gear_extender",GearBlock.Kind.EXTENDER);
    public static final GearBlock GEAR_SWING_ARM=new GearBlock("gear_swing_arm",GearBlock.Kind.SWING_ARM);
    public static final GearBlock LARGE_GEAR_EXTENDER=new LargeGearBlock("large_gear_extender",GearBlock.Kind.EXTENDER);
    public static final GearBlock LARGE_GEAR_SWING_ARM=new LargeGearBlock("large_gear_swing_arm",GearBlock.Kind.SWING_ARM);
    public static final GearBlock LANDING_WHEEL=new GearBlock("landing_wheel",GearBlock.Kind.WHEEL);
    public static final GearBlock LARGE_LANDING_WHEEL=new LargeGearBlock("large_landing_wheel",GearBlock.Kind.WHEEL);
    public static final GearBlock GEAR_MOUNT=new GearBlock("gear_mount",GearBlock.Kind.MOUNT);
    public static final GearBlock LARGE_GEAR_MOUNT=new LargeGearBlock("large_gear_mount",GearBlock.Kind.MOUNT);
    private GearRegistry(){}
}
