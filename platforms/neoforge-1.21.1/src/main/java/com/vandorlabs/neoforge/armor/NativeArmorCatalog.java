package com.vandorlabs.neoforge.armor;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public final class NativeArmorCatalog {
    private static final String PATH = "/assets/vandorlabs/data/armor_textures.json";

    public record Design(int choice, String name, EquipmentSlot slot, String label,
                         ResourceLocation worn, String icon, ResourceLocation model, String role, String variant) {}

    private static final Map<Integer, Design> DESIGNS = load();

    public static Iterable<Design> designs() {
        return DESIGNS.values();
    }

    public static Design design(int choice, EquipmentSlot slot) {
        Design design = DESIGNS.get(choice);
        return design != null && design.slot() == slot ? design : null;
    }

    static Design matchingSlot(int choice, EquipmentSlot slot) {
        var source = DESIGNS.get(choice);
        if (source == null) return null;
        Design full = null;
        for (var design : DESIGNS.values()) {
            if (design.slot() != slot || !design.role().equals(source.role())) continue;
            if (design.variant().equals(source.variant())) return design;
            if (design.variant().equals("full")) full = design;
        }
        return full;
    }

    public static Design defaultDesign(EquipmentSlot slot) {
        return DESIGNS.values().stream()
            .filter(design -> design.slot() == slot && design.name().startsWith("civilian_staff_"))
            .findFirst().orElseThrow();
    }

    private static Map<Integer, Design> load() {
        Map<Integer, Design> designs = new LinkedHashMap<>();
        try (var stream = NativeArmorCatalog.class.getResourceAsStream(PATH)) {
            if (stream == null) throw new IllegalStateException("Armor texture catalog missing");
            var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var entry = value.getAsJsonObject();
                String name = entry.get("name").getAsString();
                String role = entry.has("role") ? entry.get("role").getAsString()
                    : name.replaceFirst("_(helmet|chestpiece|leggings|boots)(_(open|short))?$", "");
                String variant = entry.has("variant") ? entry.get("variant").getAsString() : "full";
                var design = new Design(entry.get("choice").getAsInt(), name,
                    EquipmentSlot.valueOf(entry.get("slot").getAsString()), entry.get("label").getAsString(),
                    ResourceLocation.parse(entry.get("worn").getAsString()), entry.get("icon").getAsString(),
                    ResourceLocation.parse(entry.get("model").getAsString()), role, variant);
                if (design.choice() < 0x10000000 || design.choice() >= 0x10000000 + 100000
                    || !design.slot().isArmor() || designs.putIfAbsent(design.choice(), design) != null) {
                    throw new IllegalStateException("Invalid armor design " + design);
                }
            }
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
        return Collections.unmodifiableMap(designs);
    }

    private NativeArmorCatalog() {}
}
