package com.vandorlabs.ui;

import java.util.Arrays;
import java.util.List;

public final class WirelessDialogLayoutTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int[] size : new int[][]{{1280, 720}, {640, 360}, {426, 240}, {320, 240}, {256, 240}}) for (boolean transmitter : new boolean[]{false, true}) {
            WirelessDialogLayout layout = new WirelessDialogLayout(size[0], size[1], transmitter);
            require(layout.width == Math.min(280, size[0] - 12) && layout.height == (transmitter ? 160 : 224), "legacy endpoint panel dimensions");
            ProgrammableDialogLayout.Button field = layout.channels();
            require(field.x == layout.left + 12 && field.y == layout.top + 48 && field.width == layout.width - 24 && field.height == 18, "channel editor geometry");
            List<WirelessDialogLayout.Control> controls = layout.controls(false, 8, 3, 1);
            int[] ids = new int[controls.size()]; for (int i = 0; i < ids.length; i++) ids[i] = controls.get(i).bounds.id;
            require(Arrays.equals(ids, transmitter ? new int[]{1, 2, 5} : new int[]{1, 2, 3, 4, 5}), "stable control IDs/order");
            require(controls.get(0).bounds.label.equals("Signal: Digital") && controls.get(1).bounds.label.equals((transmitter ? "Input" : "Output") + " port: up"), "shared option labels");
            require(controls.get(0).bounds.y == layout.top + 76 && controls.get(1).bounds.y == layout.top + 102, "shared button rows");
            for (WirelessDialogLayout.Control control : controls) require(control.bounds.x >= 0 && control.bounds.y >= 0 && control.bounds.x + control.bounds.width <= size[0] && control.bounds.y + control.bounds.height <= size[1], "scaled/narrow controls inside viewport");
            if (!transmitter) {
                require(controls.get(2).slider && controls.get(2).enabled && controls.get(2).bounds.label.equals("On level: 8") && controls.get(3).bounds.label.equals("Off level: 3"), "digital level sliders");
                require(!layout.controls(true, 8, 3, 1).get(2).enabled && layout.controls(true, 8, 3, 1).get(3).enabled, "analog disables only the on slider");
            }
            require(controls.get(controls.size() - 1).bounds.y == layout.top + layout.height - 30, "legacy done row");
        }
        String[] names = {"down", "up", "north", "south", "west", "east"};
        for (int face = 0; face < 6; face++) {
            require(WirelessDialogLayout.portLabel(false, face).equals("Output port: " + names[face]), "saved six-face label order");
            require(WirelessDialogLayout.nextFace(WirelessDialogLayout.nextFace(face, -1), 1) == face, "left/right face symmetry");
        }
        require(WirelessDialogLayout.validChannelCharacters("0, 123, 2147483647") && !WirelessDialogLayout.validChannelCharacters("1x")
                && !WirelessDialogLayout.validChannelCharacters("1\n"), "shared channel editor filter");
        System.out.println("Wireless dialog contract PASS: exact labels, stable IDs, analog controls and five logical viewport sizes");
    }
}
