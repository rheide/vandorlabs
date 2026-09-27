package com.vandorlabs.tiles;

import java.util.Arrays;

/** Immutable local-face overrides, safe to pass to chunk compilation workers. */
public final class FaceTextures {
    public static final FaceTextures DEFAULT = new FaceTextures(false, new int[]{-1,-1,-1,-1,-1,-1});
    public final boolean enabled;
    private final int[] choices;

    public FaceTextures(boolean enabled, int[] values) {
        this.enabled = enabled;
        choices = new int[6];
        Arrays.fill(choices, -1);
        if (values != null && values.length == 6)
            for (int i = 0; i < 6; i++)
                choices[i] = values[i] >= 0 && values[i] < ScreenHousingTextures.IDS.length
                        ? values[i] : -1;
    }
    public int choice(int face) { return choices[face]; }
    public int texture(int face, int main) {
        return enabled && choices[face] >= 0 ? choices[face] : main;
    }
    public int[] choices() { return choices.clone(); }
    @Override public boolean equals(Object other) {
        return other instanceof FaceTextures && enabled == ((FaceTextures) other).enabled
                && Arrays.equals(choices, ((FaceTextures) other).choices);
    }
    @Override public int hashCode() { return 31 * Arrays.hashCode(choices) + (enabled ? 1 : 0); }
}
