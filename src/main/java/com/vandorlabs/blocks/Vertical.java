package com.vandorlabs.blocks;

import net.minecraft.util.IStringSerializable;

public enum Vertical implements IStringSerializable {
    DOWN("down"),
    LEVEL("level"),
    UP("up");

    private final String name;

    Vertical(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
