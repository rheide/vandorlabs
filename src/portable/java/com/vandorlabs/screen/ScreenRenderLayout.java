package com.vandorlabs.screen;

/** Convert the discovery lattice to the local axes of the rendered display. */
public final class ScreenRenderLayout {
    private ScreenRenderLayout() { }

    public static boolean mirrorColumns(ScreenFamily family, int facing) {
        return (family == ScreenFamily.SCREEN || family == ScreenFamily.CONSOLE) && facing == 1;
    }

    public static boolean mirrorRows(ScreenFamily family, int facing, boolean ceiling) {
        return (family == ScreenFamily.SCREEN || family == ScreenFamily.CONSOLE) && facing == 0
                || family.keyboard && ceiling;
    }
}
