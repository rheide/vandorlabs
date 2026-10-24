package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Logical endpoint dialog geometry, option order and labels shared by both widget adapters. */
public final class WirelessDialogLayout {
    public static final int SIGNAL = 1, PORT = 2, ON = 3, OFF = 4, DONE = 5;
    public static final int MAX_WIDTH = 280, TRANSMITTER_HEIGHT = 160, RECEIVER_HEIGHT = 224;
    public static final int PANEL_COLOR = 0xff19232c, HEADER_COLOR = 0xff304858;
    public static final int HEADER_HEIGHT = 28;
    public static final int LABEL_X = 12, TITLE_Y = 10, CHANNEL_LABEL_Y = 34, HELP_Y = 180;
    public static final String CHANNEL_LABEL = "Channels (0 = none)", ANALOG_HELP = "On level follows received signal";
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    public final int left, top, width, height;
    public final boolean transmitter;
    public WirelessDialogLayout(int screenWidth, int screenHeight, boolean transmitter) {
        this.transmitter = transmitter; width = Math.min(MAX_WIDTH, screenWidth - 12); height = transmitter ? TRANSMITTER_HEIGHT : RECEIVER_HEIGHT;
        left = (screenWidth - width) / 2; top = (screenHeight - height) / 2;
    }
    public String title() { return transmitter ? "Redstone Transmitter" : "Redstone Receiver"; }
    public ProgrammableDialogLayout.Button channels() { return button(0, 48, 18, ""); }
    private ProgrammableDialogLayout.Button button(int id, int y, int height, String label) {
        return new ProgrammableDialogLayout.Button(id, left + 12, top + y, width - 24, height, label);
    }
    public static final class Control {
        public final ProgrammableDialogLayout.Button bounds;
        public final boolean slider, enabled;
        private Control(ProgrammableDialogLayout.Button bounds, boolean slider, boolean enabled) { this.bounds = bounds; this.slider = slider; this.enabled = enabled; }
    }
    public List<Control> controls(boolean analog, int on, int off, int face) {
        List<Control> result = new ArrayList<>();
        result.add(new Control(button(SIGNAL, 76, 20, signalLabel(analog)), false, true));
        result.add(new Control(button(PORT, 102, 20, portLabel(transmitter, face)), false, true));
        if (!transmitter) {
            result.add(new Control(button(ON, 128, 20, levelLabel(true, on)), true, !analog));
            result.add(new Control(button(OFF, 154, 20, levelLabel(false, off)), true, true));
        }
        result.add(new Control(button(DONE, height - 30, 20, "Done"), false, true));
        return Collections.unmodifiableList(result);
    }
    public static String signalLabel(boolean analog) { return "Signal: " + (analog ? "Analog" : "Digital"); }
    public static String portLabel(boolean transmitter, int face) { return (transmitter ? "Input" : "Output") + " port: " + FACES[Math.floorMod(face, FACES.length)]; }
    public static String levelPrefix(boolean on) { return on ? "On level: " : "Off level: "; }
    public static String levelLabel(boolean on, int value) { return levelPrefix(on) + value; }
    public static int nextFace(int face, int direction) { return DialogInput.cycle(face, FACES.length, direction); }
    public static boolean validChannelCharacters(String text) {
        for (int i = 0; i < text.length(); i++) { char value = text.charAt(i); if (value != ',' && value != ' ' && (value < '0' || value > '9')) return false; }
        return true;
    }
}
