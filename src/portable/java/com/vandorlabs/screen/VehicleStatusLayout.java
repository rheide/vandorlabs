package com.vandorlabs.screen;

/** Pixel geometry shared by the drawn Vehicle Status panel and its ray targets. */
public final class VehicleStatusLayout {
    public static final int WIDTH = 128;
    public static final int PARK = 0, TAXI = 1, HOVER = 2, FLIGHT = 3, MODE_COUNT = 4;
    public static final int AUTOPILOT = 4, NEXT = 5, EMERGENCY = 6;
    public static final int ENABLED_COLOR = 0xff495c69, DISABLED_COLOR = 0xff333b41, ACTIVE_COLOR = 0xff37d8ad;
    private static final String[] LABELS = {"Park", "Taxi", "Hover", "Flight"};

    private static final VehicleStatusLayout FULL = new VehicleStatusLayout(19, 32, 36, 18, 3, 111, 124);
    private static final VehicleStatusLayout HALF = new VehicleStatusLayout(1, 12, 15, 12, 2, 52, 63);
    private final int modeTop, modeHeight, gap;
    public final int autopilotTop, autopilotBottom, footerTop, footerBottom;

    private VehicleStatusLayout(int autopilotTop, int autopilotBottom, int modeTop, int modeHeight,
                                int gap, int footerTop, int footerBottom) {
        this.autopilotTop = autopilotTop;
        this.autopilotBottom = autopilotBottom;
        this.modeTop = modeTop;
        this.modeHeight = modeHeight;
        this.gap = gap;
        this.footerTop = footerTop;
        this.footerBottom = footerBottom;
    }

    public static VehicleStatusLayout forHeight(int height) { return height <= 64 ? HALF : FULL; }
    public static String label(int mode) { return LABELS[mode]; }
    public static String fuelText(int fuel, int capacity) {
        return capacity <= 0 ? "" : "Fuel " + Math.round(100D * fuel / capacity) + "%  " + fuel + " mB";
    }
    public int left(int mode) { return (mode & 1) == 0 ? 6 : 66; }
    public int right(int mode) { return left(mode) + 56; }
    public int top(int mode) { return modeTop + (mode / 2) * (modeHeight + gap); }
    public int bottom(int mode) { return top(mode) + modeHeight; }
    public int centerX(int mode) { return (left(mode) + right(mode)) / 2; }
    public int centerY(int mode) { return (top(mode) + bottom(mode)) / 2; }
    public boolean enabled(int mask, int mode) { return (mask & (1 << mode)) != 0; }
    public int buttonColor(int mask, int mode, int active) {
        return !enabled(mask, mode) ? DISABLED_COLOR : mode == active ? ACTIVE_COLOR : ENABLED_COLOR;
    }

    public int target(double x, double y) {
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            if (x >= left(mode) && x < right(mode) && y >= top(mode) && y < bottom(mode)) return mode;
        }
        if (y >= autopilotTop && y < autopilotBottom && x >= 6 && x < 122) return AUTOPILOT;
        if (y >= footerTop && y < footerBottom) {
            if (x >= 6 && x < 48) return NEXT;
            if (x >= 52 && x < 122) return EMERGENCY;
        }
        return -1;
    }
}
