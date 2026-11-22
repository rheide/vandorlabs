package com.vandorlabs.screen;

public final class VehicleStatusLayoutTest {
    public static void main(String[] args) {
        for (int height : new int[]{64, 128}) {
            VehicleStatusLayout layout = VehicleStatusLayout.forHeight(height);
            for (int mode = 0; mode < VehicleStatusLayout.MODE_COUNT; mode++) {
                int x = layout.centerX(mode), y = layout.centerY(mode);
                need(layout.target(x, y) == mode, "center resolves its drawn mode");
                need(layout.target(layout.left(mode), layout.top(mode)) == mode, "inclusive top and left");
                need(layout.target(layout.right(mode), y) == -1, "right edge falls in the gap");
                need(layout.target(x, layout.bottom(mode)) == -1, "bottom edge falls in the gap");
                need(layout.bottom(mode) <= layout.footerTop && layout.footerBottom <= height, "visible input remains on the face");
                need(layout.buttonColor(0, mode, mode) == VehicleStatusLayout.DISABLED_COLOR, "disabled mode remains gray");
                need(layout.buttonColor(1 << mode, mode, mode) == VehicleStatusLayout.ACTIVE_COLOR, "active mode gets its own color");
            }
            need(layout.target(20, layout.autopilotTop + 3) == VehicleStatusLayout.AUTOPILOT, "Autopilot header");
            need(layout.target(20, layout.footerTop + 3) == VehicleStatusLayout.NEXT, "Next footer");
            need(layout.target(85, layout.footerTop + 3) == VehicleStatusLayout.EMERGENCY, "Emergency Park footer");
        }
        need("Fuel 100%  1000 mB".equals(VehicleStatusLayout.fuelText(1000, 1000)), "full tank retains amount and unit");
        need("Fuel 0%  0 mB".equals(VehicleStatusLayout.fuelText(0, 1000)), "empty installed tank still reports fuel");
        need(VehicleStatusLayout.fuelText(0, 0).isEmpty(), "craft without fuel storage");
        System.out.println("Vehicle Status grid, action bounds, enabled colors and fuel text PASS");
    }

    private static void need(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }
}
