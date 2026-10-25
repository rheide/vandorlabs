package com.vandorlabs.gear.track;

import com.vandorlabs.gear.track.core.TrackMounting;
import com.vandorlabs.gear.track.core.TrackTopology;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/** Common-side asset interface for a host landing-gear or vehicle renderer. */
public final class TrackParts {
    /** Pivot in track-cell coordinates. An arm adapter must account for the taller foot. */
    public static final double SOCKET_X = 0.5;
    public static final double SOCKET_Y = TrackMounting.ARM_SOCKET_Y;
    public static final double SOCKET_Z = 0.5;

    public static final class Part {
        public final ResourceLocation model;
        /** Clockwise viewed from above, about cell center (0.5, 0, 0.5). */
        public final int quarterTurns;

        private Part(String name, int turns) {
            this.model = new ResourceLocation(com.vandorlabs.VandorLabs.MODID, "block/track/" + name);
            this.quarterTurns = turns & 3;
        }
    }

    private TrackParts() { }

    public static String styleName(TrackTopology.Style style) { return style.name().toLowerCase(Locale.ROOT); }

    public static int quarterTurns(EnumFacing facing) {
        switch (facing) {
            case EAST: return 1;
            case SOUTH: return 2;
            case WEST: return 3;
            default: return 0;
        }
    }

    /** Model parts only. It neither moves world cells nor claims an arm/controller. */
    public static List<Part> forCell(TrackTopology.Style style, EnumFacing facing, TrackAppearance appearance) {
        List<Part> parts = new ArrayList<Part>();
        int turns = quarterTurns(facing);
        parts.add(new Part(styleName(style) + "_" + appearance.beltMask, turns));
        switch (appearance.mount) {
            case SOCKET: parts.add(new Part("socket", turns)); break;
            case TOP: parts.add(new Part("top_mount", turns)); break;
            case TOP_HIGH: parts.add(new Part("top_mount_high", turns)); break;
            default: break;
        }
        for (int side = 0; side < 4; side++) {
            if ((appearance.sideMask & 1 << side) != 0) parts.add(new Part((appearance.lowerSideMask&1<<side)!=0?"side_mount_low":"side_mount", side));
        }
        if ((appearance.bridgeMask & TrackTopology.LEFT) != 0) parts.add(new Part("bridge_left", turns));
        if ((appearance.bridgeMask & TrackTopology.RIGHT) != 0) parts.add(new Part("bridge_right", turns));
        return Collections.unmodifiableList(parts);
    }
}
