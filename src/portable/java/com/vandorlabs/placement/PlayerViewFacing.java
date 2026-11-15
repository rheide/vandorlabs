package com.vandorlabs.placement;

/** Programmable Light placement policy, in the shared DOWN/UP/NORTH/SOUTH/WEST/EAST order. */
public final class PlayerViewFacing {
    private PlayerViewFacing() { }
    public static int towardView(float pitch, int horizontalFacing) {
        if (horizontalFacing < 2 || horizontalFacing > 5) throw new IllegalArgumentException("Horizontal view facing required");
        return pitch > 45 ? 1 : pitch < -45 ? 0 : (horizontalFacing ^ 1);
    }
}
