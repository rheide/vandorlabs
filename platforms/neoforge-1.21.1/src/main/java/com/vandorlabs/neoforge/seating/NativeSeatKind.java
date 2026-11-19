package com.vandorlabs.neoforge.seating;
public enum NativeSeatKind {
    CHAIR("programmable_chair"),LUXURY("luxury_seat"),MILITARY("military_seat"),PILOT("pilot_seat"),ADVANCED("chair_siderests"),COCKPIT("cockpit");
    public boolean adjustablePilotHeight(){return this==PILOT||this==ADVANCED;}
    public final String id;NativeSeatKind(String id){this.id=id;}public boolean connected(){return this==LUXURY||this==MILITARY;}
}
