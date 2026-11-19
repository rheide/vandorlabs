package com.vandorlabs.neoforge.builders;

import java.util.List;

/** Standalone drive structures use the existing Constructor ownership journal. */
public enum NativeDriveDesign {
    LONGBURN("longburn_drive", "longburn_drive_core", 3, 3, 4, true, .5625, 1.375, .686875, .5, .625),
    MERIDIAN("meridian_core", "meridian_drive_core", 3, 3, 3, false, .375, .625, .186875, .5, .625),
    OCULUS("oculus_drive", "oculus_drive_core", 3, 3, 3, false, .8125, .65625, .364375, .375, .375);

    public final String id, block;
    public final int width, height, depth;
    public final boolean exhaust;
    public final double chipX, chipY, chipZ, chipWidth, chipHeight;

    NativeDriveDesign(String id, String block, int width, int height, int depth, boolean exhaust, double chipX, double chipY, double chipZ, double chipWidth, double chipHeight) {
        this.id = id;
        this.block = block;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.exhaust = exhaust;
        this.chipX = chipX;this.chipY = chipY;this.chipZ = chipZ;
        this.chipWidth = chipWidth;this.chipHeight = chipHeight;
    }

    public static NativeDriveDesign of(String id) {
        return switch (id) {
            case "longburn_drive" -> LONGBURN;
            case "meridian_core" -> MERIDIAN;
            case "oculus_drive" -> OCULUS;
            default -> null;
        };
    }

    public static final List<NativeDriveDesign> SUPPORTED = List.of(LONGBURN, MERIDIAN, OCULUS);
}
