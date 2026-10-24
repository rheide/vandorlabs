package com.vandorlabs.neoforge;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generated canonical catalog; numeric positions preserve the legacy saved format. */
public final class ModernFinishCatalog {
    public record Entry(String id, String label, String category, boolean visible, boolean general, int canonical, String texture, float u, float v) {}
    public record Choice(int choice,Entry entry) {}
    public static final List<Entry> ENTRIES;
    private static final java.util.concurrent.ConcurrentMap<Integer,Entry> EXTERNAL=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        var stream = ModernFinishCatalog.class.getResourceAsStream("/assets/vandorlabs/data/housing_finishes.json");
        if (stream == null) throw new IllegalStateException("Missing canonical housing finishes");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            ENTRIES = List.of(new Gson().fromJson(reader, Entry[].class));
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static void registerExternal(int choice,Entry entry) {
        if(!com.vandorlabs.tiles.MaterialChoicePolicy.isExternal(choice))throw new IllegalArgumentException("External material namespace");
        EXTERNAL.put(choice,entry);
    }
    public static Entry entry(int choice) { return EXTERNAL.getOrDefault(choice,ENTRIES.get(clamp(choice))); }
    public static int renderChoice(int choice) { return EXTERNAL.containsKey(choice)?choice:clamp(choice); }
    public static List<Choice> allChoices() {
        java.util.ArrayList<Choice> result=new java.util.ArrayList<>();
        for(int i=0;i<ENTRIES.size();i++)result.add(new Choice(i,ENTRIES.get(i)));
        EXTERNAL.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(value->result.add(new Choice(value.getKey(),value.getValue())));
        return List.copyOf(result);
    }
    public static boolean valid(int choice) { return choice >= 0 && choice < ENTRIES.size(); }
    public static boolean validChoice(int choice) { return com.vandorlabs.tiles.MaterialChoicePolicy.valid(choice,ENTRIES.size()); }
    public static int storedChoice(int choice) { return com.vandorlabs.tiles.MaterialChoicePolicy.isExternal(choice) ? choice : clamp(choice); }
    public static int clamp(int choice) { return valid(choice) ? ENTRIES.get(choice).canonical() : 0; }
    private ModernFinishCatalog() {}
}
