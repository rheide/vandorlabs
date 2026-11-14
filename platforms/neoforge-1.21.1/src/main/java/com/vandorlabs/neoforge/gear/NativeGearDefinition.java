package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.GearTopology;

/** Source registry identities and physical size classes. Large arms still occupy one cell. */
public enum NativeGearDefinition {
    EXTENDER("gear_extender",Kind.EXTENDER,1),
    ROTATING("gear_swing_arm",Kind.ROTATING,1),
    LARGE_EXTENDER("large_gear_extender",Kind.EXTENDER,2),
    LARGE_ROTATING("large_gear_swing_arm",Kind.ROTATING,2),
    MOUNT("gear_mount",Kind.MOUNT,1),
    LARGE_MOUNT("large_gear_mount",Kind.MOUNT,2),
    PLATE("landing_plate",Kind.PLATE,1),
    SKI("landing_ski",Kind.SKI,1),
    WHEEL("landing_wheel",Kind.WHEEL,1),
    LARGE_WHEEL("large_landing_wheel",Kind.WHEEL,2);

    public enum Kind { EXTENDER,ROTATING,MOUNT,PLATE,SKI,WHEEL }
    public final String id;
    public final Kind kind;
    public final int size;
    NativeGearDefinition(String id,Kind kind,int size){this.id=id;this.kind=kind;this.size=size;}
    public boolean arm(){return kind==Kind.EXTENDER||kind==Kind.ROTATING;}
    public boolean foot(){return kind==Kind.PLATE||kind==Kind.SKI||kind==Kind.WHEEL;}
    public GearTopology.FootKind footKind(){return switch(kind){case PLATE->GearTopology.FootKind.PLATE;case SKI->GearTopology.FootKind.SKI;case WHEEL->GearTopology.FootKind.WHEEL;default->GearTopology.FootKind.NONE;};}
    public static NativeGearDefinition of(String id){for(var value:values())if(value.id.equals(id))return value;throw new IllegalArgumentException("Unknown arm component "+id);}
}
