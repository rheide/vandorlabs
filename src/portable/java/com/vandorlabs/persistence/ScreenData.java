package com.vandorlabs.persistence;

import com.vandorlabs.animation.ScreenBehavior;
import java.util.function.Predicate;

/** Version-neutral programmable-screen state and its stable save contract. */
public final class ScreenData {
    public final String selectedScreen;
    public final boolean redstoneEnabled;
    public final int displayMode;
    public final boolean framed;
    public final int animationSpeedIndex;
    public final String inputPanel;
    public final String secondaryInputPanel;
    public final int wallPosition;
    public final boolean smallInput;
    public final int redstoneChannel;
    public final boolean channelSignal;
    public final int housingTexture;

    public ScreenData(String selectedScreen, boolean redstoneEnabled, int displayMode,
            boolean framed, int animationSpeedIndex, String inputPanel,
            String secondaryInputPanel, int wallPosition, boolean smallInput,
            int redstoneChannel, boolean channelSignal, int housingTexture) {
        this.selectedScreen = selectedScreen;
        this.redstoneEnabled = redstoneEnabled;
        this.displayMode = ScreenBehavior.clampMode(displayMode);
        this.framed = framed;
        this.animationSpeedIndex = ScreenBehavior.clampSpeedIndex(animationSpeedIndex);
        this.inputPanel = inputPanel;
        this.secondaryInputPanel = secondaryInputPanel;
        this.wallPosition = wallPosition < 0 ? -1 : Math.min(2, wallPosition);
        this.smallInput = smallInput;
        this.redstoneChannel = Math.max(0, redstoneChannel);
        this.channelSignal = channelSignal;
        this.housingTexture = housingTexture;
    }

    public void write(PrimitiveData data) {
        data.putInt(SaveSchema.DATA_VERSION, SaveSchema.Screen.VERSION);
        data.putString(SaveSchema.Screen.SELECTED, selectedScreen);
        data.putBoolean(SaveSchema.Screen.REDSTONE_ENABLED, redstoneEnabled);
        data.putInt(SaveSchema.Screen.DISPLAY_MODE, displayMode);
        data.putBoolean(SaveSchema.Screen.FRAMED, framed);
        data.putInt(SaveSchema.Screen.SPEED, animationSpeedIndex);
        data.putString(SaveSchema.Screen.INPUT, inputPanel);
        data.putString(SaveSchema.Screen.SECONDARY_INPUT, secondaryInputPanel);
        if (wallPosition >= 0) data.putInt(SaveSchema.Screen.WALL_POSITION, wallPosition);
        data.putBoolean(SaveSchema.Screen.SMALL_INPUT, smallInput);
        data.putInt(SaveSchema.Redstone.CHANNEL, redstoneChannel);
        data.putBoolean(SaveSchema.Redstone.SIGNAL, channelSignal);
        data.putInt(SaveSchema.Screen.HOUSING_TEXTURE, housingTexture);
    }

    public static ScreenData read(PrimitiveData data, String defaultScreen,
            String defaultInput, Predicate<String> validInput) {
        String selected = data.getString(SaveSchema.Screen.SELECTED);
        if (selected == null || selected.isEmpty()) selected = defaultScreen;
        String input = data.getString(SaveSchema.Screen.INPUT);
        if (!validInput.test(input)) input = defaultInput;
        String secondary = data.contains(SaveSchema.Screen.SECONDARY_INPUT)
                ? data.getString(SaveSchema.Screen.SECONDARY_INPUT) : input;
        if (!validInput.test(secondary)) secondary = defaultInput;
        int wall = data.contains(SaveSchema.Screen.WALL_POSITION)
                ? data.getInt(SaveSchema.Screen.WALL_POSITION) : -1;
        return new ScreenData(selected,
                data.getBoolean(SaveSchema.Screen.REDSTONE_ENABLED),
                data.getInt(SaveSchema.Screen.DISPLAY_MODE),
                !data.contains(SaveSchema.Screen.FRAMED)
                        || data.getBoolean(SaveSchema.Screen.FRAMED),
                data.getInt(SaveSchema.Screen.SPEED), input, secondary, wall,
                data.getBoolean(SaveSchema.Screen.SMALL_INPUT),
                data.getInt(SaveSchema.Redstone.CHANNEL),
                data.getBoolean(SaveSchema.Redstone.SIGNAL),
                data.getInt(SaveSchema.Screen.HOUSING_TEXTURE));
    }
}
