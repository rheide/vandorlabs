package com.vandorlabs.persistence;

import java.util.function.IntUnaryOperator;

/** The reference distinguishes saved negative values from a live vehicle-surface selection. */
public final class HousingSideTexture {
    public static final int INHERIT=-1,VEHICLE_SURFACE=-3;
    private HousingSideTexture(){}
    public static int read(PrimitiveData tag,IntUnaryOperator choice){int value=tag.getInt("SideTexture");return tag.containsInt("SideTexture")&&value>=0?choice.applyAsInt(value):INHERIT;}
    public static int selected(int value,IntUnaryOperator choice){return value==VEHICLE_SURFACE?VEHICLE_SURFACE:value<0?INHERIT:choice.applyAsInt(value);}
}
