package com.vandorlabs.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Per-item choices for which copied settings may be applied. */
public final class DuplifierApplyOptions {
    public static final String TAG = "DuplifierApplyMask";
    public static final String[] PAGES = {"Common", "Displays", "Blocks", "Doors", "Ramps"};

    public static final class Option {
        public final String key;
        public final String label;
        public final int page;

        private Option(String key, String label, int page) {
            this.key = key;
            this.label = label;
            this.page = page;
        }
    }

    private static Option option(String key, String label, int page) {
        return new Option(key, label, page);
    }

    public static final Option[] OPTIONS = {
            option(ProgrammableSettings.PRIMARY_TEXTURE, "Primary Texture", 0),
            option(ProgrammableSettings.WALL_TEXTURE, "Wall Texture", 0),
            option(ProgrammableSettings.JOIN, "Join", 0),
            option(ProgrammableSettings.CHANNEL, "Redstone Channel", 0),
            option(ProgrammableSettings.TRIGGER, "Redstone Trigger", 0),
            option(ProgrammableSettings.ACTIVE, "Active State", 0),
            option(ProgrammableSettings.PARTICLES, "Particle Stream", 0),
            option(ProgrammableSettings.FRAMED, "Frame", 0),
            option(ProgrammableSettings.GLASS_SHADE, "Glass Shade", 0),
            option(ProgrammableSettings.LIGHT_LEVEL, "Light Level", 0),

            option(ProgrammableSettings.DISPLAY_MODE, "Display Mode", 1),
            option(ProgrammableSettings.ANIMATION_SPEED, "Animation Speed", 1),
            option(ProgrammableSettings.INPUT_PANEL, "Input Panel", 1),
            option(ProgrammableSettings.SECONDARY_INPUT_PANEL, "Second Input Panel", 1),
            option(ProgrammableSettings.SMALL_INPUT, "Small Input", 1),
            option(ProgrammableSettings.WALL_POSITION, "Wall Position", 1),
            option(ProgrammableSettings.TRIGGER_ON_TEXTURE, "Trigger On Texture", 1),

            option(ProgrammableSettings.PROPULSION_SHAPE, "Thruster Shape", 2),
            option(ProgrammableSettings.PORTHOLE_SHAPE, "Porthole Shape", 2),
            option(ProgrammableSettings.GLASS_SIZE, "Glass Size", 2),
            option(ProgrammableSettings.SLAB_TILE_SIDES, "Slab Side Layout", 2),
            option(ProgrammableSettings.CHAIR_STYLE, "Chair Style", 2),
            option(ProgrammableSettings.CHAIR_HEIGHT, "Chair Height", 2),
            option(ProgrammableSettings.SWITCH_ROTATION, "Switch Rotation", 2),

            option(ProgrammableSettings.DOOR_DESIGN, "Door Design", 3),
            option(ProgrammableSettings.DOOR_DETAIL, "Door Detail", 3),
            option(ProgrammableSettings.DOOR_SLIDE_DIRECTION, "Slide Direction", 3),
            option(ProgrammableSettings.DOOR_MIDDLE, "Middle Door", 3),
            option(ProgrammableSettings.DOOR_SLIDING, "Sliding Door", 3),
            option(ProgrammableSettings.DOOR_HINGES, "Door Hinges", 3),
            option(ProgrammableSettings.DOOR_PANEL, "Door Panel", 3),
            option(ProgrammableSettings.DOOR_DEPTH, "Door Depth", 3),

            option(ProgrammableSettings.RAMP_START, "Start Offset", 4),
            option(ProgrammableSettings.RAMP_END, "End Offset", 4),
            option(ProgrammableSettings.RAMP_TREAD_PIXELS, "Tread Size", 4),
            option(ProgrammableSettings.RAMP_SPEED, "Ramp Speed", 4),
            option(ProgrammableSettings.RAMP_LIFT, "Lift Mode", 4),
            option(ProgrammableSettings.RAMP_EXTEND, "Extend Mode", 4),
            option(ProgrammableSettings.RAMP_DIRECTION, "Ramp Direction", 4),
            option(ProgrammableSettings.RAMP_TRAVEL, "Travel Direction", 4)
    };

    public static final long ALL = (1L << OPTIONS.length) - 1L;

    private DuplifierApplyOptions() { }

    public static long mask(ItemStack tool) {
        NBTTagCompound root = tool.getTagCompound();
        return root == null || !root.hasKey(TAG, 4) ? ALL : root.getLong(TAG) & ALL;
    }

    public static void setMask(ItemStack tool, long value) {
        NBTTagCompound root = tool.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        root.setLong(TAG, value & ALL);
        tool.setTagCompound(root);
    }

    public static boolean enabled(long mask, int index) {
        return (mask & (1L << index)) != 0;
    }

    public static NBTTagCompound selected(NBTTagCompound captured, long mask) {
        if (captured == null) return null;
        NBTTagCompound selected = captured.copy();
        for (int i = 0; i < OPTIONS.length; i++)
            if (!enabled(mask, i)) selected.removeTag(OPTIONS[i].key);
        if (!selected.hasKey(ProgrammableSettings.PRIMARY_TEXTURE))
            selected.removeTag(ProgrammableSettings.PRIMARY_KIND);
        return selected;
    }
}
