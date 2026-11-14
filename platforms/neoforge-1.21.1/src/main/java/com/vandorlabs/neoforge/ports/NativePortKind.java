package com.vandorlabs.neoforge.ports;

public enum NativePortKind {
    POWER("power_port","power","Power Port","RF/t"),FLUID("fluid_port","fluid","Fluid Port","mB/t"),MATTER("matter_port","matter","Matter Port","items/t");
    public final String id,texture,title,units;
    NativePortKind(String id,String texture,String title,String units){this.id=id;this.texture=texture;this.title=title;this.units=units;}
}
