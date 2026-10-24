package com.vandorlabs.neoforge;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generated canonical catalog; numeric positions preserve the legacy saved format. */
public final class ModernFinishCatalog {
    public record Entry(String id, String label, String category, boolean visible, boolean general, int canonical, String texture, float u, float v) {}
    public static final List<Entry> ENTRIES;
    static {
        var stream = ModernFinishCatalog.class.getResourceAsStream("/assets/vandorlabs/data/housing_finishes.json");
        if (stream == null) throw new IllegalStateException("Missing canonical housing finishes");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            ENTRIES = List.of(new Gson().fromJson(reader, Entry[].class));
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static boolean valid(int choice) { return choice >= 0 && choice < ENTRIES.size(); }
    public static boolean validChoice(int choice) { return com.vandorlabs.tiles.MaterialChoicePolicy.valid(choice,ENTRIES.size()); }
    public static int storedChoice(int choice) { return com.vandorlabs.tiles.MaterialChoicePolicy.isExternal(choice) ? choice : clamp(choice); }
    public static int clamp(int choice) { return valid(choice) ? ENTRIES.get(choice).canonical() : 0; }
    private ModernFinishCatalog() {}
}
