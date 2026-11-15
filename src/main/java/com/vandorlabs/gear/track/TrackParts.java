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

    private static final Part[] TEMPLATES=new Part[com.vandorlabs.gear.track.core.TrackMeshPolicy.templateCount()];
    static{for(int i=0;i<TEMPLATES.length;i++){com.vandorlabs.gear.track.core.TrackMeshPolicy.Part p=com.vandorlabs.gear.track.core.TrackMeshPolicy.template(i);TEMPLATES[i]=new Part(p.name,p.quarterTurns);}}
    private static final class Parts extends ArrayList<Part> implements com.vandorlabs.gear.track.core.TrackMeshPolicy.Sink {private static final long serialVersionUID=1L;public void part(com.vandorlabs.gear.track.core.TrackMeshPolicy.Part p){add(TEMPLATES[p.template]);}}
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
        Parts parts=new Parts();com.vandorlabs.gear.track.core.TrackMeshPolicy.emit(style,quarterTurns(facing),appearance.key(),parts);return Collections.unmodifiableList(parts);
    }
}
