package com.vandorlabs.redstone;

/** Stable row limits, text validation and four-detent control policy shared by display families. */
public final class RedstoneScreenPolicy {
    public static final int REDSTONE_SURFACE = -2, VEHICLE_SURFACE = -3;
    public static final int MAX_ROWS = 8, MAX_LABEL = 24, MAX_TITLE = 32;
    public static final int SLIDER_LABEL = 11, SLIDER_LABEL_WIDTH = 50, SLIDER_LEFT = 60, SLIDER_RIGHT = 120;
    public static final String DEFAULT_TITLE = "REDSTONE CONTROL";
    private RedstoneScreenPolicy() { }
    private static boolean validText(String text, int limit) {
        if (text == null || text.length() > limit) return false;
        for (int i = 0; i < text.length(); i++) if (Character.isISOControl(text.charAt(i)) || text.charAt(i) == '\u00a7') return false;
        return true;
    }
    public static boolean validTitle(String title) { return validText(title, MAX_TITLE); }
    public static boolean validLabel(String label) { return validText(label, MAX_LABEL) && !label.trim().isEmpty(); }
    public static boolean validOptions(String label, boolean slider, boolean pulse, int min, int max) {
        return !(pulse && slider) && (!slider || label.length() <= SLIDER_LABEL && min >= 0 && max <= 15 && min <= max);
    }
    public static boolean validLimits(int min, int max) { return min >= 1 && max <= 15 && max - min >= 2; }
    public static int levelForStep(int step, int min, int max) { return step == 0 ? 0 : step == 1 ? min : step == 2 ? (min + max + 1) / 2 : max; }
    public static int stepForLevel(int level, int min, int max) {
        int nearest = 0, distance = 16;
        for (int i = level > 0 ? 1 : 0; i < 4; i++) {
            int candidate = Math.abs(level - levelForStep(i, min, max));
            if (candidate < distance) { nearest = i; distance = candidate; }
        }
        return nearest;
    }
}
