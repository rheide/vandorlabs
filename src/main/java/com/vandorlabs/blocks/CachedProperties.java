package com.vandorlabs.blocks;

import com.google.common.base.Predicate;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import java.util.Arrays;
import java.util.Collection;

/** Vanilla-compatible immutable enum properties with a precomputed map hash. */
public final class CachedProperties {
    private CachedProperties() { }
    public static PropertyDirection direction(String name) {
        return new Direction(name,Arrays.asList(EnumFacing.values()));
    }
    public static PropertyDirection direction(String name,Predicate<EnumFacing> filter) {
        return new Direction(name,com.google.common.collect.Collections2.filter(Arrays.asList(EnumFacing.values()),filter));
    }
    public static <T extends Enum<T> & IStringSerializable> PropertyEnum<T> enumeration(String name,Class<T> type) {
        return enumeration(name,type,Arrays.asList(type.getEnumConstants()));
    }
    public static <T extends Enum<T> & IStringSerializable> PropertyEnum<T> enumeration(String name,Class<T> type,Collection<T> values) {
        return new Enumeration<>(name,type,values);
    }
    private static final class Direction extends PropertyDirection {
        private final int hash;
        Direction(String name,Collection<EnumFacing> values){super(name,values);hash=super.hashCode();}
        @Override public int hashCode(){return hash;}
    }
    private static final class Enumeration<T extends Enum<T> & IStringSerializable> extends PropertyEnum<T> {
        private final int hash;
        Enumeration(String name,Class<T> type,Collection<T> values){super(name,type,values);hash=super.hashCode();}
        @Override public int hashCode(){return hash;}
    }
}
